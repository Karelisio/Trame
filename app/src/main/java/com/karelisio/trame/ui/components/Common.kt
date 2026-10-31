package com.karelisio.trame.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.karelisio.trame.ui.theme.LocalReduceMotion

/** Couverture avec cache Coil et repli typographique si absente. */
@Composable
fun Cover(url: String?, title: String, modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(300).build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
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

/** Badge en bulle qui "pop" avec un ressort à son apparition. */
@Composable
fun BubbleBadge(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.primary, content: Color = MaterialTheme.colorScheme.onPrimary) {
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow),
        label = "bubble",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                transformOrigin = TransformOrigin(0.15f, 1f)
            }
            .clip(SpeechBubbleShape)
            .background(container)
            .padding(start = 10.dp, end = 10.dp, top = 3.dp, bottom = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
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
