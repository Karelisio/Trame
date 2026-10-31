package com.karelisio.trame.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.animation.AnimatedContentScope
import com.karelisio.trame.TrameApp
import com.karelisio.trame.ui.components.LocalNavAnimatedScope
import com.karelisio.trame.ui.components.LocalSharedTransitionScope
import com.karelisio.trame.ui.detail.DetailScreen
import com.karelisio.trame.ui.detail.DetailViewModel
import com.karelisio.trame.ui.library.LibraryScreen
import com.karelisio.trame.ui.library.LibraryViewModel
import com.karelisio.trame.ui.search.SearchScreen
import com.karelisio.trame.ui.search.SearchViewModel
import com.karelisio.trame.ui.settings.SettingsScreen
import com.karelisio.trame.ui.reader.WebReaderScreen
import com.karelisio.trame.ui.reader.WebReaderViewModel
import com.karelisio.trame.ui.history.HistoryScreen
import com.karelisio.trame.ui.calendar.CalendarScreen
import com.karelisio.trame.ui.local.LocalLibraryScreen
import com.karelisio.trame.ui.local.LocalReaderScreen
import com.karelisio.trame.ui.local.LocalReaderViewModel
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.karelisio.trame.ReadRequest

@Serializable object LibraryRoute
@Serializable object SearchRoute
@Serializable object SettingsRoute
@Serializable data class DetailRoute(val id: String)
@Serializable data class ReaderRoute(val seriesId: String, val chapterId: String? = null)
@Serializable object HistoryRoute
@Serializable object LocalRoute
@Serializable object CalendarRoute
@Serializable data class LocalReaderRoute(val uri: String)

@Composable
fun TrameNavHost(readRequests: MutableStateFlow<ReadRequest?>) {
    val nav = rememberNavController()
    val request by readRequests.collectAsState()
    LaunchedEffect(request) {
        request?.let {
            nav.navigate(ReaderRoute(it.seriesId, it.chapterId)) { launchSingleTop = true }
            readRequests.value = null
        }
    }
    val app = LocalContext.current.applicationContext as TrameApp
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(nav, startDestination = LibraryRoute) {
                screen<LibraryRoute> {
                    LibraryScreen(
                        vm = viewModel { LibraryViewModel(app.repository) },
                        onOpenSeries = { nav.navigate(DetailRoute(it)) },
                        onSearch = { nav.navigate(SearchRoute) },
                        onSettings = { nav.navigate(SettingsRoute) },
                        onHistory = { nav.navigate(HistoryRoute) },
                        onLocal = { nav.navigate(LocalRoute) },
                        onCalendar = { nav.navigate(CalendarRoute) },
                    )
                }
                screen<SearchRoute> {
                    SearchScreen(
                        vm = viewModel { SearchViewModel(app.repository) },
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate(DetailRoute(it)) },
                    )
                }
                screen<DetailRoute> { entry ->
                    val id = entry.toRoute<DetailRoute>().id
                    DetailScreen(
                        vm = viewModel(key = id) { DetailViewModel(app.repository, id) },
                        onBack = { nav.popBackStack() },
                        onRead = { nav.navigate(ReaderRoute(id, it)) },
                    )
                }
                screen<ReaderRoute> { entry ->
                    val r = entry.toRoute<ReaderRoute>()
                    WebReaderScreen(
                        vm = viewModel(key = "${r.seriesId}/${r.chapterId}") { WebReaderViewModel(app.repository, r.seriesId, r.chapterId) },
                        onBack = { nav.popBackStack() },
                    )
                }
                screen<HistoryRoute> {
                    HistoryScreen(
                        app.repository,
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate(DetailRoute(it.seriesId)) },
                    )
                }
                screen<LocalRoute> {
                    LocalLibraryScreen(
                        app.database.localBooks(),
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate(LocalReaderRoute(it)) },
                    )
                }
                screen<LocalReaderRoute> { entry ->
                    val uri = entry.toRoute<LocalReaderRoute>().uri
                    LocalReaderScreen(
                        vm = viewModel(key = uri) { LocalReaderViewModel(app, uri) },
                        onBack = { nav.popBackStack() },
                    )
                }
                screen<CalendarRoute> {
                    CalendarScreen(app.repository, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(DetailRoute(it)) })
                }
                screen<SettingsRoute> {
                    SettingsScreen(app.settings, app.repository, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/** Destination qui expose son AnimatedContentScope pour les éléments partagés. */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}
