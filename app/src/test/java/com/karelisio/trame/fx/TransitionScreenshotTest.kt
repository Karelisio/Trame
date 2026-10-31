package com.karelisio.trame.fx

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.karelisio.trame.ui.fx.HALFTONE_TRANSITION_MS
import com.karelisio.trame.ui.fx.LocalTransitionState
import com.karelisio.trame.ui.fx.SpeedLines
import com.karelisio.trame.ui.fx.TransitionState
import com.karelisio.trame.ui.fx.halftoneScreen
import com.karelisio.trame.ui.theme.TrameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capture à mi-transition : avant (révélation depuis le doigt) et retour (dissolution). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi", application = android.app.Application::class)
class TransitionScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun run(name: String, popping: Boolean) {
        compose.mainClock.autoAdvance = false
        val screen = mutableIntStateOf(if (popping) 1 else 0)
        val ts = TransitionState().apply { lastTap = Offset(800f, 1500f) }
        compose.setContent {
            TrameTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalTransitionState provides ts) {
                    AnimatedContent(
                        targetState = screen.intValue,
                        transitionSpec = {
                            ts.popping = popping
                            if (!popping) {
                                (EnterTransition.None togetherWith (fadeOut(tween(380, 180)) + scaleOut(tween(HALFTONE_TRANSITION_MS), 0.94f)))
                                    .apply { targetContentZIndex = if (targetState == 1) 1f else -1f }
                            } else {
                                ((fadeIn(tween(260)) + scaleIn(tween(HALFTONE_TRANSITION_MS), 0.94f)) togetherWith
                                    scaleOut(tween(HALFTONE_TRANSITION_MS), 0.9f)).apply { targetContentZIndex = if (targetState == 1) 1f else -1f }
                            }
                        },
                        label = "t",
                    ) { s ->
                        Box(
                            Modifier.fillMaxSize().halftoneScreen(this)
                                .background(if (s == 0) Color(0xFFFFF8EE) else Color(0xFFB3202A)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (s == 0) "Bibliothèque" else "Fiche", style = MaterialTheme.typography.displayMedium,
                                color = if (s == 0) Color.Black else Color.White)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { screen.intValue = if (popping) 0 else 1 }
        compose.mainClock.advanceTimeByFrame()
        listOf(100L, 100L, 100L).forEachIndexed { i, dt ->
            compose.mainClock.advanceTimeBy(dt)
            compose.onRoot().captureRoboImage("build/screenshots/${name}_$i.png")
        }
    }

    @Test fun forward() = run("transition_forward", popping = false)
    @Test fun back() = run("transition_back", popping = true)

    @Test fun pullToRefreshStrip() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFFFFF8EE))) {
                SpeedLines(
                    intensity = { 0.7f },
                    color = Color.Black.copy(alpha = 0.6f),
                    focus = { Offset(0.5f, 0.16f) },
                    lines = 72,
                    flicker = false,
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                )
                Text("Bibliothèque", modifier = Modifier.padding(top = 300.dp, start = 24.dp))
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/ptr_strip.png")
    }
}
