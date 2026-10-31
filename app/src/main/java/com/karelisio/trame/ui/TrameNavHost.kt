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
import kotlinx.serialization.Serializable

@Serializable object LibraryRoute
@Serializable object SearchRoute
@Serializable object SettingsRoute
@Serializable data class DetailRoute(val id: String)

@Composable
fun TrameNavHost() {
    val nav = rememberNavController()
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
                    )
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
