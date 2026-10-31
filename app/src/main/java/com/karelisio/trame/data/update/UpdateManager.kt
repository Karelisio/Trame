package com.karelisio.trame.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karelisio.trame.BuildConfig
import com.karelisio.trame.data.remote.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.io.File

private val Context.updateStore by preferencesDataStore("update")
private val LAST_CHECK = longPreferencesKey("last_check")

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

data class AvailableUpdate(val version: String, val changelog: String, val apkUrl: String, val size: Long)

sealed interface DownloadState {
    data class Progress(val fraction: Float) : DownloadState
    data class Done(val file: File) : DownloadState
}

/**
 * Mise à jour in-app via GitHub Releases (releases/latest), au plus toutes les 6 h.
 * Installation par PackageInstaller (REQUEST_INSTALL_PACKAGES).
 */
class UpdateManager(private val context: Context) {
    companion object {
        const val REPO = "Karelisio/Trame"
        private const val MIN_INTERVAL = 6 * 3_600_000L

        /** "1.10.0" > "1.9.3" ; ignore le préfixe v et les suffixes. */
        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val r = parts(remote); val l = parts(local)
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }

    suspend fun check(force: Boolean = false): AvailableUpdate? {
        val now = System.currentTimeMillis()
        val last = context.updateStore.data.first()[LAST_CHECK] ?: 0L
        if (!force && now - last < MIN_INTERVAL) return null
        val release = kotlinx.coroutines.withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            Network.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                Network.json.decodeFromString(GhRelease.serializer(), r.body.string())
            }
        }
        context.updateStore.edit { it[LAST_CHECK] = now }
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        if (!isNewer(release.tagName, BuildConfig.VERSION_NAME)) return null
        return AvailableUpdate(release.tagName.removePrefix("v"), release.body.orEmpty().trim(), apk.url, apk.size)
    }

    fun download(update: AvailableUpdate): Flow<DownloadState> = flow {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Trame-${update.version}.apk")
        Network.client.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val body = r.body
            val total = body.contentLength().takeIf { it > 0 } ?: update.size
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastEmit = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        val pct = if (total > 0) (read * 100 / total).toInt() else 0
                        if (pct != lastEmit) { lastEmit = pct; emit(DownloadState.Progress(pct / 100f)) }
                    }
                }
            }
        }
        emit(DownloadState.Done(out))
    }.flowOn(Dispatchers.IO)

    /** Installe l'APK via une session PackageInstaller ; la confirmation système est gérée par [InstallReceiver]. */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (android.os.Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("trame.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pi.intentSender)
        }
    }
}
