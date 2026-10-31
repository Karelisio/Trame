package com.karelisio.trame.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karelisio.trame.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

/** Heure courante, rafraîchie toutes les [periodMs]. */
@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs - System.currentTimeMillis() % periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Nombre animé : chaque chiffre glisse verticalement quand il change (compteurs, non lus). */
@Composable
fun AnimatedNumber(value: Long, style: TextStyle, color: Color = Color.Unspecified, minDigits: Int = 1, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val text = value.toString().padStart(minDigits, '0')
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    if (reduce) fadeIn() togetherWith fadeOut()
                    else {
                        val spec = spring<IntOffset>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                        val down = targetState < initialState // compte à rebours : les chiffres descendent
                        (slideInVertically(spec) { if (down) -it else it } + fadeIn()) togetherWith
                            (slideOutVertically(spec) { if (down) it else -it } + fadeOut())
                    }
                },
                label = "digit$i",
            ) { Text(it.toString(), style = style, color = color) }
        }
    }
}

/** Compte à rebours "2 j 04 h 12 min 09 s" avec chiffres animés. */
@Composable
fun Countdown(targetAt: Long, modifier: Modifier = Modifier, compact: Boolean = false) {
    val now = rememberNow()
    val left = (targetAt - now).coerceAtLeast(0) / 1000
    val d = left / 86_400; val h = (left % 86_400) / 3600; val m = (left % 3600) / 60; val s = left % 60
    val num = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displaySmall
    val unit = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp), verticalAlignment = Alignment.Bottom) {
        if (d > 0) CountUnit(d, "j", num, unit, color, 1)
        CountUnit(h, "h", num, unit, color, 2)
        CountUnit(m, "min", num, unit, color, 2)
        if (!compact || d == 0L) CountUnit(s, "s", num, unit, color, 2)
    }
}

@Composable
private fun CountUnit(v: Long, label: String, num: TextStyle, unit: TextStyle, color: Color, digits: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedNumber(v, num, minDigits = digits)
        Text(label, style = unit, color = color)
    }
}

/** Libellé court relatif : "dans 3 j", "dans 5 h", "imminent". */
fun shortUntil(targetAt: Long, now: Long = System.currentTimeMillis()): String {
    val left = targetAt - now
    return when {
        left <= 0 -> "imminent"
        left < 3_600_000 -> "dans ${left / 60_000} min"
        left < 86_400_000 -> "dans ${left / 3_600_000} h"
        else -> "dans ${left / 86_400_000} j"
    }
}
