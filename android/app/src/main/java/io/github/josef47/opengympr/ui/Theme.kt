package io.github.josef47.opengympr.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import io.github.josef47.opengympr.data.ThemeMode

// Same palette as the web prototype: near-black with a chalk-lime accent for PRs and the main action.
private val Dark = darkColorScheme(
    background = Color(0xFF0E0F12), surface = Color(0xFF17191E), surfaceVariant = Color(0xFF20232A),
    surfaceContainer = Color(0xFF17191E), surfaceContainerHigh = Color(0xFF20232A),
    onBackground = Color(0xFFF2F3F5), onSurface = Color(0xFFF2F3F5), onSurfaceVariant = Color(0xFF8B919E),
    primary = Color(0xFFC6FF3D), onPrimary = Color(0xFF11140A), secondary = Color(0xFFFFC43D),
    outline = Color(0xFF2B2F38), outlineVariant = Color(0xFF2B2F38), error = Color(0xFFFF5D5D),
    inverseSurface = Color(0xFFF2F3F5), inverseOnSurface = Color(0xFF0E0F12),
)
private val Light = lightColorScheme(
    background = Color(0xFFF4F4F1), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFECECE8),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFECECE8),
    onBackground = Color(0xFF15171B), onSurface = Color(0xFF15171B), onSurfaceVariant = Color(0xFF6A6F7A),
    primary = Color(0xFF4F7D00), onPrimary = Color(0xFFFFFFFF), secondary = Color(0xFFB07800),
    outline = Color(0xFFDCDCD6), outlineVariant = Color(0xFFDCDCD6), error = Color(0xFFD32F2F),
)

@Composable
fun isDark(mode: ThemeMode) = when (mode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun OpenGymTheme(mode: ThemeMode, content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isDark(mode)) Dark else Light, content = content)

/** Stroke icons on a 24-unit grid, drawn from SVG path data (no icon library needed). */
private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(PathParser().parsePathString(it).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()

object Ico {
    val Plus = strokeIcon("plus", "M12 5v14M5 12h14")
    val Chart = strokeIcon("chart", "M4 19l5-6 4 3 7-9")
    val Gear = strokeIcon("gear", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6", "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1")
    val Back = strokeIcon("back", "M15 5l-7 7 7 7")
    val Close = strokeIcon("close", "M7 7l10 10M17 7L7 17")
    val Down = strokeIcon("down", "M6 9l6 6 6-6")
}
