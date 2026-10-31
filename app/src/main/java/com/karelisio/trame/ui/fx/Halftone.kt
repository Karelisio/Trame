package com.karelisio.trame.ui.fx

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.trame.ui.components.SpeechBubbleShape
import com.karelisio.trame.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import android.graphics.RenderEffect as PlatformRenderEffect

/*
 * Trame — l'effet signature de l'appli.
 * Le contenu n'apparaît qu'à travers une trame de points (halftone) dont les points
 * grossissent jusqu'à se rejoindre (révélation) ou rétrécissent jusqu'à disparaître (dissolution).
 * La progression part d'une origine (le doigt de l'utilisateur, un coin de couverture…).
 *
 * API 33+ : shader AGSL (RuntimeShader) appliqué en RenderEffect, sans recomposition.
 * API < 33 : même rendu dessiné au Canvas (calque hors écran + BlendMode.DstIn).
 */

private const val HALFTONE_AGSL = """
uniform shader content;
uniform float2 size;
uniform float2 origin;
uniform float progress;
uniform float cell;
uniform float spread;
uniform float invert;

half4 main(float2 coord) {
    float row = floor(coord.y / cell);
    float shift = mod(row, 2.0) * 0.5 * cell;
    float2 g = float2(coord.x + shift, coord.y);
    float2 c = (floor(g / cell) + 0.5) * cell;
    float dist = length(g - c);
    float2 cs = float2(c.x - shift, c.y);
    float maxD = length(max(origin, size - origin));
    float t = clamp(length(cs - origin) / maxD, 0.0, 1.0);
    t = mix(t, 1.0 - t, invert);
    float lp = clamp(progress * (1.0 + spread) - t * spread, 0.0, 1.0);
    float eased = lp * lp * (3.0 - 2.0 * lp);
    float r = eased * cell * 0.75;
    float m = clamp(r - dist + 0.5, 0.0, 1.0);
    if (m <= 0.0) { return half4(0.0); }
    return content.eval(coord) * half(m);
}
"""

/** Exposé pour le test de compilation du shader. */
@androidx.annotation.VisibleForTesting
internal const val HALFTONE_AGSL_FOR_TEST = HALFTONE_AGSL

/** Rayon d'un point pour la même formule que le shader (repli Canvas). */
private fun dotRadius(cx: Float, cy: Float, w: Float, h: Float, o: Offset, p: Float, cell: Float, spread: Float, invert: Boolean): Float {
    val maxD = hypot(max(o.x, w - o.x), max(o.y, h - o.y))
    var t = (hypot(cx - o.x, cy - o.y) / maxD).coerceIn(0f, 1f)
    if (invert) t = 1f - t
    val local = (p * (1f + spread) - t * spread).coerceIn(0f, 1f)
    val eased = local * local * (3f - 2f * local)
    return eased * cell * 0.75f
}

/** Parcourt la grille décalée (une ligne sur deux décalée d'une demi-cellule). */
private inline fun forEachCell(w: Float, h: Float, cell: Float, block: (Float, Float) -> Unit) {
    val rows = (h / cell).toInt() + 1
    for (row in 0..rows) {
        val shift = if (row % 2 == 1) cell / 2f else 0f
        val cy = (row + 0.5f) * cell
        var col = -1
        while (true) {
            val cx = (col + 0.5f) * cell - shift
            if (cx - cell > w) break
            block(cx, cy)
            col++
        }
    }
}

/**
 * Masque halftone : le contenu n'est visible qu'à travers des points dont le rayon
 * dépend de [progress] (0 = invisible, 1 = entièrement visible, sans effet).
 * Les lambdas sont lues pendant le dessin : aucune recomposition par frame.
 */
