package com.karelisio.trame.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.karelisio.trame.ui.fx.HalftoneEasing
import com.karelisio.trame.ui.fx.HalftonePlaceholder
import com.karelisio.trame.ui.fx.halftoneMask
import kotlinx.coroutines.launch
import com.karelisio.trame.ui.theme.LocalReduceMotion

/**
 * Couverture avec cache Coil et repli typographique si absente.
 * Pendant le chargement : trame ondulante. Une fois chargée (hors cache mémoire),
 * l'image apparaît à travers une trame de points qui grossit depuis le coin supérieur gauche.
 */
@Composable
fun Cover(url: String?, title: String, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val reveal = remember(url) { Animatable(0f) }
    // Image servie par le cache mémoire : affichée tout de suite, sans trame.
    var instant by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    val revealed by remember(url) { derivedStateOf { instant || reveal.value >= 1f } }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null || failed) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            if (!revealed) HalftonePlaceholder(Modifier.matchParentSize())
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(false).build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                onState = { st ->
                    when (st) {
                        is AsyncImagePainter.State.Success ->
                            if (reduce || st.result.dataSource == DataSource.MEMORY_CACHE) instant = true
                            else scope.launch { reveal.animateTo(1f, tween(650, easing = HalftoneEasing)) }
                        is AsyncImagePainter.State.Error -> failed = true
                        else -> Unit
                    }
                },
                modifier = Modifier.matchParentSize().halftoneMask(
                    progress = { if (instant) 1f else reveal.value },
                    cell = 7.dp,
                    spread = 0.9f,
                ),
            )
        }
    }
}

/** Bulle de dialogue (rectangle arrondi + queue en bas à gauche). */
val SpeechBubbleShape = GenericShape { size, _ ->
    val tail = size.height * 0.28f
    val h = size.height - tail
    val r = h / 2f
    addRoundRect(
        androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, h, androidx.compose.ui.geometry.CornerRadius(r, r)),
    )
    moveTo(r * 0.7f, h - 1f)
    lineTo(r * 0.35f, size.height)
    lineTo(r * 1.5f, h - 1f)
    close()
}

/** Badge en bulle qui « pop » avec un ressort (et une légère rotation) à son apparition. */
@Composable
fun BubbleBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    BubbleBadgeFrame(modifier, container, wiggleKey = Unit) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** Compteur de non lus en bulle : « Nouveau ! » pour 1, sinon chiffres animés ; secousse quand il augmente. */
@Composable
fun UnreadBubble(count: Int, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onPrimary
    BubbleBadgeFrame(modifier, MaterialTheme.colorScheme.primary, wiggleKey = count) {
        if (count == 1) {
            Text("Nouveau !", style = MaterialTheme.typography.labelMedium, color = color)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedNumber(count.toLong(), MaterialTheme.typography.labelMedium, color = color)
                Text(" non lus", style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

@Composable
private fun BubbleBadgeFrame(
    modifier: Modifier,
    container: Color,
    wiggleKey: Any,
    content: @Composable () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(if (reduce) 1f else 0f) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reduce) scale.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow))
    }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(wiggleKey) {
        if (first) { first = false; return@LaunchedEffect }
        if (reduce) return@LaunchedEffect
        rotation.snapTo(-12f)
        launch { scale.snapTo(1.25f); scale.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium)) }
        rotation.animateTo(0f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium))
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                rotationZ = rotation.value
                transformOrigin = TransformOrigin(0.15f, 1f)
            }
            .clip(SpeechBubbleShape)
            .background(container)
            .padding(start = 10.dp, end = 10.dp, top = 3.dp, bottom = 9.dp),
    ) { content() }
}

/** Pastille aux formes M3 Expressive (statut de la série). */
@Composable
fun StatusShapeDot(ongoing: Boolean, modifier: Modifier = Modifier) {
    val shape = (if (ongoing) MaterialShapes.Sunny else MaterialShapes.Square).toShape()
    Box(
        modifier
            .clip(shape)
            .background(if (ongoing) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline),
    )
}

fun statusLabel(status: String?) = when (status) {
    "ongoing" -> "En cours"
    "completed" -> "Terminé"
    "hiatus" -> "En pause"
    "cancelled" -> "Annulé"
    else -> null
}

fun formatChapter(n: Double?): String? = n?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
