package com.sabaferoz.apptreminder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1F6F6B), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE8), onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF3F6B8F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E8F7), onSecondaryContainer = Color(0xFF0B2A44),
    tertiary = Color(0xFFB26A00), onTertiary = Color.White,
    background = Color(0xFFE9F3F1), onBackground = Color(0xFF14201F),
    surface = Color.White, onSurface = Color(0xFF14201F),
    surfaceVariant = Color(0xFFD5E4E1), onSurfaceVariant = Color(0xFF3F4948),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6FD3CC), onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF004F4B), onPrimaryContainer = Color(0xFFCDEBE8),
    secondary = Color(0xFF9CC9EE), onSecondary = Color(0xFF0B2A44),
    secondaryContainer = Color(0xFF254A66), onSecondaryContainer = Color(0xFFD6E8F7),
    tertiary = Color(0xFFFFB95C), onTertiary = Color(0xFF452B00),
    background = Color(0xFF0F1716), onBackground = Color(0xFFDDE5E4),
    surface = Color(0xFF1A2322), onSurface = Color(0xFFDDE5E4),
    surfaceVariant = Color(0xFF3A4140), onSurfaceVariant = Color(0xFFBFC9C7),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1F1B), onErrorContainer = Color(0xFFFFDAD6),
)

enum class Tone { NEUTRAL, GOOD, WARN, BAD, INFO }

/** Background / foreground pair for a meaning (green = fine, amber = look at it, red = problem, blue = info). */
data class ToneColors(val bg: Color, val fg: Color)

@Composable
fun toneColors(tone: Tone): ToneColors {
    val dark = isSystemInDarkTheme()
    return when (tone) {
        Tone.GOOD -> if (dark) ToneColors(Color(0xFF1F3D22), Color(0xFFBFE8C3)) else ToneColors(Color(0xFFD9F2DC), Color(0xFF1B5E20))
        Tone.WARN -> if (dark) ToneColors(Color(0xFF4A3A10), Color(0xFFFFD98A)) else ToneColors(Color(0xFFFFEBBF), Color(0xFF7A4B00))
        Tone.BAD -> if (dark) ToneColors(Color(0xFF5C1F1B), Color(0xFFFFB4AB)) else ToneColors(Color(0xFFF9D4D0), Color(0xFF8C1D18))
        Tone.INFO -> if (dark) ToneColors(Color(0xFF15324D), Color(0xFFB5D6F2)) else ToneColors(Color(0xFFD0E6F8), Color(0xFF0B3A66))
        Tone.NEUTRAL -> if (dark) ToneColors(Color(0xFF3A4140), Color(0xFFDDE5E4)) else ToneColors(Color(0xFFE1E8E7), Color(0xFF3F4948))
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
