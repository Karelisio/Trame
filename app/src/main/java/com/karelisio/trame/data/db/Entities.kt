package com.karelisio.trame.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SeriesSource { MANGADEX, MANGAUPDATES }

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: String,
    val title: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val source: SeriesSource = SeriesSource.MANGADEX,
    val isFavorite: Boolean = false,
    val addedAt: Long = 0,
    /** Dernier chapitre connu (sert à détecter les nouveautés). */
    val lastKnownChapter: Double? = null,
    /** Dernier chapitre connu au moment de l'ajout : rien en dessous n'est compté "non lu". */
    val baselineChapter: Double? = null,
    /** Dernier chapitre lu. */
    val lastReadChapter: Double? = null,
    val lastCheckedAt: Long? = null,
)

@Entity(
    tableName = "chapters",
    indices = [Index("seriesId"), Index(value = ["seriesId", "numberValue"])],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val number: String?,
    val numberValue: Double?,
    val volume: String? = null,
    val title: String? = null,
    val language: String,
    val publishAt: Long,
    val externalUrl: String? = null,
    val pages: Int = 0,
    val readAt: Long? = null,
)

@Entity(tableName = "history", indices = [Index("seriesId"), Index("readAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: String,
    val chapterId: String?,
    val chapterNumber: String?,
    val readAt: Long,
)

data class LibraryItem(
    @Embedded val series: SeriesEntity,
    val unread: Int,
)

data class HistoryRow(
    val id: Long,
    val seriesId: String,
    val chapterId: String?,
    val chapterNumber: String?,
    val readAt: Long,
    val seriesTitle: String,
    val coverUrl: String?,
)
