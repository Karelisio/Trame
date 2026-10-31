package com.karelisio.trame.ui.local

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.karelisio.trame.data.local.PageSource
import com.karelisio.trame.data.repo.ReaderFilter
import com.karelisio.trame.data.repo.ReaderMode
import com.karelisio.trame.data.repo.ReaderPrefs
import com.karelisio.trame.ui.components.rememberZoomState
import com.karelisio.trame.ui.components.zoomable
import com.karelisio.trame.ui.reader.ImmersiveMode
import com.karelisio.trame.ui.fx.HalftoneLoader
import com.karelisio.trame.ui.fx.HalftonePlaceholder
import com.karelisio.trame.ui.theme.LocalReduceMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val SepiaMatrix = ColorMatrix(
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** Nuit : couleurs inversées, contraste adouci et teinte chaude. */
private val NightMatrix = ColorMatrix(
    floatArrayOf(
        -0.80f, 0f, 0f, 0f, 215f,
        0f, -0.78f, 0f, 0f, 205f,
        0f, 0f, -0.72f, 0f, 185f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

private fun ReaderFilter.colorFilter() = when (this) {
    ReaderFilter.NONE -> null
    ReaderFilter.SEPIA -> ColorFilter.colorMatrix(SepiaMatrix)
    ReaderFilter.NIGHT -> ColorFilter.colorMatrix(NightMatrix)
}

private fun ReaderFilter.background() = when (this) {
    ReaderFilter.NONE -> Color(0xFF111111)
    ReaderFilter.SEPIA -> Color(0xFF2B2117)
    ReaderFilter.NIGHT -> Color(0xFF0B0A09)
}

@Composable
fun LocalReaderScreen(vm: LocalReaderViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    ImmersiveMode()
    WindowBrightness(prefs.brightness)

    Box(Modifier.fillMaxSize().background(prefs.filter.background())) {
        when (val s = state) {
            LocalReaderState.Loading -> HalftoneLoader(
                Modifier.align(Alignment.Center),
                color = Color.White,
                container = Color.White.copy(alpha = 0.12f),
            )
            is LocalReaderState.Error -> Text(s.message, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            is LocalReaderState.Ready -> Reader(s, prefs, vm, onBack)
        }
    }
}

@Composable
private fun Reader(s: LocalReaderState.Ready, prefs: ReaderPrefs, vm: LocalReaderViewModel, onBack: () -> Unit) {
    var controls by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val pager = rememberPagerState(initialPage = s.startPage) { s.source.count }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = s.startPage)
    var currentPage by remember { mutableStateOf(s.startPage) }

    // Changement de mode : on garde la page courante.
    LaunchedEffect(prefs.mode) {
        if (prefs.mode == ReaderMode.PAGED) pager.scrollToPage(currentPage) else list.scrollToItem(currentPage)
    }
    LaunchedEffect(prefs.mode, pager, list) {
        val flow = if (prefs.mode == ReaderMode.PAGED) snapshotFlow { pager.currentPage } else snapshotFlow { list.firstVisibleItemIndex }
        flow.distinctUntilChanged().collect { currentPage = it; vm.onPage(it) }
    }

    fun goTo(page: Int) {
        val p = page.coerceIn(0, s.source.count - 1)
        scope.launch {
            if (prefs.mode == ReaderMode.PAGED) { if (reduce) pager.scrollToPage(p) else pager.animateScrollToPage(p) }
            else list.scrollToItem(p)
        }
    }

    val filter = prefs.filter.colorFilter()
    val onTap: (Float, Float) -> Unit = { x, width ->
        val third = width / 3f
        when {
            prefs.mode == ReaderMode.VERTICAL || x in third..(2 * third) -> controls = !controls
            // Sens japonais : toucher à gauche = page suivante.
            (x < third) == prefs.rightToLeft -> { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); goTo(currentPage + 1) }
            else -> { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); goTo(currentPage - 1) }
        }
    }

    if (prefs.mode == ReaderMode.PAGED) {
        // reverseLayout = lecture droite → gauche.
        HorizontalPager(
            state = pager,
            reverseLayout = prefs.rightToLeft,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val zoom = rememberZoomState()
            LaunchedEffect(pager.currentPage) { if (pager.currentPage != index) zoom.reset() }
            Box(
                Modifier.fillMaxSize().zoomable(zoom) { p, size -> onTap(p.x, size.width.toFloat()) },
                contentAlignment = Alignment.Center,
            ) {
                Page(s.source, index, filter, ContentScale.Fit, Modifier.fillMaxSize())
            }
        }
    } else {
        val zoom = rememberZoomState()
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().zoomable(zoom, panWithOneFinger = false) { p, size -> onTap(p.x, size.width.toFloat()) },
        ) {
            items(s.source.count) { index ->
                Page(s.source, index, filter, ContentScale.FillWidth, Modifier.fillMaxWidth())
            }
        }
    }

    AnimatedVisibility(
        visible = controls,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${currentPage + 1} / ${s.source.count}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ControlsPanel(prefs, currentPage, s.source.count, onSeek = ::goTo, onPrefs = vm::updatePrefs)
        }
    }
}

