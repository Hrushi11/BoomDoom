package app.boomdoom.feature.learn

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.Illus
import app.boomdoom.core.designsystem.Illustration
import app.boomdoom.core.designsystem.LearnButton
import app.boomdoom.core.designsystem.PaperTheme
import app.boomdoom.core.designsystem.PillShape
import app.boomdoom.core.designsystem.SecondaryButton
import app.boomdoom.core.designsystem.Tag
import app.boomdoom.core.designsystem.TextLink
import app.boomdoom.core.player.LessonQuestion
import app.boomdoom.core.player.LessonReportForm
import app.boomdoom.core.player.LessonVideo
import app.boomdoom.core.player.SegmentedProgress
import app.boomdoom.core.player.rememberLessonPlayer
import kotlinx.coroutines.delay

/** US-18 → US-21 (designs L2–L5, L9). */
@AndroidOptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onExit: () -> Unit,
    onAnotherLesson: (String) -> Unit,
    vm: PlayerViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val lesson = ui.lesson

    PaperTheme {
        Box(Modifier.fillMaxSize().background(BoomColors.Paper)) {
            when (val phase = ui.phase) {
                PlayerPhase.Loading -> Unit
                PlayerPhase.Missing -> Missing(onExit)
                PlayerPhase.Recap -> Recap(
                    concept = lesson?.manifest?.concept.orEmpty(), recap = lesson?.manifest?.recap.orEmpty(),
                    hasNext = ui.nextLessonId != null,
                    onAnother = { ui.nextLessonId?.let(onAnotherLesson) },
                    onBackToReels = {
                        ctx.packageManager.getLaunchIntentForPackage("com.instagram.android")?.let { ctx.startActivity(it) }
                        onExit()
                    },
                    onDone = onExit,
                )
                else -> if (lesson != null) {
                    LessonPlayback(vm, ui, phase, onExit)
                }
            }
        }
    }
}

@AndroidOptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonPlayback(vm: PlayerViewModel, ui: PlayerUi, phase: PlayerPhase, onExit: () -> Unit) {
    val lesson = ui.lesson!!
    val ctx = LocalContext.current
    val m = lesson.manifest
    var muted by remember { mutableStateOf(false) }
    var progressS by remember { mutableFloatStateOf(0f) }
    var reporting by remember { mutableStateOf(false) }

    val player = rememberLessonPlayer(lesson.source!!, m, vm::onInteractionPoint, vm::onEnded)
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    LaunchedEffect(phase) { if (phase is PlayerPhase.Playing && !reporting) player.play() }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(Unit) { while (true) { progressS = player.currentPosition / 1000f; delay(100) } }
    LaunchedEffect(ui.reported) {
        if (ui.reported) {
            Toast.makeText(ctx, "Thanks, we'll review this lesson.", Toast.LENGTH_SHORT).show()
            vm.clearReported()
        }
    }
    BackHandler(onBack = onExit)

    val asking = phase as? PlayerPhase.Asking
    Box(Modifier.fillMaxSize()) {
        LessonVideo(
            player,
            shutterColor = android.graphics.Color.rgb(250, 246, 238),
            modifier = Modifier.fillMaxSize().then(if (asking != null) Modifier.blur(8.dp) else Modifier)
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {
                    if (asking == null) { if (player.isPlaying) player.pause() else player.play() }
                },
        )

        // top bar: segmented progress, topic, close
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentedProgress(m.segments.map { it.endS.toFloat() }, progressS, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                RoundIcon(Icons.Filled.Close, "Close lesson", onExit)
            }
            Spacer(Modifier.height(8.dp))
            Tag(lesson.topicName)
        }
        // side controls
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIcon(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                if (muted) "Sound on" else "Sound off") { muted = !muted }
            if (!lesson.row.bundled) RoundIcon(Icons.Filled.Flag, "Report a mistake") { player.pause(); reporting = true }
        }
        m.disclaimer?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp))
        }

        if (asking != null) {
            LessonQuestion(asking.interaction, asking.answer, vm::answer, vm::resume)
        }
    }

    if (reporting) {
        ReportSheet(
            onDismiss = { reporting = false; if (asking == null) player.play() },
            onSend = { reason, note -> vm.report(reason, note, player.currentPosition / 1000.0); reporting = false; player.play() },
        )
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xCCF2EDE3), contentColor = BoomColors.Ink)) {
        Icon(icon, description)
    }
}

/** Recap card (L5). */
@Composable
private fun Recap(concept: String, recap: String, hasNext: Boolean, onAnother: () -> Unit, onBackToReels: () -> Unit, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFFFFFBF4))
                .border(1.dp, BoomColors.PaperBorder, RoundedCornerShape(28.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.clip(PillShape).background(BoomColors.LearnTint).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = BoomColors.LearnDeep, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Saved to Learned", style = MaterialTheme.typography.labelMedium, color = BoomColors.Ink)
            }
            Illustration(Illus.recap, Modifier.fillMaxWidth().aspectRatio(1.4f).padding(vertical = 12.dp))
            Text(concept, style = MaterialTheme.typography.headlineMedium, color = BoomColors.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(recap, style = MaterialTheme.typography.bodyLarge, color = BoomColors.InkSecondary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        if (hasNext) {
            LearnButton("Another lesson", onAnother, Modifier.fillMaxWidth(), height = 56.dp,
                leading = null)
            Spacer(Modifier.height(10.dp))
        }
        SecondaryButton("Back to my reels", onBackToReels)
        TextLink("Done for now", onDone, underline = false)
    }
}

@Composable
private fun Missing(onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Illustration(Illus.insightsEmpty, Modifier.size(220.dp))
        Text("This lesson isn't available right now.", style = MaterialTheme.typography.titleLarge, color = BoomColors.Ink,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        LearnButton("Back", onExit)
    }
}

/** Report a mistake (L9). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(onDismiss: () -> Unit, onSend: (String, String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BoomColors.Paper) {
        LessonReportForm(onSend)
    }
}
