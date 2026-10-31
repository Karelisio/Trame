package com.karelisio.trame.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import com.karelisio.trame.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import androidx.compose.runtime.withFrameNanos

/** Pseudo-aléatoire déterministe (stable pour un même couple ligne/tirage). */
private fun hash(i: Int, seed: Int): Float {
    var x = i * 374761393 + seed * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777215f
}

/**
 * Lignes de vitesse manga (« shūchūsen ») qui convergent vers [focus] (fraction de la taille).
 * [intensity] (0..1) rapproche les pointes du centre et épaissit les traits.
 * Les traits « clignotent » ~16 fois par seconde comme une case animée, sauf si les animations sont réduites.
 * Lecture des lambdas au dessin : aucune recomposition par frame.
 */
@Composable
fun SpeedLines(
    intensity: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    focus: () -> Offset = { Offset(0.5f, 0.5f) },
    lines: Int = 90,
    flicker: Boolean = true,
) {
    val reduce = LocalReduceMotion.current
    val seed = remember { mutableIntStateOf(1) }
    if (flicker && !reduce) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (now - last > 62_000_000L) { last = now; seed.intValue++ }
                }
            }
        }
    }
    val minWidth = with(LocalDensity.current) { 1.2f * density }
    val path = remember { Path() }
    Canvas(modifier.clipToBounds()) {
        val k = intensity().coerceIn(0f, 1f)
        if (k <= 0.01f) return@Canvas
        val f = focus()
        val c = Offset(size.width * f.x, size.height * f.y)
        val outer = hypot(max(c.x, size.width - c.x), max(c.y, size.height - c.y)) * 1.05f
        val s = seed.intValue
        // Rayon intérieur : loin du centre quand l'intensité est faible, proche quand elle est forte.
        val innerBase = outer * (0.95f - 0.62f * k)
        for (i in 0 until lines) {
            val a = (i + hash(i, s) * 0.9f) / lines * 2f * PI.toFloat()
            val inner = innerBase * (0.82f + 0.36f * hash(i + 999, s))
            // Surtout des traits fins, quelques traits épais (distribution au cube).
            val h = hash(i + 7, s)
            val half = minWidth * (0.6f + 2.4f * h * h * h) * (0.6f + 0.6f * k) / outer
            path.reset()
            path.moveTo(c.x + cos(a - half) * outer, c.y + sin(a - half) * outer)
            path.lineTo(c.x + cos(a) * inner, c.y + sin(a) * inner)
            path.lineTo(c.x + cos(a + half) * outer, c.y + sin(a + half) * outer)
            path.close()
            drawPath(path, color.copy(alpha = color.alpha * (0.55f + 0.45f * k)))
        }
    }
}
