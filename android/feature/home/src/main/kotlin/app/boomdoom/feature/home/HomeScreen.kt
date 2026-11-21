package app.boomdoom.feature.home

import app.boomdoom.core.data.repo.TodayStats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.boomdoom.core.designsystem.BoomCard
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.BudgetProgress
import app.boomdoom.core.designsystem.Donut
import app.boomdoom.core.designsystem.Illus
import app.boomdoom.core.designsystem.Illustration
import app.boomdoom.core.designsystem.LearnButton
import app.boomdoom.core.designsystem.NightTheme
import app.boomdoom.core.designsystem.PaperPanel
import app.boomdoom.core.designsystem.PillShape
import app.boomdoom.core.designsystem.Secondary
import app.boomdoom.core.designsystem.StatTile
import app.boomdoom.core.designsystem.StatusPill
import app.boomdoom.core.designsystem.Tag
import app.boomdoom.core.designsystem.TextLink
import app.boomdoom.core.model.BuiltInLabel
import app.boomdoom.core.model.MonitoringStatus
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility

/** US-09, US-10 (designs H1–H4). */
@Composable
fun HomeScreen(onOpenLesson: (String) -> Unit, onOpenLearn: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }
    val s = ui.settings
    val t = ui.today

    NightTheme {
        Column(
            Modifier.fillMaxSize().background(BoomColors.Background).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Illustration(Illus.logo, Modifier.size(32.dp)); Spacer(Modifier.width(8.dp))
                Text("BoomDoom", style = MaterialTheme.typography.titleMedium)
            }
            Greeting(s.displayName)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(ui.status)
                Spacer(Modifier.width(10.dp))
                TextLink(if (s.monitoringPaused) "Resume" else "Pause", { vm.setPaused(!s.monitoringPaused) },
                    color = BoomColors.TextPrimary, modifier = Modifier.padding(0.dp))
            }
            if (t.demo || s.demoMode) DemoBanner()
            SetupBanner(ui.status) { ctx.startActivity(vm.fixIntent(ui.status)) }
            val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
                r.data?.let { if (r.resultCode == android.app.Activity.RESULT_OK) vm.startCapture(r.resultCode, it) }
            }
            // Banner and budget card share one child, and the banner animates its height: toggling Pause/Resume
            // used to drop or insert ~120 px in a single frame, so everything below jumped (looked like flicker).
            Column {
                AnimatedVisibility(
                    visible = ui.status !is MonitoringStatus.SetupRequired && !s.monitoringPaused && ui.modelsReady && !ui.captureActive,
                    enter = fadeIn(tween(220, easing = EaseOutExpoLike)) + expandVertically(tween(300, easing = EaseOutExpoLike)),
                    exit = fadeOut(tween(150, easing = EaseOutExpoLike)) + shrinkVertically(tween(220, easing = EaseOutExpoLike)),
                ) {
                    Box(Modifier.padding(bottom = 14.dp)) { CaptureBanner { consent.launch(vm.captureIntent()) } }
                }
                BudgetCard(t.reels, s.budget, ui.next) { ui.next?.let { onOpenLesson(it.id) } ?: onOpenLearn() }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${t.flagged}", "flagged", { Icon(Icons.Outlined.Flag, null, Modifier.size(18.dp)) },
                    Modifier.weight(1f))
                StatTile("${t.lessons}", "lessons", { Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp)) },
                    Modifier.weight(1f))
                StatTile("${t.learningSec / 60} min", "learning",
                    { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) }, Modifier.weight(1.15f))
            }

            if (!t.hasTracking) EmptyInsight()
            ui.next?.let { LessonCard(it) { onOpenLesson(it.id) } } ?: NoLessonCard(onOpenLearn)
            if (t.hasTracking) InsightCard(t)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** "Good evening, Hrushi" + today's date (the user asked for a personal greeting). Name is optional. */
