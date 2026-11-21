package app.boomdoom.core.designsystem

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Palette from prompts_for_design.md and the mockups in /designs.
 * Two surfaces: Night (onboarding, Home, overlays) and Paper (Learn, player, Insights, You).
 */
object BoomColors {
    // accents (shared)
    val Boom = Color(0xFFFF6B3D)       // coral-orange primary
    val Coral = Color(0xFFFF6F61)      // softer coral used for paper-screen CTAs
    val Learn = Color(0xFF2EC4B6)      // learning teal
    val LearnDeep = Color(0xFF0E8A78)  // teal buttons on paper
    val LearnTint = Color(0xFFCDEFE8)
    val Budget = Color(0xFFFF3B3B)     // the red line
    val Warning = Color(0xFFFFB020)
    val WarningTint = Color(0xFFFFE3A8)
    val Success = Color(0xFF34C759)
    val Lavender = Color(0xFFC9C2F5)

    // night
    val Background = Color(0xFF0E0F13)
    val Card = Color(0xFF171922)
    val CardRaised = Color(0xFF1F2230)
    val Border = Color(0xFF2A2D3A)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A1AA)

    // paper
    val Paper = Color(0xFFFAF6EE)
    val PaperCard = Color(0xFFFFFBF4)
    val PaperBorder = Color(0xFFE9E2D4)
    val Ink = Color(0xFF14151A)
    val InkSecondary = Color(0xFF5B5F6B)
}

@OptIn(ExperimentalTextApi::class)
private val Jakarta = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.plus_jakarta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private fun style(size: Int, weight: Int, line: Int = (size * 1.25).toInt()) =
    TextStyle(fontFamily = Jakarta, fontSize = size.sp, fontWeight = FontWeight(weight), lineHeight = line.sp)

val BoomTypography = Typography(
    displayLarge = style(64, 600, 68),      // big counters ("124 / 150")
    displayMedium = style(44, 600, 48),
    headlineLarge = style(34, 600, 40),     // screen titles
    headlineMedium = style(28, 600, 34),
    headlineSmall = style(22, 600, 28),
    titleLarge = style(20, 600),
    titleMedium = style(17, 600),
    titleSmall = style(15, 600),
    bodyLarge = style(16, 400, 22),
    bodyMedium = style(14, 400, 20),
    bodySmall = style(12, 400, 16),
    labelLarge = style(17, 600),            // buttons
    labelMedium = style(14, 500),
    labelSmall = style(12, 500),
)

/** Extra tokens Material doesn't model. */
@Immutable
data class BoomTokens(
    val card: Color,
    val cardRaised: Color,
    val border: Color,
    val textSecondary: Color,
    val primaryCta: Color,
    val onPrimaryCta: Color,
    val isPaper: Boolean,
)

val LocalBoomTokens = staticCompositionLocalOf {
    BoomTokens(BoomColors.Card, BoomColors.CardRaised, BoomColors.Border, BoomColors.TextSecondary,
        BoomColors.Boom, BoomColors.Background, isPaper = false)
}

private val NightScheme = darkColorScheme(
    primary = BoomColors.Boom, onPrimary = BoomColors.Background,
    secondary = BoomColors.Learn, onSecondary = BoomColors.Background,
    tertiary = BoomColors.Warning,
    error = BoomColors.Budget,
    background = BoomColors.Background, onBackground = BoomColors.TextPrimary,
    surface = BoomColors.Card, onSurface = BoomColors.TextPrimary,
    surfaceVariant = BoomColors.CardRaised, onSurfaceVariant = BoomColors.TextSecondary,
    outline = BoomColors.Border,
)

private val PaperScheme = lightColorScheme(
    primary = BoomColors.LearnDeep, onPrimary = Color.White,
    secondary = BoomColors.Coral, onSecondary = BoomColors.Ink,
    tertiary = BoomColors.Warning,
    error = Color(0xFFD32F2F),
    background = BoomColors.Paper, onBackground = BoomColors.Ink,
    surface = BoomColors.PaperCard, onSurface = BoomColors.Ink,
    surfaceVariant = BoomColors.PaperCard, onSurfaceVariant = BoomColors.InkSecondary,
    outline = BoomColors.PaperBorder,
)

/** Dark "night" surface: onboarding, Home, overlays. */
@Composable
fun NightTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBoomTokens provides BoomTokens(BoomColors.Card, BoomColors.CardRaised, BoomColors.Border,
            BoomColors.TextSecondary, BoomColors.Boom, BoomColors.Background, isPaper = false),
    ) {
        MaterialTheme(colorScheme = NightScheme, typography = BoomTypography) {
            // Text outside a Surface uses LocalContentColor: make it readable on this surface.
            CompositionLocalProvider(LocalContentColor provides BoomColors.TextPrimary, content = content)
        }
    }
}

/** Cream "paper" surface: Learn, lesson player, Insights, You. */
@Composable
fun PaperTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBoomTokens provides BoomTokens(BoomColors.PaperCard, Color.White, BoomColors.PaperBorder,
            BoomColors.InkSecondary, BoomColors.Coral, BoomColors.Ink, isPaper = true),
    ) {
        MaterialTheme(colorScheme = PaperScheme, typography = BoomTypography) {
            CompositionLocalProvider(LocalContentColor provides BoomColors.Ink, content = content)
        }
    }
}

/** Kept for callers that only need "the app theme". */
@Composable
fun BoomDoomTheme(content: @Composable () -> Unit) = NightTheme(content)
