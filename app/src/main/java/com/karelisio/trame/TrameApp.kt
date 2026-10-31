package com.karelisio.trame

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.karelisio.trame.data.db.TrameDatabase
import com.karelisio.trame.data.remote.Network
import com.karelisio.trame.data.repo.MangaRepository
import com.karelisio.trame.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrameApp : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { TrameDatabase.build(this) }
    val settings by lazy { SettingsRepository(this) }
    val repository by lazy { MangaRepository(database, Network.mangaDex, Network.mangaUpdates, settings) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.seedIfNeeded() }
    }

    /** Cache disque des couvertures : la bibliothèque reste belle hors ligne. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Network.client })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("covers"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
