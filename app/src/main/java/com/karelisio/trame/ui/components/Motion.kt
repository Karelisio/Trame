package com.karelisio.trame.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.karelisio.trame.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Trajectoire des éléments partagés : ressort légèrement rebondi (M3 Expressive). */
private val ExpressiveBounds = BoundsTransform { _, _ -> spring(dampingRatio = 0.82f, stiffness = 340f, visibilityThreshold = Rect.VisibilityThreshold) }

/** Élément partagé (couverture → fiche → lecteur). Sans effet si aucun scope n'est fourni. */
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            boundsTransform = ExpressiveBounds,
        )
    }
}

@Composable
fun Modifier.sharedBounds(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            boundsTransform = ExpressiveBounds,
        )
    }
}

/** Entrée en cascade (index → délai), avec ressort. Joué une seule fois par élément. */
@Composable
fun Modifier.cascadeIn(index: Int, key: Any = index): Modifier {
    val reduce = LocalReduceMotion.current
    val played = rememberSaveable(key) { booleanArrayOf(reduce) }
    val progress = remember(key) { Animatable(if (played[0]) 1f else 0f) }
    LaunchedEffect(key) {
        if (!played[0]) {
            delay((index.coerceAtMost(12) * 45L))
            launch { progress.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            played[0] = true
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 80f
        scaleX = 0.9f + 0.1f * p
        scaleY = 0.9f + 0.1f * p
    }
}

/**
 * Micro-interaction : l'élément s'enfonce légèrement quand on appuie, puis rebondit.
 * À combiner avec le même [interaction] que le clickable / Surface.
 */
@Composable
fun Modifier.pressBounce(interaction: InteractionSource, pressedScale: Float = 0.95f): Modifier {
    val reduce = LocalReduceMotion.current
    val pressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (reduce) return@LaunchedEffect
        scale.animateTo(
            if (pressed) pressedScale else 1f,
            if (pressed) spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh)
            else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        )
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
