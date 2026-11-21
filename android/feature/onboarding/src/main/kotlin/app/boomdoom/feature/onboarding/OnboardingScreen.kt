package app.boomdoom.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.Illus
import app.boomdoom.core.designsystem.Illustration
import app.boomdoom.core.designsystem.NightTheme
import app.boomdoom.core.designsystem.StepDots

/** US-01 → US-07. [onPlayLesson] opens the player for the "try a lesson" step and returns here. */
@Composable
fun OnboardingScreen(onPlayLesson: (String) -> Unit, onFinished: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = s.step != Step.WELCOME) { vm.back() }
    NightTheme {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0xFF2A1712), BoomColors.Background), radius = 1400f),
            ).safeDrawingPadding(),
        ) {
            AnimatedContent(s.step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { step ->
                when (step) {
                    Step.WELCOME -> WelcomeStep { vm.go(Step.BUDGET) }
                    Step.BUDGET -> Stepped(step, vm::back) { BudgetStep(s, vm) }
                    Step.LABELS -> Stepped(step, vm::back) { LabelsStep(s, vm) }
                    Step.FLAGS -> Stepped(step, vm::back) { FlagsStep(s, vm) }
                    Step.TOPICS -> Stepped(step, vm::back) { TopicsStep(s, vm) }
                    Step.TRY -> Stepped(step, vm::back) {
                        TryLessonStep(s, onPlay = { id -> vm.go(Step.PERMISSIONS); onPlayLesson(id) },
                            onSkip = { vm.go(Step.PERMISSIONS) })
                    }
                    Step.PERMISSIONS -> Stepped(step, vm::back) { PermissionsStep(s, vm) { vm.finish(onFinished) } }
                }
            }
        }
    }
}

/** Logo row + back arrow + step dots (designs 02–09). */
@Composable
private fun Stepped(step: Step, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Illustration(Illus.logo, Modifier.size(34.dp))
            Spacer(Modifier.width(8.dp))
            Text("BoomDoom", style = MaterialTheme.typography.titleMedium, color = BoomColors.TextPrimary)
        }
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BoomColors.TextPrimary)
            }
            step.number?.let { StepDots(it, 6, Modifier.align(Alignment.Center)) }
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}
