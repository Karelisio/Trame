package com.karelisio.trame.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.karelisio.trame.ui.components.UnreadBubble
import com.karelisio.trame.ui.fx.BubbleController
import com.karelisio.trame.ui.fx.BubbleHost
import com.karelisio.trame.ui.fx.HalftoneLoader
import com.karelisio.trame.ui.fx.HalftonePlaceholder
import com.karelisio.trame.ui.fx.ImpactController
import com.karelisio.trame.ui.fx.ImpactEvent
import com.karelisio.trame.ui.fx.ImpactOverlay
import com.karelisio.trame.ui.fx.SpeedLines
import com.karelisio.trame.ui.fx.halftoneMask
import com.karelisio.trame.ui.theme.TrameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendu des effets signature, pour vérification visuelle (build/screenshots/). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [32], qualifiers = "w400dp-h800dp-xxhdpi", application = android.app.Application::class)
class EffectsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/screenshots/$name.png")

    @Test fun halftoneReveal() {
        compose.setContent {
            TrameTheme(dynamicColor = false) {
                Row(Modifier.background(MaterialTheme.colorScheme.surface).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.25f, 0.5f, 0.75f, 0.95f).forEach { p ->
                        Box(
                            Modifier.size(90.dp, 130.dp)
                                .halftoneMask(progress = { p }, origin = { Offset.Zero }, cell = 7.dp, spread = 0.9f)
                                .background(Brush.linearGradient(listOf(Color(0xFFB3202A), Color(0xFF2B1B5A)))),
                        )
                    }
                }
            }
        }
        shot("halftone_reveal")
    }

    @Test fun loaders() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TrameTheme(dynamicColor = false) {
                Row(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    HalftoneLoader()
                    HalftonePlaceholder(Modifier.padding(start = 16.dp).size(90.dp, 130.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UnreadBubble(1)
                        UnreadBubble(12)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(700)
        shot("loaders")
    }

    @Test fun speedLines() {
        compose.setContent {
            TrameTheme(dynamicColor = false) {
                Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    listOf(0.3f, 0.9f).forEach { k ->
                        SpeedLines(
                            intensity = { k },
                            color = Color.Black,
                            focus = { Offset(0.5f, 0.3f) },
                            flicker = false,
                            modifier = Modifier.size(200.dp, 300.dp),
                        )
                    }
                }
            }
        }
        shot("speed_lines")
    }

    @Test fun impactAndBubble() {
        compose.mainClock.autoAdvance = false
        val impact = ImpactController().apply { trigger(ImpactEvent("op", "One Piece", "1162", 1)) }
        val bubbles = BubbleController().apply { show("One Piece ajouté à ta bibliothèque") }
        compose.setContent {
            TrameTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    Text("Bibliothèque", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(24.dp))
                    ImpactOverlay(impact, onOpen = {})
                    BubbleHost(bubbles)
                }
            }
        }
        compose.mainClock.advanceTimeBy(1200)
        shot("impact")
    }
}
