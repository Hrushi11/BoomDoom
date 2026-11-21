package app.boomdoom.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.boomdoom.core.data.db.DailyAggregateEntity
import app.boomdoom.core.data.repo.TodayStats
import app.boomdoom.core.data.repo.TrackingRepository
import app.boomdoom.core.data.settings.SettingsRepository
import app.boomdoom.core.data.settings.UserSettings
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.CardShape
import app.boomdoom.core.designsystem.Donut
import app.boomdoom.core.designsystem.Illus
import app.boomdoom.core.designsystem.Illustration
import app.boomdoom.core.designsystem.LearnButton
import app.boomdoom.core.designsystem.PaperTheme
import app.boomdoom.core.designsystem.PillShape
import app.boomdoom.core.model.BuiltInLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class InsightsUi(
    val settings: UserSettings = UserSettings(),
    val today: TodayStats = TodayStats(""),
    val week: List<DailyAggregateEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(settings: SettingsRepository, tracking: TrackingRepository) : ViewModel() {
    private val week = tracking.dayKeys.flatMapLatest { key ->
        tracking.observeDays(LocalDate.parse(key).minusDays(6).toString())
    }
    val ui: StateFlow<InsightsUi> = combine(settings.settings, tracking.observeToday(), week) { s, t, w -> InsightsUi(s, t, w) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUi())
}

/** Deterministic summary (Tech Spec §5.8 fallback): only facts we have, no guessed reasons or feelings. */
internal fun summary(t: TodayStats): String = buildString {
    append("You watched ${t.reels} reels today")
    append(if (t.sessions > 0) " across ${t.sessions} ${if (t.sessions == 1) "session" else "sessions"}." else ".")
    t.recognised.firstOrNull()?.let { (label, share) ->
        val name = BuiltInLabel.entries.firstOrNull { it.key == label }?.displayName ?: label
        append(" $name made up ${(share * 100).toInt()}% of recognised content.")
    }
    if (t.longestSessionSec >= 60) {
        append(" Your longest session was ${t.longestSessionSec / 60} minutes")
        t.peakHour?.let { h -> append(", mostly around ${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}") }
        append(".")
    }
    append(" You completed ${t.lessons} ${if (t.lessons == 1) "lesson" else "lessons"}.")
}

private val sliceColors = listOf(Color(0xFFFF7A6B), Color(0xFFB4A8F5), Color(0xFF8EE3D2), Color(0xFFFFD166), Color(0xFF9CC9FF), Color(0xFFD0D0D0))

/** US-24, US-25, US-26 (designs I1, I3, I4). */
@Composable
fun InsightsScreen(onOpenLearn: () -> Unit, vm: InsightsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var week by remember { mutableStateOf(false) }
    val t = ui.today
    PaperTheme {
        Column(Modifier.fillMaxSize().background(BoomColors.Paper).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(PillShape).background(BoomColors.LearnTint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.BarChart, null, tint = BoomColors.Ink)
                }
                Spacer(Modifier.width(10.dp))
                Text("Insights", style = MaterialTheme.typography.headlineLarge)
            }
            if (!t.hasTracking && t.lessons == 0 && ui.week.none { it.reels > 0 }) {
                EmptyInsights(onOpenLearn)
                return@Column
            }
            Segmented(week) { week = it }
            if (!week) Today(t, ui.settings) else Week(ui.week, ui.settings.budget)
        }
    }
}

