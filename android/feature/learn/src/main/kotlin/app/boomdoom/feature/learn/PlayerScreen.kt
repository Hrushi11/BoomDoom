package app.boomdoom.feature.learn

import android.os.Looper
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.Illus
import app.boomdoom.core.designsystem.Illustration
import app.boomdoom.core.designsystem.LearnButton
import app.boomdoom.core.designsystem.PaperTheme
import app.boomdoom.core.designsystem.PillShape
import app.boomdoom.core.designsystem.PrimaryButton
import app.boomdoom.core.designsystem.SecondaryButton
import app.boomdoom.core.designsystem.Tag
import app.boomdoom.core.designsystem.TextLink
import app.boomdoom.core.model.Interaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(lesson.source!!))
            prepare()
            playWhenReady = true
            // Pause exactly at each interaction point (Tech Spec §7.2).
            m.interactions.forEach { i ->
                createMessage { _, _ -> pause(); vm.onInteractionPoint(i) }
                    .setPosition((i.pauseAtS * 1000).toLong())
                    .setLooper(Looper.getMainLooper())
                    .setDeleteAfterDelivery(true)
                    .send()
            }
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) vm.onEnded()
                }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
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
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT // never crop captions
                    setShutterBackgroundColor(android.graphics.Color.rgb(250, 246, 238))
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
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
            if (asking.interaction.type == "true_false") {
                TrueFalseCard(asking, onAnswer = vm::answer, onContinue = vm::resume)
            } else {
                QuestionSheet(asking, onAnswer = vm::answer, onContinue = vm::resume, modifier = Modifier.align(Alignment.BottomCenter))
            }
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

/** Segmented bar from segments[].end_s (Tech Spec §7.2). */
@Composable
private fun SegmentedProgress(ends: List<Float>, position: Float, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        var start = 0f
        ends.forEach { end ->
            val f = ((position - start) / (end - start).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            Box(Modifier.weight(1f).height(6.dp).clip(PillShape).background(Color(0x33000000))) {
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(PillShape).background(BoomColors.LearnDeep))
            }
            start = end
        }
    }
}

private fun typeLabel(type: String) = when (type) {
    "predict" -> "Predict"; "mcq" -> "Quick quiz"; "reflect" -> "Reflect"; "your_answer" -> "Your answer"
    "go_deeper" -> "Go deeper"; else -> "Question"
}

