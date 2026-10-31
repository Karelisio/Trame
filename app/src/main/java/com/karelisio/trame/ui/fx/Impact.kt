package com.karelisio.trame.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.trame.ui.components.SpeechBubbleShape
import com.karelisio.trame.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ImpactEvent(val seriesId: String, val title: String, val chapter: String?, val count: Int, val id: Long = System.nanoTime())

/** Case « impact » plein écran quand un nouveau chapitre est détecté pendant que l'appli est ouverte. */
@Stable
class ImpactController {
    var current by mutableStateOf<ImpactEvent?>(null)
        private set

    fun trigger(e: ImpactEvent) { current = e }
    fun dismiss() { current = null }
}

/**
 * Lignes de vitesse qui convergent vers une grosse bulle « NOUVEAU ! » qui pop avec un ressort.
 * Un tap sur la bulle ouvre la lecture ; ailleurs, ferme. Disparaît seule après ~2,6 s.
 */
@Composable
fun ImpactOverlay(controller: ImpactController, onOpen: (ImpactEvent) -> Unit) {
    val e = controller.current ?: return
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val lines = remember(e.id) { Animatable(0f) }
    val scrim = remember(e.id) { Animatable(0f) }
    val pop = remember(e.id) { Animatable(if (reduce) 1f else 0f) }
    val tilt = remember(e.id) { Animatable(if (reduce) -3f else -14f) }

    LaunchedEffect(e.id) {
        if (!reduce) {
            launch { scrim.animateTo(1f, tween(160)) }
            launch { lines.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) }
            delay(110)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            launch { tilt.animateTo(-3f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            pop.animateTo(1f, spring(0.42f, Spring.StiffnessMediumLow))
        } else {
            scrim.snapTo(1f)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        delay(2300)
        launch { lines.animateTo(0f, tween(260)) }
        launch { scrim.animateTo(0f, tween(320)) }
        if (!reduce) pop.animateTo(0f, tween(220)) else delay(320)
        controller.dismiss()
    }

    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = scrim.value }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { controller.dismiss() },
        contentAlignment = Alignment.Center,
    ) {
        if (!reduce) {
            SpeedLines(
                intensity = { lines.value },
                color = ink.copy(alpha = 0.9f),
                focus = { Offset(0.5f, 0.47f) },
                lines = 150,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = pop.value; scaleY = pop.value
                    rotationZ = tilt.value
                    transformOrigin = TransformOrigin(0.15f, 1f)
                }
                .widthIn(max = 320.dp)
                .shadow(16.dp, SpeechBubbleShape)
                .clip(SpeechBubbleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { controller.dismiss(); onOpen(e) }
                .padding(start = 30.dp, end = 30.dp, top = 22.dp, bottom = 48.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "NOUVEAU !",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    buildString {
                        append(e.title)
                        e.chapter?.let { append(" – Chapitre $it") }
                        if (e.count > 1) append("\n${e.count} nouveaux chapitres")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Touche pour lire",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