@Composable
private fun Segmented(week: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(PillShape).border(1.dp, Color(0xFF9A958B), PillShape).padding(4.dp)) {
        listOf(false to "Today", true to "7 days").forEach { (w, label) ->
            Box(Modifier.weight(1f).clip(PillShape).background(if (w == week) BoomColors.LearnDeep else Color.Transparent)
                .clickable(role = Role.Tab) { onChange(w) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = if (w == week) Color.White else BoomColors.Ink)
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier) {
    Column(modifier.clip(CardShape).background(BoomColors.PaperCard).border(1.dp, BoomColors.PaperBorder, CardShape).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = tint)
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
    }
}

@Composable
private fun Today(t: TodayStats, s: UserSettings) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(Icons.Filled.Movie, "${t.reels}", "reels", BoomColors.Ink, Modifier.weight(1f))
        Tile(Icons.Filled.HourglassBottom, if (t.reels > s.budget) "+${t.reels - s.budget}" else "${s.budget - t.reels}",
            if (t.reels > s.budget) "over" else "left", Color(0xFFC77700), Modifier.weight(1f))
        Tile(Icons.Filled.Flag, "${t.flagged}", "flagged", Color(0xFFE5484D), Modifier.weight(1f))
        Tile(Icons.Filled.Timer, "${t.longestSessionSec / 60} min", "longest", BoomColors.LearnDeep, Modifier.weight(1f))
    }
    Card {
        val shares = t.recognised
        if (shares.isEmpty()) {
            Text("What you watched", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Content categories need screen understanding, which is coming soon. Until then BoomDoom counts reels and sessions only — it never guesses.",
                style = MaterialTheme.typography.bodyMedium, color = BoomColors.InkSecondary)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Donut(shares.take(6).mapIndexed { i, (_, v) -> v to sliceColors[i] }, Modifier.size(150.dp), stroke = 30.dp,
                    track = Color(0xFFEDE7DB)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${t.reels}", style = MaterialTheme.typography.headlineMedium); Text("reels", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shares.take(6).forEachIndexed { i, (label, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(PillShape).background(sliceColors[i]))
                            Spacer(Modifier.width(6.dp))
                            Text(BuiltInLabel.entries.firstOrNull { it.key == label }?.displayName ?: label,
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp))
                            Text("${(v * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
    Card {
        Row {
            Box(Modifier.size(52.dp).clip(PillShape).background(BoomColors.LearnTint), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, tint = BoomColors.Warning)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Your daily summary", style = MaterialTheme.typography.titleLarge)
                Text(summary(t), style = MaterialTheme.typography.bodyMedium, color = BoomColors.InkSecondary)
            }
        }
    }
}

@Composable
private fun Week(days: List<DailyAggregateEntity>, budget: Int) {
    val byDay = days.associateBy { it.dayKey }
    val end = days.maxOfOrNull { it.dayKey }?.let { LocalDate.parse(it) } ?: LocalDate.now()
    val keys = (6 downTo 0).map { end.minusDays(it.toLong()) }
    val max = (keys.maxOfOrNull { byDay[it.toString()]?.reels ?: 0 } ?: 0).coerceAtLeast(budget).coerceAtLeast(1)
    Card {
        Text("Reels per day", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            keys.forEach { d ->
                val n = byDay[d.toString()]?.reels ?: 0
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom) {
                    Text("$n", style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.fillMaxWidth().fillMaxHeight(n.toFloat() / max * 0.85f + 0.01f).clip(RoundedCornerShape(6.dp))
                        .background(if (n > budget) Color(0xFFFF7A6B) else BoomColors.Learn))
                    Text(d.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val total = keys.sumOf { byDay[it.toString()]?.reels ?: 0 }
        val lessons = keys.sumOf { byDay[it.toString()]?.lessons ?: 0 }
        Text("$total reels and $lessons lessons in the last 7 days.", style = MaterialTheme.typography.bodyMedium, color = BoomColors.InkSecondary)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(BoomColors.PaperCard).border(1.dp, BoomColors.PaperBorder, CardShape)
        .padding(16.dp)) { content() }
}

@Composable
private fun EmptyInsights(onOpenLearn: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Illustration(Illus.insightsEmpty, Modifier.fillMaxWidth().height(320.dp))
        Spacer(Modifier.height(16.dp))
        Text("Your insights will appear after your first session.", style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        LearnButton("Try a lesson meanwhile", onOpenLearn)
    }
}
