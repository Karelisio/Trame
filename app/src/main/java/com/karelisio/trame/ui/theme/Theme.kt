package com.karelisio.trame.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Fallback : rouge encre + crème papier.
val InkRed = Color(0xFFB3202A)
val InkRedDark = Color(0xFFFFB3AE)
val Paper = Color(0xFFFFF8EE)
val PaperDim = Color(0xFFF3E9D8)
val Ink = Color(0xFF1E1B18)

val LightFallback = lightColorScheme(
    primary = InkRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410005),
    secondary = Color(0xFF775653),
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF2C1513),
    tertiary = Color(0xFF715B2E),
    tertiaryContainer = Color(0xFFFDDFA6),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = Color(0xFF534341),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF2E5),
    surfaceContainer = Color(0xFFF6ECDE),
    surfaceContainerHigh = Color(0xFFF0E6D8),
    surfaceContainerHighest = Color(0xFFEAE0D2),
    outline = Color(0xFF857371),
)

val DarkFallback = darkColorScheme(
    primary = InkRedDark,
    onPrimary = Color(0xFF68000F),
    primaryContainer = Color(0xFF8F0D1C),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB8),
    secondaryContainer = Color(0xFF5D3F3C),
    tertiary = Color(0xFFE0C38C),
    tertiaryContainer = Color(0xFF574419),
    background = Color(0xFF17120F),
    onBackground = Color(0xFFEDE0D6),
    surface = Color(0xFF17120F),
    onSurface = Color(0xFFEDE0D6),
    surfaceVariant = Color(0xFF534341),
    surfaceContainerLowest = Color(0xFF110D0B),
    surfaceContainerLow = Color(0xFF1F1A17),
    surfaceContainer = Color(0xFF241E1A),
    surfaceContainerHigh = Color(0xFF2E2824),
    surfaceContainerHighest = Color(0xFF39332E),
)

private val base = Typography()
private fun TextStyle.heavy(weight: FontWeight, tracking: Float = -0.5f) =
    copy(fontFamily = FontFamily.SansSerif, fontWeight = weight, letterSpacing = tracking.sp)

/** Typographie expressive : titres serrés et épais, façon couverture de tankōbon. */
val TrameTypography = Typography(
    displayLarge = base.displayLarge.heavy(FontWeight.Black, -1.5f),
    displayMedium = base.displayMedium.heavy(FontWeight.Black, -1f),
    displaySmall = base.displaySmall.heavy(FontWeight.ExtraBold, -0.8f),
    headlineLarge = base.headlineLarge.heavy(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.heavy(FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.heavy(FontWeight.Bold),
    titleLarge = base.titleLarge.heavy(FontWeight.Bold, -0.2f),
    titleMedium = base.titleMedium.heavy(FontWeight.SemiBold, 0f),
    titleSmall = base.titleSmall.heavy(FontWeight.SemiBold, 0f),
    labelLarge = base.labelLarge.heavy(FontWeight.Bold, 0.1f),
    labelMedium = base.labelMedium.heavy(FontWeight.SemiBold, 0.2f),
    labelSmall = base.labelSmall.heavy(FontWeight.SemiBold, 0.3f),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
)

val TrameShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** true si l'utilisateur a désactivé les animations (Accessibilité > Supprimer les animations). */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun TrameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkFallback
        else -> LightFallback
    }
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            shapes = TrameShapes,
            typography = TrameTypography,
            content = content,
        )
    }
}