@Composable
fun Modifier.halftoneMask(
    progress: () -> Float,
    origin: () -> Offset = { Offset.Zero },
    cell: Dp = 14.dp,
    spread: Float = 0.7f,
    invert: () -> Boolean = { false },
    enabled: () -> Boolean = { true },
): Modifier {
    if (AgslSupport.available) {
        val holder = remember { ShaderHolder() }
        return graphicsLayer {
            val p = progress()
            if (!enabled() || p >= 0.999f) {
                renderEffect = null
                alpha = 1f
                return@graphicsLayer
            }
            if (p <= 0.001f) {
                renderEffect = null
                alpha = 0f
                return@graphicsLayer
            }
            alpha = 1f
            val effect = holder.effect(size.width, size.height, origin(), p, cell.toPx(), spread, invert())
            renderEffect = effect
            if (effect == null) alpha = p // filet de sécurité, ne devrait pas arriver (cf. AgslSupport)
        }
    }
    return this
        .graphicsLayer {
            val p = progress()
            compositingStrategy = if (enabled() && p < 0.999f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            alpha = if (enabled() && p <= 0.001f) 0f else 1f
        }
        .drawWithContent {
            drawContent()
            val p = progress()
            if (!enabled() || p >= 0.999f || p <= 0.001f) return@drawWithContent
            drawHalftoneMask(p, origin(), cell.toPx(), spread, invert())
        }
}

private val maskPaint = Paint().apply { blendMode = BlendMode.DstIn }

private fun DrawScope.drawHalftoneMask(p: Float, o: Offset, cell: Float, spread: Float, invert: Boolean) {
    val w = size.width; val h = size.height
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), maskPaint)
        forEachCell(w, h, cell) { cx, cy ->
            val r = dotRadius(cx, cy, w, h, o, p, cell, spread, invert)
            if (r > 0.3f) drawCircle(Color.Black, r, Offset(cx, cy))
        }
        canvas.restore()
    }
}

/**
 * Le shader est compilé une fois au premier usage : s'il échoue (GPU / version de Skia exotique),
 * toute l'appli bascule sur le rendu Canvas, sans crash et sans perdre l'effet.
 */
private object AgslSupport {
    val available: Boolean by lazy {
        Build.VERSION.SDK_INT >= 33 && runCatching { RuntimeShader(HALFTONE_AGSL) }.isSuccess
    }
}

@RequiresApi(33)
private class ShaderHolderImpl {
    val shader = RuntimeShader(HALFTONE_AGSL)
}

/** Garde le RuntimeShader (créé une fois) ; ne référence la classe API 33 qu'à l'exécution. */
private class ShaderHolder {
    private var impl: Any? = null
    private var failed = false

    @RequiresApi(33)
    fun effect(w: Float, h: Float, o: Offset, p: Float, cell: Float, spread: Float, invert: Boolean): androidx.compose.ui.graphics.RenderEffect? {
        if (failed) return null
        val holder = (impl as? ShaderHolderImpl)
            ?: runCatching { ShaderHolderImpl() }.getOrNull()?.also { impl = it }
            ?: run { failed = true; return null }
        val s = holder.shader
        s.setFloatUniform("size", w, h)
        s.setFloatUniform("origin", o.x, o.y)
        s.setFloatUniform("progress", p)
        s.setFloatUniform("cell", cell)
        s.setFloatUniform("spread", spread)
        s.setFloatUniform("invert", if (invert) 1f else 0f)
        return PlatformRenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }
}

/**
 * Placeholder de chargement : une trame qui ondule en diagonale (remplace le shimmer).
 * L'animation est lue au dessin uniquement.
 */
@Composable
fun HalftonePlaceholder(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
    cell: Dp = 9.dp,
) {
    val reduce = LocalReduceMotion.current
    val phase = rememberInfiniteTransition(label = "placeholder")
        .animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "phase")
    Canvas(modifier.clipToBounds()) {
        val c = cell.toPx()
        val ph = if (reduce) 1f else phase.value
        val diag = size.width + size.height
        forEachCell(size.width, size.height, c) { cx, cy ->
            val wave = 0.5f + 0.5f * sin(ph - (cx + cy) / diag * 7f)
            val r = c * (0.12f + 0.3f * wave)
            drawCircle(color, r, Offset(cx, cy))
        }
    }
}

/**
 * Indicateur de chargement maison : une bulle de dialogue remplie d'une trame
 * dont les points pulsent en onde depuis le centre.
 */
@Composable
fun HalftoneLoader(
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
) {
    val reduce = LocalReduceMotion.current
    val phase = rememberInfiniteTransition(label = "loader")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "phase")
    Box(modifier.size(size, size * 0.86f).clip(SpeechBubbleShape).background(container)) {
        Canvas(Modifier.size(size, size * 0.86f)) {
            val c = this.size.width / 9f
            val center = Offset(this.size.width / 2f, this.size.height * 0.36f)
            val maxD = this.size.width * 0.6f
            val ph = if (reduce) 0.25f else phase.value
            forEachCell(this.size.width, this.size.height, c) { cx, cy ->
                val d = hypot(cx - center.x, cy - center.y) / maxD
                val x = ph - d * 0.8f
                val wave = 0.5f + 0.5f * sin(((x - floor(x)) * 2f * PI).toFloat())
                drawCircle(color, c * (0.1f + 0.38f * wave * wave), Offset(cx, cy))
            }
        }
    }
}
