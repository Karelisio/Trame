package com.karelisio.trame.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.karelisio.trame.ui.components.SpeechBubbleShape
import com.karelisio.trame.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BubbleMessage(val text: String, val icon: ImageVector? = null, val id: Long = System.nanoTime())

/** Notifications in-app en bulle de dialogue (remplacent les snackbars). */
@Stable
class BubbleController {
    var current by mutableStateOf<BubbleMessage?>(null)
        private set

    fun show(text: String, icon: ImageVector? = null) { current = BubbleMessage(text, icon) }
    internal fun clear(id: Long) { if (current?.id == id) current = null }
}

val LocalBubbles = staticCompositionLocalOf { BubbleController() }

/** Hôte : la bulle « pop » en haut de l'écran avec un ressort, puis s'envole. */
@Composable
fun BubbleHost(controller: BubbleController, modifier: Modifier = Modifier) {
    val msg = controller.current ?: return
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val scale = remember(msg.id) { Animatable(if (reduce) 1f else 0f) }
    val lift = remember(msg.id) { Animatable(0f) }
    val tilt = remember(msg.id) { Animatable(if (reduce) 0f else -9f) }
    LaunchedEffect(msg.id) {
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        if (!reduce) {
            launch { tilt.animateTo(-2f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            scale.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow))
        }
        delay(2400)
        if (!reduce) {
            launch { lift.animateTo(1f, tween(260)) }
            scale.animateTo(0f, tween(260))
        }
        controller.clear(msg.id)
    }
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale.value; scaleY = scale.value
                    rotationZ = tilt.value
                    translationY = -lift.value * 60f
                    alpha = 1f - lift.value
                    transformOrigin = TransformOrigin(0.1f, 1f)
                }
                .widthIn(max = 340.dp)
                .shadow(10.dp, SpeechBubbleShape)
                .clip(SpeechBubbleShape)
                .background(MaterialTheme.colorScheme.inverseSurface)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                msg.icon?.let {
                    Icon(it, null, tint = MaterialTheme.colorScheme.inversePrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(msg.text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}