@Composable
private fun Greeting(name: String) {
    val now = remember { LocalDateTime.now() }
    val part = when (now.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Hey night owl" }
    Column {
        Text(
            if (name.isBlank()) part else "$part,",
            style = MaterialTheme.typography.titleMedium, color = BoomColors.TextSecondary,
        )
        if (name.isNotBlank()) {
            Text(name.trim(), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.bodyMedium,
            color = BoomColors.TextSecondary)
    }
}

@Composable
private fun StatusChip(status: MonitoringStatus) = when (status) {
    MonitoringStatus.Active -> StatusPill("Monitoring active", BoomColors.Success)
    MonitoringStatus.Paused -> StatusPill("Monitoring paused", BoomColors.TextSecondary)
    MonitoringStatus.NeedsReconsent -> StatusPill("Needs your OK", BoomColors.Warning)
    is MonitoringStatus.Limited -> StatusPill("Limited monitoring", BoomColors.Warning)
    is MonitoringStatus.SetupRequired -> StatusPill("Setup required", BoomColors.Budget)
}

@Composable
private fun SetupBanner(status: MonitoringStatus, onFix: () -> Unit) {
    val (title, body) = when (status) {
        is MonitoringStatus.SetupRequired -> "Reel counting is off" to "Turn on Accessibility so BoomDoom can count reels."
        is MonitoringStatus.Limited -> "Budget bar is off" to "Allow display over apps to see your bar and check-ins."
        else -> return
    }
    BoomCard(Modifier.fillMaxWidth(), color = Color(0xFF221B10), border = BoomColors.Warning) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(BoomColors.Warning), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.LinkOff, null, tint = BoomColors.Ink)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Secondary(body, align = TextAlign.Start)
            }
            Spacer(Modifier.width(8.dp))
            Text("Fix setup", style = MaterialTheme.typography.labelMedium, color = BoomColors.Background,
                modifier = Modifier.clip(PillShape).background(BoomColors.Boom)
                    .clickable(onClick = onFix).padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

/** US-32: content understanding is off (never enabled, or Android needs consent again after a restart). */
/** One easing curve for UI motion (unslop tokens: cubic-bezier(0.22, 1, 0.36, 1)). */
private val EaseOutExpoLike = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
private fun CaptureBanner(onResume: () -> Unit) {
    BoomCard(Modifier.fillMaxWidth(), color = BoomColors.CardRaised, border = BoomColors.Border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Screen understanding is off", style = MaterialTheme.typography.titleSmall)
                Secondary("Categories and flags won't update until it's on. Everything stays on this phone.", align = TextAlign.Start)
            }
            Spacer(Modifier.width(8.dp))
            Text("Turn on", style = MaterialTheme.typography.labelMedium, color = BoomColors.Background,
                modifier = Modifier.clip(PillShape).background(BoomColors.Learn).clickable(onClick = onResume)
                    .padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun BudgetCard(reels: Int, budget: Int, next: NextLesson?, onLesson: () -> Unit) {
    val over = reels > budget
    BoomCard(Modifier.fillMaxWidth(), padding = 18.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (over) BoomColors.Budget else BoomColors.Boom)) { append("$reels") }
                    withStyle(SpanStyle(color = BoomColors.TextPrimary, fontSize = MaterialTheme.typography.displayMedium.fontSize)) {
                        append(" / $budget")
                    }
                },
                style = MaterialTheme.typography.displayLarge,
            )
            if (over) {
                Spacer(Modifier.width(10.dp))
                Tag("+${reels - budget} over", color = Color(0xFFFF7A6B), textColor = BoomColors.Ink)
            }
        }
        Text("reels today", style = MaterialTheme.typography.titleMedium, color = BoomColors.TextSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        BudgetProgress(reels, budget)
        if (over) {
            Spacer(Modifier.height(10.dp))
            Secondary("Over today's budget — want a quick learning break?", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            LearnButton(if (next != null) "Start a lesson" else "Open Learn", onLesson, Modifier.fillMaxWidth(),
                leading = { Icon(Icons.Filled.PlayArrow, null) })
        }
    }
}

@Composable
private fun LessonCard(next: NextLesson, onStart: () -> Unit) {
    PaperPanel(Modifier.fillMaxWidth().height(180.dp)) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Illustration(Illus.homeLesson, Modifier.weight(0.9f).fillMaxSize())
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1.2f)) {
                Tag("${next.topic} · ${next.durationS} sec")
                Spacer(Modifier.height(8.dp))
                Text(next.concept, style = MaterialTheme.typography.titleLarge, color = BoomColors.Ink, maxLines = 2)
                Spacer(Modifier.height(10.dp))
                LearnButton("Start learning", onStart, Modifier.fillMaxWidth(), height = 46.dp,
                    leading = { Icon(Icons.Filled.PlayArrow, null) })
            }
        }
    }
}

