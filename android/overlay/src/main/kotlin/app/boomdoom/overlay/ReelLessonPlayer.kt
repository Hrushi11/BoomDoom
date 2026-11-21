package app.boomdoom.overlay

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.boomdoom.core.data.repo.LearningRepository
import app.boomdoom.core.data.repo.PlayableLesson
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.NightTheme
import app.boomdoom.core.designsystem.PaperTheme
import app.boomdoom.core.designsystem.PrimaryButton
import app.boomdoom.core.designsystem.SecondaryButton
import app.boomdoom.core.model.Interaction
import app.boomdoom.core.player.LessonQuestion
import app.boomdoom.core.player.LessonReportForm
import app.boomdoom.core.player.LessonVideo
import app.boomdoom.core.player.SegmentedProgress
import app.boomdoom.core.player.rememberLessonPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ReelLessonPlayer(
    initialLesson: PlayableLesson,
    learning: LearningRepository,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    var lesson by remember { mutableStateOf(initialLesson) }
    var attempt by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    val focused = LocalWindowInfo.current.isWindowFocused
    val audioReady = ReelAudioService.audioFocusAvailable()
    val hasAudioFocus = rememberReelAudioFocus(audioReady && focused)

    LaunchedEffect(lesson) { scope.launch { learning.started(lesson) } }
    NightTheme {
        key(lesson.row.lessonId, attempt) {
            ReelPlayback(
                lesson, learning, scope, focused, hasAudioFocus, muted,
                onToggleSound = { muted = !muted },
                onNext = { lesson = it },
                onRetry = { attempt++ },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun ReelPlayback(
    lesson: PlayableLesson,
    learning: LearningRepository,
    scope: CoroutineScope,
    focused: Boolean,
    hasAudioFocus: Boolean,
    muted: Boolean,
    onToggleSound: () -> Unit,
    onNext: (PlayableLesson) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var paused by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf<Interaction?>(null) }
    var answer by remember { mutableStateOf<Int?>(null) }
    var ended by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var next by remember { mutableStateOf<PlayableLesson?>(null) }
    var position by remember { mutableFloatStateOf(0f) }
    val manifest = lesson.manifest
    val player = rememberLessonPlayer(
        source = requireNotNull(lesson.source),
        manifest = manifest,
        onInteraction = { question = it },
        onEnded = {
            ended = true
            scope.launch { learning.completed(lesson) }
        },
        onError = { failed = true },
    )
    SideEffect {
        player.volume = if (hasAudioFocus && !muted) 1f else 0f
        player.playWhenReady = focused && !paused && question == null && !reporting && !ended && !failed
    }
    LaunchedEffect(player, ended) {
        if (ended) {
            next = learning.nextLessonId(lesson.row.topicId, excluding = lesson.row.lessonId)
                ?.let { learning.playable(it) }?.takeIf { it.source != null }
        } else {
            while (true) {
                position = player.currentPosition / 1000f
                delay(100)
            }
        }
    }

    val canSwipe = question == null && !reporting
    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(canSwipe) {
            if (!canSwipe) return@pointerInput
            var distance = 0f
            detectVerticalDragGestures(
                onDragStart = { distance = 0f },
                onDragEnd = { if (distance <= -72.dp.toPx()) onDismiss() },
            ) { _, drag -> distance += drag }
        },
    ) {
        LessonVideo(player, android.graphics.Color.BLACK, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().clickable(enabled = canSwipe && !ended && !failed) { paused = !paused })
        if (paused && canSwipe && !ended && !failed) {
            ReelButton(Icons.Filled.PlayArrow, "Resume lesson", { paused = false }, Modifier.align(Alignment.Center))
        }

        if (canSwipe && !ended && !failed) {
            ReelDetails(lesson, Modifier.align(Alignment.BottomCenter)) {
                ReelButton(
                    if (muted || !hasAudioFocus) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    when {
                        !hasAudioFocus -> "Lesson sound unavailable"
                        muted -> "Unmute lesson"
                        else -> "Mute lesson"
                    },
                    onToggleSound,
                    enabled = hasAudioFocus,
                )
                if (!lesson.row.bundled) {
                    ReelButton(Icons.Filled.Flag, "Report a mistake", { reporting = true })
                }
            }
        }

        if (ended || failed) {
            val nextLesson = next
            ReelSheet(Modifier.align(Alignment.BottomCenter)) {
                Text(if (failed) "Lesson unavailable" else manifest.concept, style = MaterialTheme.typography.headlineSmall)
                Text(if (failed) "The video couldn’t play." else manifest.recap)
                if (failed) PrimaryButton("Try again", onRetry)
                if (!failed && nextLesson != null) PrimaryButton("Another lesson", { onNext(nextLesson) })
                SecondaryButton("Back to my reels", onDismiss)
            }
        }

        question?.let { interaction ->
            PaperTheme {
                LessonQuestion(interaction, answer, onAnswer = { index ->
                    if (answer == null) {
                        answer = index
                        val correct = interaction.answerIndex?.let { it == index }
                        scope.launch { learning.answered(lesson, interaction.id, index, correct) }
                    }
                }, onContinue = {
                    question = null
                    answer = null
                })
            }
        }

        if (reporting) {
            PaperTheme {
                LessonReportForm(
                    onSend = { reason, note ->
                        val at = player.currentPosition / 1000.0
                        scope.launch {
                            learning.report(lesson, reason, note, at)
                            Toast.makeText(context, "Thanks, we'll review this lesson.", Toast.LENGTH_SHORT).show()
                        }
                        reporting = false
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().imePadding()
                        .background(BoomColors.Paper, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .verticalScroll(rememberScrollState()).padding(top = 20.dp),
                    onCancel = { reporting = false },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentedProgress(
                manifest.segments.map { it.endS.toFloat() },
                if (ended) manifest.durationS.toFloat() else position,
                Modifier.weight(1f),
                track = Color(0x66343744),
                fill = BoomColors.Boom,
            )
            ReelButton(Icons.Filled.Close, "Back to my reels", onDismiss)
        }
    }
}

@Composable
private fun ReelDetails(lesson: PlayableLesson, modifier: Modifier, controls: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFA111218))))
            .safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val attribution = listOf("BoomDoom", lesson.topicName).filter { it.isNotBlank() }.joinToString(" · ")
            Text(attribution, color = BoomColors.Learn, style = MaterialTheme.typography.labelLarge)
            Text(lesson.manifest.concept, color = Color.White, style = MaterialTheme.typography.titleMedium)
            lesson.manifest.disclaimer?.let {
                Text(it, color = BoomColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = { controls() })
    }
}

@Composable
private fun ReelSheet(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().background(BoomColors.Card, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = { content() },
    )
}

@Composable
private fun ReelButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color(0xC9111218), contentColor = Color.White,
            disabledContainerColor = Color(0xC9111218), disabledContentColor = Color.LightGray,
        ),
    ) { Icon(icon, description) }
}
