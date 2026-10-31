package com.karelisio.trame.ui.fx

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.karelisio.trame.ui.theme.LocalReduceMotion

/** Durée de la transition « trame » entre écrans. */
const val HALFTONE_TRANSITION_MS = 560

/** Accélération forte puis ressort doux : les points « gonflent » puis se posent. */
val HalftoneEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/**
 * État partagé des transitions : dernier point touché (origine de la trame)
 * et sens de navigation (avant / retour), renseigné par les lambdas de transition du NavHost.
 */
@Stable
class TransitionState {
    var lastTap by mutableStateOf(Offset.Unspecified)
    var popping by mutableStateOf(false)
}

val LocalTransitionState = staticCompositionLocalOf { TransitionState() }

/** Enregistre la position de chaque appui (passe Initial, sans rien consommer). */
fun Modifier.trackTaps(state: TransitionState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { state.lastTap = it.position }
        }
    }
}

/**
 * Transition signature d'un écran :
 * - en avant, le nouvel écran apparaît à travers une trame qui grossit depuis le doigt ;
 * - au retour (y compris le retour prédictif, piloté par le geste), l'écran se dissout en points
 *   depuis le bouton retour.
 * L'écran qui reste dessous est géré par les fondus/échelles du NavHost.
 */
@Composable
fun Modifier.halftoneScreen(scope: AnimatedVisibilityScope): Modifier {
    if (LocalReduceMotion.current) return this
    val state = LocalTransitionState.current
    val progress by scope.transition.animateFloat(
        transitionSpec = { tween(HALFTONE_TRANSITION_MS, easing = HalftoneEasing) },
        label = "halftone",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return halftoneMask(
        progress = { progress },
        origin = {
            val exiting = scope.transition.targetState == EnterExitState.PostExit
            if (exiting) Offset(24.dp.toPxApprox(), 48.dp.toPxApprox())
            else state.lastTap.takeIf { it != Offset.Unspecified } ?: Offset.Zero
        },
        cell = 18.dp,
        spread = 0.85f,
        // Au retour, la dissolution commence au bouton retour et balaie l'écran.
        invert = { scope.transition.targetState == EnterExitState.PostExit },
        enabled = {
            val t = scope.transition
            val exiting = t.targetState == EnterExitState.PostExit
            val entering = t.targetState == EnterExitState.Visible && t.currentState == EnterExitState.PreEnter
            // Avant : seul l'écran entrant est tramé. Retour : seul l'écran sortant se dissout.
            (entering && !state.popping) || (exiting && state.popping)
        },
    )
}

/** Conversion approximative dp → px hors DrawScope (densité lue une fois à l'usage). */
private fun androidx.compose.ui.unit.Dp.toPxApprox(): Float =
    value * android.content.res.Resources.getSystem().displayMetrics.density
