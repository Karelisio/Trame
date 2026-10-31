package com.karelisio.trame.fx

import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.karelisio.trame.ui.fx.HALFTONE_AGSL_FOR_TEST
import com.karelisio.trame.ui.fx.halftoneMask
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compile le shader AGSL avec le vrai compilateur Skia (graphismes natifs Robolectric) et le rend. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h300dp-xxhdpi", application = android.app.Application::class)
class ShaderScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /**
     * Le Skia de Robolectric précède la syntaxe AGSL `shader.eval(p)` (Android 13+) et ne connaît
     * que l'ancienne forme `sample(shader, p)` : on convertit cette seule instruction pour valider
     * tout le reste du shader avec le vrai compilateur SkSL.
     */
    @Test fun agslCompiles() {
        val legacy = HALFTONE_AGSL_FOR_TEST.replace("content.eval(coord)", "sample(content, coord)")
        check(legacy != HALFTONE_AGSL_FOR_TEST) { "Appel content.eval(coord) introuvable" }
        RuntimeShader(legacy) // lève IllegalArgumentException si le SkSL est invalide
    }

    @Test fun maskRenderWithShaderFallback() {
        compose.setContent {
            Row(Modifier.background(Color(0xFFFFF8EE)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.25f, 0.5f, 0.75f, 0.95f).forEach { p ->
                    Box(
                        Modifier.size(90.dp, 130.dp)
                            .halftoneMask(progress = { p }, origin = { Offset.Zero }, cell = 7.dp, spread = 0.9f)
                            .background(Brush.linearGradient(listOf(Color(0xFFB3202A), Color(0xFF2B1B5A)))),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/halftone_agsl.png")
    }
}
