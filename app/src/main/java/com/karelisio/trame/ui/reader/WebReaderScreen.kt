package com.karelisio.trame.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.trame.ui.components.Cover
import com.karelisio.trame.ui.fx.HalftoneLoader
import com.karelisio.trame.ui.components.sharedElement
import kotlinx.coroutines.delay

/** Domaines officiels autorisés dans la WebView ; le reste part dans le navigateur. */
private val OFFICIAL_HOSTS = listOf("mangaplus.shueisha.co.jp", "shueisha.co.jp", "mangaplus-creators.jp")

/**
 * Lecture officielle MANGA Plus dans une WebView plein écran (mode immersif).
 * Aucune extraction de contenu : on affiche simplement le site officiel.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebReaderScreen(vm: WebReaderViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var barVisible by remember { mutableStateOf(true) }
    var barPing by remember { mutableIntStateOf(0) }

    ImmersiveMode()

    // La barre se masque d'elle-même ; un tap sur la poignée la fait revenir.
    LaunchedEffect(barVisible, barPing) {
        if (barVisible) { delay(3500); barVisible = false }
    }

    val current = state.current
    var loadedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current?.id, webView) {
        val wv = webView ?: return@LaunchedEffect
        val url = current?.externalUrl ?: return@LaunchedEffect
        if (loadedId == current.id) return@LaunchedEffect
        loadedId = current.id
        wv.loadUrl(url)
        vm.onOpened(current)
    }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack() && wv.url != current?.externalUrl) wv.goBack() else onBack()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val host = request.url.host.orEmpty()
                            if (OFFICIAL_HOSTS.any { host == it || host.endsWith(".$it") }) return false
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                            return true
                        }
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 10 }
                        override fun onPageFinished(view: WebView, url: String?) { progress = 100 }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                    }
                    webView = this
                }
            },
            onRelease = { it.stopLoading(); it.destroy() },
        )

        // Pendant le chargement : la couverture (élément partagé) + indicateur expressif.
        AnimatedVisibility(visible = progress < 80, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(
                        state.coverUrl, state.seriesTitle,
                        Modifier.sharedElement("cover-${vm.seriesId}").width(160.dp).height(228.dp).clip(MaterialTheme.shapes.extraLarge),
                    )
                    HalftoneLoader(
                        Modifier.padding(top = 28.dp),
                        size = 64.dp,
                        color = Color.White,
                        container = Color.White.copy(alpha = 0.12f),
                    )
                }
            }
        }

        // Poignée invisible en bas pour rappeler la barre.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    barVisible = true; barPing++
                },
        )

        AnimatedVisibility(
            visible = barVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                    IconButton(
                        enabled = state.previous != null,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.goTo(state.previous); barPing++ },
                    ) { Icon(Icons.Rounded.SkipPrevious, "Chapitre précédent") }
                    Column(Modifier.padding(horizontal = 8.dp).width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Chapitre ${current?.number ?: "?"}", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            "MANGA Plus · ${state.seriesTitle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        enabled = state.next != null,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.goTo(state.next); barPing++ },
                    ) { Icon(Icons.Rounded.SkipNext, "Chapitre suivant") }
                }
            }
        }

        if (current != null && current.externalUrl == null) {
            Text(
                "Ce chapitre n'est pas disponible en lecture officielle.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
    }
}

/** Masque barres d'état et de navigation (réapparition par balayage). */
@Composable
fun ImmersiveMode() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

