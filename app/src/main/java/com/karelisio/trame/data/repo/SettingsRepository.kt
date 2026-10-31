package com.karelisio.trame.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    suspend fun isSeeded() = context.dataStore.data.first()[Keys.seeded] == true

    suspend fun markSeeded() {
        context.dataStore.edit { it[Keys.seeded] = true }
    }
}
