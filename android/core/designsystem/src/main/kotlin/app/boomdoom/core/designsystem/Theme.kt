package app.boomdoom.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette from prompts_for_design.md (style block). */
object BoomColors {
    val Background = Color(0xFF0E0F13)
    val Card = Color(0xFF171922)
    val CardRaised = Color(0xFF1F2230)
    val Border = Color(0xFF2A2D3A)
    val Boom = Color(0xFFFF6B3D)       // primary accent, coral-orange
    val Learn = Color(0xFF2EC4B6)      // learning accent, teal
    val Budget = Color(0xFFFF3B3B)     // budget/progress red line
    val Warning = Color(0xFFFFB020)
    val Success = Color(0xFF34C759)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A1AA)
    val Paper = Color(0xFFFAF8F3)      // stickman lesson cards

    // Light variant
    val LightBackground = Color(0xFFF7F7F5)
    val LightCard = Color(0xFFFFFFFF)
    val LightBorder = Color(0xFFE6E6E3)
    val LightTextPrimary = Color(0xFF111216)
    val LightTextSecondary = Color(0xFF5B5F6B)
}

private val Dark = darkColorScheme(
    primary = BoomColors.Boom, onPrimary = BoomColors.Background,
    secondary = BoomColors.Learn, onSecondary = BoomColors.Background,
    error = BoomColors.Budget,
    background = BoomColors.Background, onBackground = BoomColors.TextPrimary,
    surface = BoomColors.Card, onSurface = BoomColors.TextPrimary,
    surfaceVariant = BoomColors.CardRaised, onSurfaceVariant = BoomColors.TextSecondary,
    outline = BoomColors.Border,
)

private val Light = lightColorScheme(
    primary = BoomColors.Boom, onPrimary = BoomColors.LightTextPrimary,
    secondary = BoomColors.Learn,
    error = BoomColors.Budget,
    background = BoomColors.LightBackground, onBackground = BoomColors.LightTextPrimary,
    surface = BoomColors.LightCard, onSurface = BoomColors.LightTextPrimary,
    onSurfaceVariant = BoomColors.LightTextSecondary,
    outline = BoomColors.LightBorder,
)

@Composable
fun BoomDoomTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
