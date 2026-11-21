package app.boomdoom.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.Illus
import app.boomdoom.core.designsystem.Illustration
import app.boomdoom.core.designsystem.NightTheme
import app.boomdoom.core.designsystem.PaperPanel
import app.boomdoom.core.designsystem.PillShape
import app.boomdoom.core.designsystem.PrimaryButton
import app.boomdoom.core.designsystem.SecondaryButton
import app.boomdoom.core.designsystem.StackedBar
import app.boomdoom.core.designsystem.TextLink

enum class BarState { NORMAL, NEAR, OVER }

fun barState(reels: Int, budget: Int): BarState = when {
    reels > budget -> BarState.OVER
    reels >= budget * 0.8 -> BarState.NEAR
    else -> BarState.NORMAL
}

/** The red line (US-12, designs v1/v2): 4 dp bar under the status bar + a small count chip. */
@Composable
fun BudgetBar(reels: Int, budget: Int) {
    val state = barState(reels, budget)
    val fraction by animateFloatAsState((reels.toFloat() / budget.coerceAtLeast(1)).coerceIn(0f, 1f), tween(600), label = "bar")
    val fill = when (state) {
        BarState.NORMAL -> Brush.horizontalGradient(listOf(BoomColors.Budget, BoomColors.Boom))
        BarState.NEAR -> Brush.horizontalGradient(listOf(BoomColors.Boom, BoomColors.Warning))
        BarState.OVER -> Brush.horizontalGradient(listOf(BoomColors.Budget, BoomColors.Budget))
    }
    val label = if (state == BarState.OVER) "+${reels - budget}" else "$reels / $budget"
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Reel budget: $reels of $budget" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(4.dp).clip(PillShape).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(PillShape).background(fill))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = if (state == BarState.OVER) Color.White else BoomColors.TextPrimary,
            modifier = Modifier.clip(PillShape)
                .background(if (state == BarState.OVER) BoomColors.Budget else Color(0xCC2A2D3A))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Shared scrim + centred night card. Tapping the scrim does nothing: the user picks an action. */
@Composable
private fun CardScrim(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(28.dp)).background(BoomColors.Card)
                .border(1.dp, BoomColors.Border, RoundedCornerShape(28.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

private val BreakdownColors = listOf(Color(0xFFFF7A6B), Color(0xFF8EE3D2), Color(0xFFD6CCFF), BoomColors.Warning, Color(0xFF9CC9FF))

data class CheckInUi(
    val sessionReels: Int,
    /** Category shares for this session; empty when classification isn't available (never fabricated). */
    val breakdown: List<Pair<String, Float>>,
    val lessonTopic: String?,
)

/** Check-in card (US-15, design v5). */
@Composable
fun CheckInCard(ui: CheckInUi, onSwitch: () -> Unit, onContinue: () -> Unit, onDone: () -> Unit, onSnooze: () -> Unit) {
    NightTheme {
        CardScrim {
            Text("${ui.sessionReels} reels already 👀", style = MaterialTheme.typography.headlineSmall, color = BoomColors.TextPrimary)
            if (ui.breakdown.isNotEmpty()) {
                Text("Here's what you've been watching.", style = MaterialTheme.typography.bodyMedium, color = BoomColors.Lavender)
                Spacer(Modifier.height(14.dp))
                val top = ui.breakdown.take(3)
                StackedBar(top.mapIndexed { i, (_, v) -> v to BreakdownColors[i] })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    top.forEachIndexed { i, (label, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(BreakdownColors[i]))
                            Spacer(Modifier.width(5.dp))
                            Text("$label ${(v * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = BoomColors.TextPrimary)
                        }
                    }
                }
            } else {
                Text("Time for a quick pause?", style = MaterialTheme.typography.bodyMedium, color = BoomColors.Lavender)
            }
            Spacer(Modifier.height(16.dp))
            if (ui.lessonTopic != null) {
                PaperPanel(Modifier.fillMaxWidth().height(108.dp)) {
                    Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Illustration(Illus.checkIn, Modifier.width(130.dp).fillMaxHeight())
                        Spacer(Modifier.width(10.dp))
                        Text("Swap 60 seconds for a ${ui.lessonTopic} lesson?", style = MaterialTheme.typography.titleSmall,
                            color = BoomColors.Ink)
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Switch to lesson", onSwitch)
                Spacer(Modifier.height(10.dp))
            }
            SecondaryButton("Continue scrolling", onContinue)
            TextLink("Done for now", onDone, underline = false, color = BoomColors.TextPrimary)
            TextLink("Snooze for this session", onSnooze)
        }
    }
}

/** Budget reached card (US-16). */
@Composable
fun BudgetCard(reels: Int, budget: Int, lessonTopic: String?, onLesson: () -> Unit, onKeep: () -> Unit, onFinish: () -> Unit) {
    NightTheme {
        CardScrim {
            Text("🎯", style = MaterialTheme.typography.displayMedium)
            Text("You've reached today's scroll budget.", style = MaterialTheme.typography.headlineSmall,
                color = BoomColors.TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("$reels of $budget reels watched today. BoomDoom won't lock anything — it's your call.",
                style = MaterialTheme.typography.bodyMedium, color = BoomColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            if (lessonTopic != null) {
                PrimaryButton("Start a $lessonTopic lesson", onLesson)
                Spacer(Modifier.height(10.dp))
            }
            SecondaryButton("Keep scrolling", onKeep)
            TextLink("Finish session", onFinish, underline = false, color = BoomColors.TextPrimary)
        }
    }
}

/** Small flag chip (US-14, design v4). Shown only when confidence ≥ threshold. */
@Composable
fun FlagTag(label: String) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, top = 22.dp)) {
        Text("⚑ Flagged: $label", style = MaterialTheme.typography.labelMedium, color = BoomColors.Ink,
            modifier = Modifier.clip(PillShape).background(BoomColors.WarningTint).border(1.dp, BoomColors.Warning, PillShape)
                .padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

