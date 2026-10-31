package com.karelisio.trame.ui.onboarding

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.karelisio.trame.permissions.AppPermission
import com.karelisio.trame.permissions.Permissions
import com.karelisio.trame.ui.components.MorphShape
import com.karelisio.trame.ui.components.SpeechBubbleShape
import com.karelisio.trame.ui.theme.LocalReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.sin

private sealed interface Page {
    data object Welcome : Page
    data class Feature(val icon: ImageVector, val title: String, val text: String) : Page
    data class Permission(val p: AppPermission) : Page
    data object Done : Page
}

private val pages: List<Page> = listOf(
    Page.Welcome,
    Page.Feature(Icons.Rounded.Schedule, "Jamais en retard", "Trame suit tes séries et te prévient dès qu'un chapitre sort. Compte à rebours jusqu'au prochain, détection des pauses."),
    Page.Feature(Icons.AutoMirrored.Rounded.MenuBook, "Lis officiellement", "Les chapitres MANGA Plus s'ouvrent dans l'appli, en plein écran. Tes CBZ et dossiers d'images aussi, en sens japonais."),
) + AppPermission.entries.map { Page.Permission(it) } + Page.Done

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val states = rememberPermissionStates()
    val request = rememberPermissionRequester()
    val batteryOk = states[AppPermission.BATTERY] == true
    val batteryIndex = pages.indexOfFirst { it is Page.Permission && it.p == AppPermission.BATTERY }
    // L'exclusion batterie est obligatoire : on ne peut pas aller au-delà tant qu'elle n'est pas accordée.
    val pageCount = if (batteryOk) pages.size else batteryIndex + 1
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            when (val p = pages[i]) {
                Page.Welcome -> PageLayout(
                    illustration = { WelcomeBubble() },
                    title = "Trame",
                    text = "Tes mangas, dès leur sortie.\nPensé pour One Piece, prêt pour toutes tes séries.",
                )
                is Page.Feature -> PageLayout({ ShapeIllustration(p.icon) }, p.title, p.text)
                is Page.Permission -> PageLayout(
                    illustration = { ShapeIllustration(p.p.icon, granted = states[p.p] == true) },
                    title = p.p.title,
                    text = p.p.rationale,
                ) {
                    GrantButton(states[p.p] == true, onClick = { request(p.p) })
                    if (p.p == AppPermission.BATTERY) BatteryExtras(batteryOk)
                }
                Page.Done -> PageLayout({ WelcomeBubble() }, "C'est parti !", "One Piece est déjà dans ta bibliothèque. Ajoute tes autres séries avec la recherche.") {
                    Button(onClick = onFinish, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp)) {
                        Text("Commencer", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            PageDots(pages.size, pager.currentPage, Modifier.weight(1f))
            val last = pager.currentPage == pages.lastIndex
            val blocked = pager.currentPage == batteryIndex && !batteryOk
            if (!last) {
                Button(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    enabled = !blocked,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(if (blocked) "Requis" else "Suivant")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun BatteryExtras(granted: Boolean) {
    val context = LocalContext.current
    if (!granted) {
        Text(
            "Indispensable pour recevoir les notifications.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    TextButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Permissions.dontKillMyAppUrl().toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) { Text("Conseils pour ${Permissions.manufacturer} (dontkillmyapp.com)") }
}

@Composable
private fun PageLayout(
    illustration: @Composable () -> Unit,
    title: String,
    text: String,
    actions: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) { illustration() }
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

/** Indicateur de pages : la pastille active s'allonge. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 24.dp else 8.dp, label = "dot")
            val c by animateColorAsState(
                if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dotc",
            )
            Box(Modifier.width(w).height(8.dp).clip(CircleShape).background(c))
        }
    }
}

/** Icône dans une forme M3 Expressive qui tourne et morphe en boucle ; devient « Sunny » une fois accordé. */
@Composable
private fun ShapeIllustration(icon: ImageVector, granted: Boolean = false) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "illu")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "rot")
    val morph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "morph")
    val bounce by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "bounce")
    val shape = remember(morph, granted) {
        if (granted) MorphShape(MaterialShapes.Sunny, MaterialShapes.Cookie12Sided, if (reduce) 0f else morph)
        else MorphShape(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, if (reduce) 0f else morph)
    }
    val container by animateColorAsState(
        if (granted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer, label = "c",
    )
    Box(
        Modifier.size(200.dp).graphicsLayer { rotationZ = if (reduce) 0f else rot }.clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null,
            tint = if (granted) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(84.dp).graphicsLayer {
                rotationZ = if (reduce) 0f else -rot
                val s = if (reduce) 1f else 1f + 0.08f * bounce
                scaleX = s; scaleY = s
            },
        )
    }
}

/** Bulle de dialogue remplie d'une trame de points qui ondule. */
@Composable
private fun WelcomeBubble() {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "bubble")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "phase")
    val paper = MaterialTheme.colorScheme.surfaceContainerHighest
    val ink = MaterialTheme.colorScheme.primary
    Box(Modifier.size(220.dp, 190.dp).clip(SpeechBubbleShape).background(paper)) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 14.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else step / 2
                while (x < size.width) {
                    val diag = (x / size.width + y / size.height) / 2f
                    val wave = if (reduce) 0f else 0.25f * sin(phase + diag * 6f)
                    val r = step * 0.48f * (diag + wave).coerceIn(0.05f, 1f)
                    drawCircle(ink, r, Offset(x, y))
                    x += step
                }
                y += step * 0.866f
                row++
            }
        }
    }
}
