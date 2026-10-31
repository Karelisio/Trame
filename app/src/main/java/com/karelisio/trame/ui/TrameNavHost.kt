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
import com.karelisio.trame.ui.settings.PermissionsScreen
import com.karelisio.trame.ui.update.UpdateSheet
import com.karelisio.trame.ui.update.UpdateViewModel
import com.karelisio.trame.ui.onboarding.OnboardingScreen
import androidx.navigation.NavDestination.Companion.hasRoute
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.karelisio.trame.ui.components.formatChapter
import com.karelisio.trame.ui.fx.BubbleController
import com.karelisio.trame.ui.fx.BubbleHost
import com.karelisio.trame.ui.fx.HALFTONE_TRANSITION_MS
import com.karelisio.trame.ui.fx.HalftoneEasing
import com.karelisio.trame.ui.fx.ImpactController
import com.karelisio.trame.ui.fx.ImpactEvent
import com.karelisio.trame.ui.fx.ImpactOverlay
import com.karelisio.trame.ui.fx.LocalBubbles
import com.karelisio.trame.ui.fx.LocalTransitionState
import com.karelisio.trame.ui.fx.TransitionState
import com.karelisio.trame.ui.fx.halftoneScreen
import com.karelisio.trame.ui.fx.trackTaps
import com.karelisio.trame.ui.theme.LocalReduceMotion

@Serializable object LibraryRoute
@Serializable object OnboardingRoute
@Serializable object PermissionsRoute
@Serializable object SearchRoute
@Serializable object SettingsRoute
@Serializable data class DetailRoute(val id: String)
@Serializable data class ReaderRoute(val seriesId: String, val chapterId: String? = null)
@Serializable object HistoryRoute
@Serializable object LocalRoute
@Serializable object CalendarRoute
@Serializable data class LocalReaderRoute(val uri: String)

@Composable
fun TrameNavHost(readRequests: MutableStateFlow<ReadRequest?>, startOnboarding: Boolean, onOnboarded: () -> Unit) {
    val nav = rememberNavController()
    val request by readRequests.collectAsState()
    LaunchedEffect(request) {
        if (startOnboarding && nav.currentDestination?.hasRoute(OnboardingRoute::class) == true) return@LaunchedEffect
        request?.let {
            nav.navigate(ReaderRoute(it.seriesId, it.chapterId)) { launchSingleTop = true }
            readRequests.value = null
        }
    }
    val app = LocalContext.current.applicationContext as TrameApp
    val updates: UpdateViewModel = viewModel()
    val transitions = remember { TransitionState() }
    val bubbles = remember { BubbleController() }
    val impact = remember { ImpactController() }
    val reduce = LocalReduceMotion.current

    // Détection en direct : si le dernier chapitre connu d'une série augmente pendant que l'appli
    // est ouverte (actualisation manuelle, worker…), case « impact » avec lignes de vitesse.
    LaunchedEffect(Unit) {
        var known: Map<String, Double?>? = null
        app.repository.observeLibrary().collect { list ->
            val prev = known
            if (prev != null) {
                list.firstOrNull { item ->
                    val before = prev[item.series.id]
                    before != null && (item.series.lastKnownChapter ?: 0.0) > before
                }?.let { item ->
                    val before = prev[item.series.id] ?: 0.0
                    val latest = item.series.lastKnownChapter ?: before
                    impact.trigger(
                        ImpactEvent(
                            seriesId = item.series.id,
                            title = item.series.title,
                            chapter = formatChapter(latest),
                            count = (latest - before).toInt().coerceAtLeast(1),
                        ),
                    )
                }
            }
            known = list.associate { it.series.id to it.series.lastKnownChapter }
        }
    }

    CompositionLocalProvider(LocalTransitionState provides transitions, LocalBubbles provides bubbles) {
    Box(Modifier.fillMaxSize().trackTaps(transitions)) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                nav,
                startDestination = if (startOnboarding) OnboardingRoute else LibraryRoute,
                // En avant : le nouvel écran se révèle en trame (cf. halftoneScreen), l'ancien recule et s'estompe.
                enterTransition = {
                    transitions.popping = false
                    if (reduce) fadeIn(tween(150)) else EnterTransition.None
                },
                exitTransition = {
                    transitions.popping = false
                    if (reduce) fadeOut(tween(150))
                    else fadeOut(tween(380, delayMillis = 180)) +
                        scaleOut(tween(HALFTONE_TRANSITION_MS, easing = HalftoneEasing), targetScale = 0.94f)
                },
                // Retour (bouton ou geste prédictif) : l'écran courant se dissout en points, le précédent revient.
                popEnterTransition = {
                    transitions.popping = true
                    if (reduce) fadeIn(tween(150))
                    else fadeIn(tween(260)) + scaleIn(tween(HALFTONE_TRANSITION_MS, easing = HalftoneEasing), initialScale = 0.94f)
                },
                popExitTransition = {
                    transitions.popping = true
                    if (reduce) fadeOut(tween(150))
                    else scaleOut(tween(HALFTONE_TRANSITION_MS, easing = HalftoneEasing), targetScale = 0.9f)
                },
            ) {
                screen<OnboardingRoute> {
                    OnboardingScreen(onFinish = {
                        onOnboarded()
                        nav.navigate(LibraryRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                    })
                }
                screen<LibraryRoute> {
                    LibraryScreen(
                        vm = viewModel { LibraryViewModel(app.repository) },
                        onOpenSeries = { nav.navigate(DetailRoute(it)) },
                        onSearch = { nav.navigate(SearchRoute) },
                        onSettings = { nav.navigate(SettingsRoute) },
                        onHistory = { nav.navigate(HistoryRoute) },
                        onLocal = { nav.navigate(LocalRoute) },
                        onCalendar = { nav.navigate(CalendarRoute) },
                        onPermissions = { nav.navigate(PermissionsRoute) },
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
                // Lecteur MANGA Plus : WebView (rendu GPU propre) → fondu plutôt que masque de trame.
                screen<ReaderRoute>(halftone = false, transitions = transitions) { entry ->
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
                    SettingsScreen(app.settings, app.repository, onBack = { nav.popBackStack() }, onPermissions = { nav.navigate(PermissionsRoute) }, onCheckUpdate = { updates.check(force = true) })
                }
                screen<PermissionsRoute> {
                    PermissionsScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
    ImpactOverlay(impact, onOpen = { e -> nav.navigate(ReaderRoute(e.seriesId, null)) { launchSingleTop = true } })
    BubbleHost(bubbles)
    UpdateSheet(updates)
    }
    }
}

/** Destination : expose son AnimatedContentScope (éléments partagés) et applique la transition « trame ». */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        val scope = this
        CompositionLocalProvider(LocalNavAnimatedScope provides scope) {
            Box(Modifier.fillMaxSize().halftoneScreen(scope)) { scope.content(entry) }
        }
    }
}

/** Variante sans trame : fondu + léger zoom (écrans à base de WebView). */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    @Suppress("UNUSED_PARAMETER") halftone: Boolean,
    transitions: TransitionState,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T>(
        enterTransition = {
            transitions.popping = false
            fadeIn(tween(320, delayMillis = 60)) + scaleIn(tween(HALFTONE_TRANSITION_MS, easing = HalftoneEasing), initialScale = 1.04f)
        },
        popExitTransition = {
            transitions.popping = true
            fadeOut(tween(260)) + scaleOut(tween(HALFTONE_TRANSITION_MS, easing = HalftoneEasing), targetScale = 0.92f)
        },
    ) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}
