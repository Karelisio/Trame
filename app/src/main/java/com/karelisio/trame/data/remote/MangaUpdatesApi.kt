package com.karelisio.trame.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/** API publique MangaUpdates, utilisée en secours pour la recherche. */
interface MangaUpdatesApi {
    @POST("v1/series/search")
    suspend fun search(@Body body: MuSearchRequest): MuSearchResponse

    companion object {
        const val BASE_URL = "https://api.mangaupdates.com/"
    }
}

@Serializable
data class MuSearchRequest(val search: String, val perpage: Int = 20)

@Serializable
data class MuSearchResponse(
    @SerialName("total_hits") val totalHits: Int = 0,
    val results: List<MuResult> = emptyList(),
)

@Serializable
data class MuResult(val record: MuRecord)

@Serializable
data class MuRecord(
    @SerialName("series_id") val seriesId: Long,
    val title: String,
    val url: String? = null,
    val description: String? = null,
    val image: MuImage? = null,
    val type: String? = null,
    val year: String? = null,
)

@Serializable
data class MuImage(val url: MuImageUrl? = null)

@Serializable
data class MuImageUrl(val original: String? = null, val thumb: String? = null)