@Composable
private fun Page(source: PageSource, index: Int, filter: ColorFilter?, scale: ContentScale, modifier: Modifier) {
    val context = LocalContext.current
    var model by remember(source, index) { mutableStateOf<Any?>(null) }
    LaunchedEffect(source, index) { model = runCatching { source.page(index) }.getOrNull() }
    val m = model
    if (m == null) {
        HalftonePlaceholder(modifier.aspectRatio(0.7f), color = Color.White.copy(alpha = 0.12f))
        return
    }
    SubcomposeAsyncImage(
        model = pageRequest(context, m),
        contentDescription = "Page ${index + 1}",
        contentScale = scale,
        colorFilter = filter,
        loading = { HalftonePlaceholder(Modifier.fillMaxWidth().aspectRatio(0.7f), color = Color.White.copy(alpha = 0.12f)) },
        modifier = modifier,
    )
}

@Composable
private fun ControlsPanel(
    prefs: ReaderPrefs,
    page: Int,
    count: Int,
    onSeek: (Int) -> Unit,
    onPrefs: ((ReaderPrefs) -> ReaderPrefs) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (count > 1) {
                var seek by remember(page) { mutableFloatStateOf(page.toFloat()) }
                // Le slider suit le sens de lecture.
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (prefs.rightToLeft && prefs.mode == ReaderMode.PAGED) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    Slider(
                        value = seek,
                        onValueChange = { seek = it },
                        onValueChangeFinished = { onSeek(seek.toInt()) },
                        valueRange = 0f..(count - 1).toFloat(),
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(
                    Triple("Japonais", ReaderMode.PAGED, true),
                    Triple("Occidental", ReaderMode.PAGED, false),
                    Triple("Vertical", ReaderMode.VERTICAL, prefs.rightToLeft),
                )
                options.forEachIndexed { i, (label, mode, rtl) ->
                    val selected = if (mode == ReaderMode.VERTICAL) prefs.mode == mode else prefs.mode == mode && prefs.rightToLeft == rtl
                    SegmentedButton(
                        selected = selected,
                        onClick = { onPrefs { it.copy(mode = mode, rightToLeft = rtl) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFilter.entries.forEach { f ->
                    FilterChip(selected = prefs.filter == f, onClick = { onPrefs { it.copy(filter = f) } }, label = { Text(f.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(
                    checked = prefs.brightness < 0,
                    onCheckedChange = { auto -> onPrefs { it.copy(brightness = if (auto) -1f else 0.5f) } },
                ) { Icon(if (prefs.brightness < 0) Icons.Rounded.BrightnessAuto else Icons.Rounded.BrightnessMedium, "Luminosité automatique") }
                Spacer(Modifier.width(4.dp))
                Slider(
                    value = if (prefs.brightness < 0) 0.5f else prefs.brightness,
                    onValueChange = { v -> onPrefs { it.copy(brightness = v.coerceIn(0.02f, 1f)) } },
                    enabled = prefs.brightness >= 0,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Luminosité propre à la liseuse (fenêtre uniquement, rétablie en sortie). */
@Composable
private fun WindowBrightness(value: Float) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, value) {
        val window = activity.window
        window.attributes = window.attributes.apply { screenBrightness = if (value < 0) -1f else value }
        onDispose { window.attributes = window.attributes.apply { screenBrightness = -1f } }
    }
}
