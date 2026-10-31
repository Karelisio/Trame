package com.karelisio.trame.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** API publique MangaDex (https://api.mangadex.org), sans clé. */
interface MangaDexApi {
    @GET("manga")
    suspend fun search(
        @Query("title") title: String,
        @Query("limit") limit: Int = 24,
        @Query("includes[]") includes: List<String> = listOf("cover_art"),
        @Query("contentRating[]") ratings: List<String> = listOf("safe", "suggestive"),
        @Query("order[relevance]") order: String = "desc",
    ): MdListResponse<MdManga>

    @GET("manga/{id}")
    suspend fun manga(
        @Path("id") id: String,
        @Query("includes[]") includes: List<String> = listOf("cover_art"),
    ): MdEntityResponse<MdManga>

    @GET("manga/{id}/feed")
    suspend fun feed(
        @Path("id") id: String,
        @Query("translatedLanguage[]") languages: List<String>,
        @Query("order[chapter]") order: String = "desc",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("contentRating[]") ratings: List<String> = listOf("safe", "suggestive", "erotica", "pornographic"),
        @Query("includeFuturePublishAt") includeFuture: Int = 0,
        @Query("includeEmptyPages") includeEmpty: Int = 1,
        @Query("includeExternalUrl") includeExternal: Int = 1,
    ): MdListResponse<MdChapter>

    companion object {
        const val BASE_URL = "https://api.mangadex.org/"
        const val ONE_PIECE_ID = "a1c7c817-4e59-43b7-9365-09675a149a6f"
        fun coverUrl(mangaId: String, fileName: String, size: Int = 512) =
            "https://uploads.mangadex.org/covers/$mangaId/$fileName.$size.jpg"
    }
}

@Serializable
data class MdListResponse<T>(
    val result: String = "ok",
    val data: List<T> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
)

@Serializable
data class MdEntityResponse<T>(val result: String = "ok", val data: T)

@Serializable
data class MdRelationship(val id: String, val type: String, val attributes: JsonObject? = null)

@Serializable
data class MdManga(
    val id: String,
    val attributes: MdMangaAttributes,
    val relationships: List<MdRelationship> = emptyList(),
) {
    val displayTitle: String
        get() = attributes.title["en"]
            ?: attributes.altTitles.firstNotNullOfOrNull { it["en"] }
            ?: attributes.title.values.firstOrNull()
            ?: "Sans titre"

    fun description(lang: String): String? =
        attributes.description[lang]?.takeIf { it.isNotBlank() }
            ?: attributes.description["en"]?.takeIf { it.isNotBlank() }
            ?: attributes.description.values.firstOrNull { it.isNotBlank() }

    val coverUrl: String?
        get() = relationships.firstOrNull { it.type == "cover_art" }
            ?.attributes?.get("fileName")?.jsonPrimitive?.contentOrNull
            ?.let { MangaDexApi.coverUrl(id, it) }
}

@Serializable
data class MdMangaAttributes(
    val title: Map<String, String> = emptyMap(),
    val altTitles: List<Map<String, String>> = emptyList(),
    val description: Map<String, String> = emptyMap(),
    val status: String? = null,
    val year: Int? = null,
    val lastChapter: String? = null,
)

@Serializable
data class MdChapter(val id: String, val attributes: MdChapterAttributes)

@Serializable
data class MdChapterAttributes(
    val volume: String? = null,
    val chapter: String? = null,
    val title: String? = null,
    val translatedLanguage: String = "en",
    val externalUrl: String? = null,
    val publishAt: String? = null,
    val readableAt: String? = null,
    val pages: Int = 0,
)
