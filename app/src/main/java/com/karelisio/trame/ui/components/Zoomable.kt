package com.karelisio.trame.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import com.karelisio.trame.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch

@Stable
class ZoomState(private val maxScale: Float = 5f) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    val isZoomed get() = scale > 1.01f

    private fun clamp(o: Offset, s: Float, size: IntSize): Offset {
        val mx = size.width * (s - 1f) / 2f
        val my = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    /** Zoom autour du centroïde (coordonnées locales), puis déplacement. */
    fun transform(zoom: Float, pan: Offset, centroid: Offset, size: IntSize) {
        val newScale = (scale * zoom).coerceIn(1f, maxScale)
        val c = centroid - Offset(size.width / 2f, size.height / 2f)
        val o = c - (c - offset) * (newScale / scale) + pan
        scale = newScale
        offset = clamp(o, newScale, size)
    }

    suspend fun toggle(tap: Offset, size: IntSize, animated: Boolean) {
        val targetScale = if (isZoomed) 1f else 2.5f
        val c = tap - Offset(size.width / 2f, size.height / 2f)
        val target = if (targetScale == 1f) Offset.Zero else clamp(c * (1f - targetScale), targetScale, size)
        val s0 = scale; val o0 = offset
        if (!animated) { scale = targetScale; offset = target; return }
        animate(0f, 1f, animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { v, _ ->
            scale = lerp(s0, targetScale, v)
            offset = lerp(o0, target, v)
        }
    }

    fun reset() { scale = 1f; offset = Offset.Zero }
}

@Composable
fun rememberZoomState() = remember { ZoomState() }

/**
 * Pinch-zoom + double-tap. Un doigt sans zoom n'est pas consommé (le pager / la liste défilent).
 * [panWithOneFinger] = false : en mode vertical, seul le pinch déplace (la liste garde le défilement).
 */
@Composable
fun Modifier.zoomable(
    state: ZoomState,
    panWithOneFinger: Boolean = true,
    onTap: (Offset, IntSize) -> Unit = { _, _ -> },
): Modifier {
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    return this
        .pointerInput(state) {
            detectTapGestures(
                onDoubleTap = { p -> scope.launch { state.toggle(p, size, !reduce) } },
                onTap = { p -> onTap(p, size) },
            )
        }
        .pointerInput(state, panWithOneFinger) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val pointers = event.changes.count { it.pressed }
                    val multi = pointers > 1
                    if (multi || (state.isZoomed && panWithOneFinger)) {
                        state.transform(event.calculateZoom(), event.calculatePan(), event.calculateCentroid(useCurrent = true), size)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .graphicsLayer {
            scaleX = state.scale; scaleY = state.scale
            translationX = state.offset.x; translationY = state.offset.y
        }
}

private fun androidx.compose.ui.input.pointer.PointerInputChange.positionChanged() = position != previousPosition
