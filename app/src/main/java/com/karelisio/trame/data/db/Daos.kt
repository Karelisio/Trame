package com.karelisio.trame.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val UNREAD_SUBQUERY = """
    (SELECT COUNT(DISTINCT c.numberValue) FROM chapters c
     WHERE c.seriesId = s.id AND c.readAt IS NULL AND c.numberValue IS NOT NULL
     AND c.numberValue > MAX(COALESCE(s.baselineChapter, -1), COALESCE(s.lastReadChapter, -1)))
"""

@Dao
interface SeriesDao {
    @Query("SELECT s.*, $UNREAD_SUBQUERY AS unread FROM series s WHERE s.isFavorite = 1 ORDER BY s.addedAt ASC")
    fun observeLibrary(): Flow<List<LibraryItem>>

    @Query("SELECT s.*, $UNREAD_SUBQUERY AS unread FROM series s WHERE s.id = :id")
    fun observeItem(id: String): Flow<LibraryItem?>

    @Query("SELECT * FROM series WHERE isFavorite = 1 ORDER BY addedAt ASC")
    suspend fun favorites(): List<SeriesEntity>

    @Query("SELECT id FROM series WHERE isFavorite = 1")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun get(id: String): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(series: SeriesEntity): Long

    @Upsert
    suspend fun upsert(series: SeriesEntity)

    @Query("UPDATE series SET title = :title, coverUrl = COALESCE(:coverUrl, coverUrl), description = COALESCE(:description, description), status = COALESCE(:status, status), year = COALESCE(:year, year) WHERE id = :id")
    suspend fun updateMeta(id: String, title: String, coverUrl: String?, description: String?, status: String?, year: Int?)

    @Query("UPDATE series SET isFavorite = :favorite, addedAt = :addedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, addedAt: Long)

    @Query("UPDATE series SET lastKnownChapter = :latest, baselineChapter = COALESCE(baselineChapter, :latest), lastCheckedAt = :checkedAt WHERE id = :id")
    suspend fun updateCheck(id: String, latest: Double?, checkedAt: Long)

    @Query("UPDATE series SET lastReadChapter = :chapter WHERE id = :id")
    suspend fun setLastRead(id: String, chapter: Double?)

    @Query("SELECT * FROM series")
    suspend fun all(): List<SeriesEntity>
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE seriesId = :seriesId ORDER BY numberValue DESC, publishAt DESC")
    fun observeForSeries(seriesId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE seriesId = :seriesId")
    suspend fun forSeries(seriesId: String): List<ChapterEntity>

    @Upsert
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE seriesId = :seriesId AND language != :language")
    suspend fun deleteOtherLanguages(seriesId: String, language: String)

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun get(id: String): ChapterEntity?

    @Query("UPDATE chapters SET readAt = :readAt WHERE id = :id")
    suspend fun setReadAt(id: String, readAt: Long?)

    @Query("UPDATE chapters SET readAt = :now WHERE seriesId = :seriesId AND readAt IS NULL")
    suspend fun markAllRead(seriesId: String, now: Long)

    @Query("UPDATE chapters SET readAt = NULL WHERE seriesId = :seriesId")
    suspend fun markAllUnread(seriesId: String)

    @Query("SELECT MAX(numberValue) FROM chapters WHERE seriesId = :seriesId AND readAt IS NOT NULL")
    suspend fun maxRead(seriesId: String): Double?

    @Query("SELECT * FROM chapters WHERE readAt IS NOT NULL")
    suspend fun allRead(): List<ChapterEntity>
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query("""SELECT h.id, h.seriesId, h.chapterId, h.chapterNumber, h.readAt, s.title AS seriesTitle, s.coverUrl AS coverUrl
        FROM history h JOIN series s ON s.id = h.seriesId ORDER BY h.readAt DESC LIMIT :limit""")
    fun observeRecent(limit: Int = 300): Flow<List<HistoryRow>>

    @Query("DELETE FROM history WHERE seriesId = :seriesId AND chapterId = :chapterId")
    suspend fun deleteFor(seriesId: String, chapterId: String)

    @Query("SELECT * FROM history ORDER BY readAt DESC")
    suspend fun all(): List<HistoryEntity>

    @Transaction
    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface LocalBookDao {
    @Query("SELECT * FROM local_books ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<LocalBookEntity>>

    @Query("SELECT * FROM local_books WHERE uri = :uri")
    suspend fun get(uri: String): LocalBookEntity?

    @Upsert
    suspend fun upsert(book: LocalBookEntity)

    @Query("UPDATE local_books SET lastPage = :page, lastOpenedAt = :now WHERE uri = :uri")
    suspend fun setProgress(uri: String, page: Int, now: Long)

    @Query("UPDATE local_books SET pageCount = :count, coverUri = :cover WHERE uri = :uri")
    suspend fun setInfo(uri: String, count: Int, cover: String?)

    @Query("DELETE FROM local_books WHERE uri = :uri")
    suspend fun delete(uri: String)
}
