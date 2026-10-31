package com.karelisio.trame.data.repo

import com.karelisio.trame.data.db.ChapterEntity
import com.karelisio.trame.data.db.HistoryEntity
import com.karelisio.trame.data.db.LibraryItem
import com.karelisio.trame.data.db.SeriesEntity
import com.karelisio.trame.data.db.SeriesSource
import com.karelisio.trame.data.db.TrameDatabase
import com.karelisio.trame.data.remote.MangaDexApi
import com.karelisio.trame.data.remote.MangaUpdatesApi
import com.karelisio.trame.data.remote.MdChapter
import com.karelisio.trame.data.remote.MdManga
import com.karelisio.trame.data.remote.MuSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

/** Résultat de recherche, quelle que soit la source. */
data class SeriesSummary(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val description: String?,
    val status: String?,
    val year: Int?,
    val source: SeriesSource,
)

data class SearchOutcome(val results: List<SeriesSummary>, val source: SeriesSource)

class MangaRepository(
    private val db: TrameDatabase,
    private val md: MangaDexApi,
    private val mu: MangaUpdatesApi,
    private val settings: SettingsRepository,
) {
    private val seriesDao = db.series()
    private val chapterDao = db.chapters()

    fun observeLibrary(): Flow<List<LibraryItem>> = seriesDao.observeLibrary()
    fun observeItem(id: String): Flow<LibraryItem?> = seriesDao.observeItem(id)
    fun observeChapters(id: String): Flow<List<ChapterEntity>> = chapterDao.observeForSeries(id)
    fun observeFavoriteIds(): Flow<Set<String>> = seriesDao.observeFavoriteIds().map { it.toSet() }

    /** Recherche MangaDex, MangaUpdates en secours (erreur réseau ou aucun résultat). */
    suspend fun search(query: String): SearchOutcome = withContext(Dispatchers.IO) {
        val lang = settings.currentLanguage().code
        val mdResult = runCatching { md.search(query).data.map { it.toSummary(lang) } }
        val list = mdResult.getOrNull()
        if (!list.isNullOrEmpty()) return@withContext SearchOutcome(list, SeriesSource.MANGADEX)
        val muList = runCatching {
            mu.search(MuSearchRequest(query)).results.map { r ->
                val rec = r.record
                SeriesSummary(
                    id = "mu:${rec.seriesId}",
                    title = rec.title.stripHtml(),
                    coverUrl = rec.image?.url?.original ?: rec.image?.url?.thumb,
                    description = rec.description?.stripHtml(),
                    status = null,
                    year = rec.year?.toIntOrNull(),
                    source = SeriesSource.MANGAUPDATES,
                )
            }
        }
        if (muList.isSuccess) SearchOutcome(muList.getOrThrow(), SeriesSource.MANGAUPDATES)
        else throw (mdResult.exceptionOrNull() ?: muList.exceptionOrNull()!!)
    }

    /** Enregistre (sans l'ajouter aux favoris) une série pour pouvoir afficher sa fiche. */
    suspend fun ensureSeries(summary: SeriesSummary) {
        val resolved = resolveToMangaDex(summary)
        seriesDao.insertIgnore(resolved.toEntity())
        seriesDao.updateMeta(resolved.id, resolved.title, resolved.coverUrl, resolved.description, resolved.status, resolved.year)
    }

    /** Une série MangaUpdates est rattachée à MangaDex si possible (pour le feed des chapitres). */
    suspend fun resolveToMangaDex(summary: SeriesSummary): SeriesSummary {
        if (summary.source == SeriesSource.MANGADEX) return summary
        val lang = settings.currentLanguage().code
        val match = runCatching { md.search(summary.title, limit = 5).data }.getOrNull()
            ?.firstOrNull { it.displayTitle.equals(summary.title, ignoreCase = true) }
        return match?.toSummary(lang) ?: summary
    }

    suspend fun setFavorite(summary: SeriesSummary, favorite: Boolean) {
        val resolved = resolveToMangaDex(summary)
        ensureSeries(resolved)
        setFavorite(resolved.id, favorite)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        seriesDao.setFavorite(id, favorite, System.currentTimeMillis())
        if (favorite) runCatching { refreshSeries(id) }
    }

    /**
     * Rafraîchit métadonnées + chapitres d'une série.
     * @return les chapitres nouvellement détectés (numéro > dernier connu).
     */
    suspend fun refreshSeries(id: String): List<ChapterEntity> = withContext(Dispatchers.IO) {
        val series = seriesDao.get(id) ?: return@withContext emptyList()
        if (series.source != SeriesSource.MANGADEX) return@withContext emptyList()
        val lang = settings.currentLanguage().code

        runCatching { md.manga(id).data }.getOrNull()?.let { m ->
            seriesDao.updateMeta(id, m.displayTitle, m.coverUrl, m.description(lang), m.attributes.status, m.attributes.year)
        }

        val remote = fetchFeed(id, lang)
        val existing = chapterDao.forSeries(id).associateBy { it.id }
        val merged = dedupe(remote.map { it.toEntity(id) })
            .map { c -> existing[c.id]?.let { c.copy(readAt = it.readAt) } ?: c }
        chapterDao.deleteOtherLanguages(id, lang)
        chapterDao.upsertAll(merged)

        val latest = merged.mapNotNull { it.numberValue }.maxOrNull()
        val previous = series.lastKnownChapter
        seriesDao.updateCheck(id, latest ?: previous, System.currentTimeMillis())
        if (previous == null) emptyList()
        else merged.filter { (it.numberValue ?: 0.0) > previous }.sortedBy { it.numberValue }
    }

    /** Rafraîchit toutes les séries favorites (4 en parallèle max). */
    suspend fun refreshAll(): Map<SeriesEntity, List<ChapterEntity>> = coroutineScope {
        val gate = Semaphore(4)
        seriesDao.favorites().map { s ->
            async { s to gate.withPermit { runCatching { refreshSeries(s.id) }.getOrDefault(emptyList()) } }
        }.awaitAll().toMap()
    }

    // --- Progression -------------------------------------------------------

    fun observeHistory() = db.history().observeRecent()

    suspend fun chapter(id: String) = chapterDao.get(id)

    /** Marque un chapitre lu / non lu et met à jour le dernier chapitre lu de la série. */
    suspend fun setRead(chapter: ChapterEntity, read: Boolean) {
        val now = System.currentTimeMillis()
        chapterDao.setReadAt(chapter.id, if (read) now else null)
        if (read) {
            db.history().insert(HistoryEntity(seriesId = chapter.seriesId, chapterId = chapter.id, chapterNumber = chapter.number, readAt = now))
        } else {
            db.history().deleteFor(chapter.seriesId, chapter.id)
        }
        seriesDao.setLastRead(chapter.seriesId, chapterDao.maxRead(chapter.seriesId))
    }

    suspend fun setAllRead(seriesId: String, read: Boolean) {
        if (read) chapterDao.markAllRead(seriesId, System.currentTimeMillis()) else chapterDao.markAllUnread(seriesId)
        seriesDao.setLastRead(seriesId, chapterDao.maxRead(seriesId))
    }

    /** One Piece pré-ajouté au premier lancement. */
    suspend fun seedIfNeeded() {
        if (settings.isSeeded()) return
        seriesDao.insertIgnore(
            SeriesEntity(
                id = MangaDexApi.ONE_PIECE_ID,
                title = "One Piece",
                isFavorite = true,
                addedAt = System.currentTimeMillis(),
                status = "ongoing",
                year = 1997,
            ),
        )
        settings.markSeeded()
        runCatching { refreshSeries(MangaDexApi.ONE_PIECE_ID) }
    }

    private suspend fun fetchFeed(id: String, lang: String, maxPages: Int = 3): List<MdChapter> {
        val out = mutableListOf<MdChapter>()
        var offset = 0
        repeat(maxPages) {
            val page = md.feed(id, listOf(lang), offset = offset)
            out += page.data
            offset += page.data.size
            if (page.data.isEmpty() || offset >= page.total) return out
        }
        return out
    }

    /** Un seul chapitre par numéro : on privilégie l'officiel (MANGA Plus), puis le plus récent. */
    private fun dedupe(list: List<ChapterEntity>): List<ChapterEntity> =
        list.groupBy { it.number ?: it.id }.values.map { group ->
            group.sortedWith(compareByDescending<ChapterEntity> { it.externalUrl != null }.thenByDescending { it.publishAt }).first()
        }
}

private fun MdManga.toSummary(lang: String) = SeriesSummary(
    id = id,
    title = displayTitle,
    coverUrl = coverUrl,
    description = description(lang),
    status = attributes.status,
    year = attributes.year,
    source = SeriesSource.MANGADEX,
)

private fun SeriesSummary.toEntity() = SeriesEntity(
    id = id, title = title, coverUrl = coverUrl, description = description,
    status = status, year = year, source = source,
)

private fun MdChapter.toEntity(seriesId: String) = ChapterEntity(
    id = id,
    seriesId = seriesId,
    number = attributes.chapter,
    numberValue = attributes.chapter?.toDoubleOrNull(),
    volume = attributes.volume,
    title = attributes.title?.takeIf { it.isNotBlank() },
    language = attributes.translatedLanguage,
    publishAt = parseInstant(attributes.readableAt ?: attributes.publishAt),
    externalUrl = attributes.externalUrl,
    pages = attributes.pages,
)

internal fun parseInstant(iso: String?): Long =
    iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L

private fun String.stripHtml(): String =
    replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").trim()