/** Predict / multiple choice (L3). */
@Composable
private fun QuestionSheet(asking: PlayerPhase.Asking, onAnswer: (Int) -> Unit, onContinue: () -> Unit, modifier: Modifier) {
    val i = asking.interaction
    val graded = i.answerIndex != null
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(BoomColors.Paper)
            .navigationBarsPadding().padding(20.dp),
    ) {
        Row(Modifier.clip(PillShape).background(BoomColors.LearnTint).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lightbulb, null, tint = BoomColors.Ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp)); Text(typeLabel(i.type), style = MaterialTheme.typography.labelMedium, color = BoomColors.Ink)
        }
        Spacer(Modifier.height(10.dp))
        Text(i.question, style = MaterialTheme.typography.headlineSmall, color = BoomColors.Ink)
        Spacer(Modifier.height(14.dp))
        i.options.forEachIndexed { idx, option ->
            val chosen = asking.answer == idx
            val isRight = graded && idx == i.answerIndex
            val showRight = asking.answer != null && isRight
            val border = when { showRight -> BoomColors.LearnDeep; chosen -> Color(0xFFE5484D); else -> Color(0xFFBFB8AA) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (showRight) BoomColors.LearnTint else Color.White)
                    .border(if (showRight || chosen) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
                    .clickable(enabled = asking.answer == null, role = Role.RadioButton) { onAnswer(idx) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when { showRight -> Icons.Filled.CheckCircle; chosen -> Icons.Outlined.Cancel; else -> Icons.Outlined.Circle },
                    null, tint = border, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(option, style = MaterialTheme.typography.titleSmall, color = BoomColors.Ink)
            }
        }
        if (asking.answer != null) {
            val correct = !graded || asking.answer == i.answerIndex
            Spacer(Modifier.height(10.dp))
            Text(
                (if (correct) "✓  " + i.feedbackCorrect else i.feedbackWrong),
                style = MaterialTheme.typography.titleSmall, color = BoomColors.Ink,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (correct) Color(0xFFDDF3D9) else Color(0xFFFFE3DC)).padding(14.dp),
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Continue", onContinue)
        } else {
            TextLink("Skip question", onContinue, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** Swipe true/false (L4) — always with tap buttons as an alternative (US-19, US-33). */
@Composable
private fun TrueFalseCard(asking: PlayerPhase.Asking, onAnswer: (Int) -> Unit, onContinue: () -> Unit) {
    val i = asking.interaction
    val trueIdx = i.options.indexOfFirst { it.equals("true", true) }.takeIf { it >= 0 } ?: 0
    val falseIdx = i.options.indexOfFirst { it.equals("false", true) }.takeIf { it >= 0 } ?: 1
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(BoomColors.Paper.copy(alpha = 0.97f)).statusBarsPadding().navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Tag("True or false?", color = BoomColors.LearnTint)
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f).offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { rotationZ = offsetX.value / 40f }
                .clip(RoundedCornerShape(28.dp)).background(Color(0xFFFFFBF4)).border(1.dp, BoomColors.PaperBorder, RoundedCornerShape(28.dp))
                .pointerInput(asking.answer) {
                    if (asking.answer != null) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offsetX.value > 220 -> onAnswer(trueIdx)
                                    offsetX.value < -220 -> onAnswer(falseIdx)
                                }
                                offsetX.animateTo(0f)
                            }
                        },
                    ) { _, drag -> scope.launch { offsetX.snapTo(offsetX.value + drag) } }
                }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Illustration(Illus.homeLesson, Modifier.fillMaxWidth().weight(1f))
            Text(i.question, style = MaterialTheme.typography.headlineSmall, color = BoomColors.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("← False", color = Color(0xFFE5484D), style = MaterialTheme.typography.titleSmall)
                Text("True →", color = BoomColors.LearnDeep, style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (asking.answer == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TfButton("False", Color(0xFFFF7A6B), Modifier.weight(1f)) { onAnswer(falseIdx) }
                TfButton("True", Color(0xFF6FE0C9), Modifier.weight(1f)) { onAnswer(trueIdx) }
            }
        } else {
            val correct = asking.answer == i.answerIndex
            Text(if (correct) "✓  ${i.feedbackCorrect}" else i.feedbackWrong, style = MaterialTheme.typography.titleSmall,
                color = BoomColors.Ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (correct) Color(0xFFDDF3D9) else Color(0xFFFFE3DC)).padding(14.dp))
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Continue", onContinue)
        }
    }
}

@Composable
private fun TfButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.height(64.dp), shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = BoomColors.Ink)) {
        Text(text, style = MaterialTheme.typography.headlineSmall)
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
    var reason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BoomColors.Paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Report a mistake", style = MaterialTheme.typography.headlineSmall, color = BoomColors.Ink)
            Spacer(Modifier.height(12.dp))
            listOf("wrong_fact" to "📖  Wrong fact", "other" to "❓  Confusing", "audio_video" to "▶️  Audio/video problem",
                "inappropriate" to "⚠️  Inappropriate").forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .border(1.dp, BoomColors.PaperBorder, RoundedCornerShape(12.dp)).clickable(role = Role.RadioButton) { reason = key }
                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = BoomColors.Ink, modifier = Modifier.weight(1f))
                    RadioButton(reason == key, { reason = key })
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), placeholder = { Text("Share more details… (optional)") },
                minLines = 2)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Send report", { reason?.let { onSend(it, note.ifBlank { null }) } }, enabled = reason != null)
            Text("We'll review this lesson.", style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

