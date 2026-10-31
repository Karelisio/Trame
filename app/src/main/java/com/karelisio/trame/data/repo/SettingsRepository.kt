package com.karelisio.trame.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ChapterLanguage(val code: String, val label: String) {
    FR("fr", "Français"), EN("en", "English");

    companion object {
        fun from(code: String?) = entries.firstOrNull { it.code == code } ?: EN
    }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val language = stringPreferencesKey("chapter_language")
        val interval = intPreferencesKey("check_interval_hours")
        val seeded = booleanPreferencesKey("seeded")
        val onboarded = booleanPreferencesKey("onboarded")
        val readerMode = stringPreferencesKey("reader_mode")
        val readerRtl = booleanPreferencesKey("reader_rtl")
        val readerFilter = stringPreferencesKey("reader_filter")
        val readerBrightness = floatPreferencesKey("reader_brightness")
    }

    val language: Flow<ChapterLanguage> =
        context.dataStore.data.map { ChapterLanguage.from(it[Keys.language]) }

    /** Intervalle de vérification des nouveautés, en heures (1 à 3). */
    val checkIntervalHours: Flow<Int> =
        context.dataStore.data.map { (it[Keys.interval] ?: 2).coerceIn(1, 3) }

    suspend fun currentLanguage() = language.first()

    suspend fun setLanguage(lang: ChapterLanguage) {
        context.dataStore.edit { it[Keys.language] = lang.code }
    }

    suspend fun setCheckInterval(hours: Int) {
        context.dataStore.edit { it[Keys.interval] = hours.coerceIn(1, 3) }
    }

    val readerPrefs: Flow<ReaderPrefs> = context.dataStore.data.map {
        ReaderPrefs(
            mode = runCatching { ReaderMode.valueOf(it[Keys.readerMode] ?: "") }.getOrDefault(ReaderMode.PAGED),
            rightToLeft = it[Keys.readerRtl] ?: true,
            filter = runCatching { ReaderFilter.valueOf(it[Keys.readerFilter] ?: "") }.getOrDefault(ReaderFilter.NONE),
            brightness = it[Keys.readerBrightness] ?: -1f,
        )
    }

    suspend fun updateReaderPrefs(transform: (ReaderPrefs) -> ReaderPrefs) {
        context.dataStore.edit { p ->
            val cur = ReaderPrefs(
                mode = runCatching { ReaderMode.valueOf(p[Keys.readerMode] ?: "") }.getOrDefault(ReaderMode.PAGED),
                rightToLeft = p[Keys.readerRtl] ?: true,
                filter = runCatching { ReaderFilter.valueOf(p[Keys.readerFilter] ?: "") }.getOrDefault(ReaderFilter.NONE),
                brightness = p[Keys.readerBrightness] ?: -1f,
            )
            val n = transform(cur)
            p[Keys.readerMode] = n.mode.name
            p[Keys.readerRtl] = n.rightToLeft
            p[Keys.readerFilter] = n.filter.name
            p[Keys.readerBrightness] = n.brightness
        }
    }

    suspend fun isOnboarded() = context.dataStore.data.first()[Keys.onboarded] == true

    suspend fun setOnboarded() {
        context.dataStore.edit { it[Keys.onboarded] = true }
    }

    suspend fun isSeeded() = context.dataStore.data.first()[Keys.seeded] == true

    suspend fun markSeeded() {
        context.dataStore.edit { it[Keys.seeded] = true }
    }
}

enum class ReaderMode { PAGED, VERTICAL }
enum class ReaderFilter(val label: String) { NONE("Normal"), SEPIA("Sépia"), NIGHT("Nuit") }

/** Réglages de la liseuse locale. brightness < 0 = luminosité système. */
data class ReaderPrefs(
    val mode: ReaderMode = ReaderMode.PAGED,
    val rightToLeft: Boolean = true,
    val filter: ReaderFilter = ReaderFilter.NONE,
    val brightness: Float = -1f,
)
