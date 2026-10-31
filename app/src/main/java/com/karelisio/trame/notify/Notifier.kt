package com.karelisio.trame.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.karelisio.trame.MainActivity
import com.karelisio.trame.R
import com.karelisio.trame.data.db.ChapterEntity
import com.karelisio.trame.data.db.SeriesEntity
import com.karelisio.trame.ui.components.formatChapter

/** Notifications de nouveaux chapitres : un canal dédié par série. */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    companion object {
        const val GROUP_SERIES = "series"
        const val ACTION_READ = "com.karelisio.trame.READ"
        const val EXTRA_SERIES = "series_id"
        const val EXTRA_CHAPTER = "chapter_id"
        fun channelId(seriesId: String) = "series_$seriesId"
        fun notificationId(seriesId: String) = seriesId.hashCode()
    }

    fun canNotify(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun ensureChannel(series: SeriesEntity) {
        manager.createNotificationChannelGroup(NotificationChannelGroup(GROUP_SERIES, "Séries suivies"))
        val channel = NotificationChannel(channelId(series.id), series.title, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Nouveaux chapitres de ${series.title}"
            group = GROUP_SERIES
            enableLights(true)
            lightColor = 0xFFB3202A.toInt()
        }
        manager.createNotificationChannel(channel)
    }

    fun deleteChannel(seriesId: String) {
        manager.deleteNotificationChannel(channelId(seriesId))
    }

    /** Synchronise les canaux avec les favoris (création / suppression). */
    fun syncChannels(favorites: List<SeriesEntity>) {
        favorites.forEach(::ensureChannel)
        val ids = favorites.map { channelId(it.id) }.toSet()
        manager.notificationChannels
            .filter { it.group == GROUP_SERIES && it.id !in ids }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }

    suspend fun notifyNewChapters(series: SeriesEntity, chapters: List<ChapterEntity>) {
        if (chapters.isEmpty() || !canNotify()) return
        ensureChannel(series)
        val latest = chapters.maxBy { it.numberValue ?: 0.0 }
        val num = latest.number ?: formatChapter(latest.numberValue) ?: "?"
        val title = "${series.title} – Chapitre $num"
        val text = when {
            chapters.size > 1 -> "${chapters.size} nouveaux chapitres disponibles"
            latest.title != null -> latest.title
            else -> "Nouveau chapitre disponible"
        }
        val cover = loadCover(series.coverUrl)
        val nid = notificationId(series.id)

        val builder = NotificationCompat.Builder(context, channelId(series.id))
            .setSmallIcon(R.drawable.ic_stat_trame)
            .setColor(0xFFB3202A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setWhen(latest.publishAt.takeIf { it > 0 } ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(readIntent(series.id, latest.id, nid))
            .addAction(0, "Lire", readIntent(series.id, latest.id, nid))
            .addAction(0, "Plus tard", SnoozeReceiver.pendingIntent(context, series.id, nid))
        if (cover != null) {
            builder.setLargeIcon(cover)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(cover)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(text),
                )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        @Suppress("MissingPermission")
        manager.notify(nid, builder.build())
    }

    fun cancel(id: Int) = manager.cancel(id)

    private fun readIntent(seriesId: String, chapterId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_READ
            putExtra(EXTRA_SERIES, seriesId)
            putExtra(EXTRA_CHAPTER, chapterId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun loadCover(url: String?): Bitmap? {
        url ?: return null
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(512, 732).build()
        val result = SingletonImageLoader.get(context).execute(request)
        return (result as? SuccessResult)?.image?.toBitmap()
    }
}
