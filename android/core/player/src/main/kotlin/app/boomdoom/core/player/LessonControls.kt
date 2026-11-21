package app.boomdoom.core.player

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.Illus
import app.boomdoom.core.designsystem.Illustration
import app.boomdoom.core.designsystem.PillShape
import app.boomdoom.core.designsystem.PrimaryButton
import app.boomdoom.core.designsystem.Tag
import app.boomdoom.core.designsystem.TextLink
import app.boomdoom.core.model.Interaction
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LessonQuestion(interaction: Interaction, answer: Int?, onAnswer: (Int) -> Unit, onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (interaction.type == "true_false") {
            TrueFalseCard(interaction, answer, onAnswer, onContinue)
        } else {
            QuestionSheet(interaction, answer, onAnswer, onContinue, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Segmented bar from segments[].end_s (Tech Spec §7.2). */
@Composable
fun SegmentedProgress(
    ends: List<Float>,
    position: Float,
    modifier: Modifier = Modifier,
    track: Color = Color(0x33000000),
    fill: Color = BoomColors.LearnDeep,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        var start = 0f
        ends.forEach { end ->
            val f = ((position - start) / (end - start).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            Box(Modifier.weight(1f).height(6.dp).clip(PillShape).background(track)) {
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(PillShape).background(fill))
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
private fun QuestionSheet(i: Interaction, answer: Int?, onAnswer: (Int) -> Unit, onContinue: () -> Unit, modifier: Modifier) {
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
            val chosen = answer == idx
            val isRight = graded && idx == i.answerIndex
            val showRight = answer != null && isRight
            val border = when { showRight -> BoomColors.LearnDeep; chosen -> Color(0xFFE5484D); else -> Color(0xFFBFB8AA) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (showRight) BoomColors.LearnTint else Color.White)
                    .border(if (showRight || chosen) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
                    .clickable(enabled = answer == null, role = Role.RadioButton) { onAnswer(idx) }
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
        if (answer != null) {
            val correct = !graded || answer == i.answerIndex
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
private fun TrueFalseCard(i: Interaction, answer: Int?, onAnswer: (Int) -> Unit, onContinue: () -> Unit) {
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
                .pointerInput(answer) {
                    if (answer != null) return@pointerInput
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
        if (answer == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TfButton("False", Color(0xFFFF7A6B), Modifier.weight(1f)) { onAnswer(falseIdx) }
                TfButton("True", Color(0xFF6FE0C9), Modifier.weight(1f)) { onAnswer(trueIdx) }
            }
        } else {
            val correct = answer == i.answerIndex
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

@Composable
fun LessonReportForm(
    onSend: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    var reason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    Column(modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Report a mistake", style = MaterialTheme.typography.headlineSmall, color = BoomColors.Ink)
        Spacer(Modifier.height(12.dp))
        listOf("wrong_fact" to "Wrong fact", "other" to "Confusing", "audio_video" to "Audio or video problem",
            "inappropriate" to "Inappropriate").forEach { (key, label) ->
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
        if (onCancel != null) TextLink("Cancel", onCancel, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