@Composable
private fun NoLessonCard(onOpenLearn: () -> Unit) {
    PaperPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Your lessons are on the way", style = MaterialTheme.typography.titleLarge, color = BoomColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text("Pick topics in Learn and we'll prepare short stickman lessons for you.", color = BoomColors.InkSecondary,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            LearnButton("Open Learn", onOpenLearn)
        }
    }
}

@Composable
private fun EmptyInsight() {
    PaperPanel(Modifier.fillMaxWidth().height(150.dp)) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Illustration(Illus.homeEmpty, Modifier.weight(1f).fillMaxSize())
            Spacer(Modifier.width(10.dp))
            Text("Your insights will appear after your first scroll session.", style = MaterialTheme.typography.titleMedium,
                color = BoomColors.Ink, modifier = Modifier.weight(1.2f))
        }
    }
}

private val sliceColors = listOf(Color(0xFFFF7A6B), Color(0xFFB4A8F5), Color(0xFF8EE3D2), BoomColors.Warning, Color(0xFF9CC9FF))

@Composable
private fun InsightCard(t: TodayStats) {
    BoomCard(Modifier.fillMaxWidth()) {
        Text("Today's insight", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        val top = t.breakdown.firstOrNull { it.first != TodayStats.OTHER && it.first != TodayStats.UNANALYSED }
        if (!t.enoughRecognised || top == null) {
            // Never fabricate categories (US-15/US-24): say what we actually know.
            Text("${t.reels} reels across ${t.sessions} ${if (t.sessions == 1) "session" else "sessions"} today.",
                style = MaterialTheme.typography.titleLarge)
            Secondary(
                when {
                    t.recognisedCount > 0 -> "Recognised ${t.recognisedCount} of ${t.reels} so far: too few to show a pattern yet."
                    t.unanalysedCount < t.reels -> "Nothing recognised yet: the reels so far didn't match a category."
                    else -> "Content categories appear once screen understanding is on."
                },
                align = TextAlign.Start,
            )
        } else {
            // Share of ALL today's reels, so a few recognised reels never read as "100% of your feed".
            val (label, share) = top
            val name = BuiltInLabel.entries.firstOrNull { it.key == label }?.displayName ?: label.replaceFirstChar { it.uppercase() }
            val pct = (share * 100).toInt().coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Donut(listOf(share to sliceColors[0]), Modifier.size(96.dp), stroke = 16.dp) {
                    Text("$pct%", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(16.dp))
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = BoomColors.Boom)) { append(name) }
                    append(" was $pct% of your feed.")
                }, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun DemoBanner() {
    Text("Demo data", style = MaterialTheme.typography.labelMedium, color = BoomColors.Ink,
        modifier = Modifier.fillMaxWidth().clip(PillShape).background(BoomColors.Warning).border(1.dp, BoomColors.Warning, PillShape)
            .padding(8.dp), textAlign = TextAlign.Center)
}

