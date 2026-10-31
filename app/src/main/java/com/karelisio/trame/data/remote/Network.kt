package com.karelisio.trame.data.remote

import com.karelisio.trame.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object Network {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    val userAgent = "Trame/${BuildConfig.VERSION_NAME} (Android; +https://github.com/karelisio/trame)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
            .build()
    }

    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val mangaDex: MangaDexApi by lazy { retrofit(MangaDexApi.BASE_URL).create(MangaDexApi::class.java) }
    val mangaUpdates: MangaUpdatesApi by lazy { retrofit(MangaUpdatesApi.BASE_URL).create(MangaUpdatesApi::class.java) }
}
