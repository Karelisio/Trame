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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.karelisio.trame.notify.Notifier
import com.karelisio.trame.work.CheckWorker

class TrameApp : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { TrameDatabase.build(this) }
    val settings by lazy { SettingsRepository(this) }
    val notifier by lazy { Notifier(this) }
    val repository by lazy { MangaRepository(database, Network.mangaDex, Network.mangaUpdates, settings) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.seedIfNeeded() }
        // Worker reprogrammé à chaque changement d'intervalle.
        appScope.launch {
            settings.checkIntervalHours.distinctUntilChanged().collect { CheckWorker.schedule(this@TrameApp, it) }
        }
        // Un canal de notification par série suivie.
        appScope.launch {
            repository.observeLibrary().map { list -> list.map { it.series } }
                .distinctUntilChanged { a, b -> a.map { it.id to it.title } == b.map { it.id to it.title } }
                .collect { notifier.syncChannels(it) }
        }
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
