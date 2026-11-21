package app.boomdoom.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.boomdoom.core.designsystem.AccentHeadline
import app.boomdoom.core.designsystem.BoomCard
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.CardShape
import app.boomdoom.core.designsystem.NumberEntryDialog
import app.boomdoom.core.designsystem.Illus
import app.boomdoom.core.designsystem.Illustration
import app.boomdoom.core.designsystem.PaperPanel
import app.boomdoom.core.designsystem.PillShape
import app.boomdoom.core.designsystem.PrimaryButton
import app.boomdoom.core.designsystem.Secondary
import app.boomdoom.core.designsystem.SelectChip
import app.boomdoom.core.designsystem.Tag
import app.boomdoom.core.designsystem.TextLink
import app.boomdoom.core.model.BuiltInLabel
import app.boomdoom.core.model.PresetTopic

internal val labelIcons: Map<String, Pair<ImageVector, Color>> = mapOf(
    "comedy" to (Icons.Filled.TheaterComedy to Color(0xFFFF7A6B)),
    "fitness" to (Icons.Filled.FitnessCenter to BoomColors.Learn),
    "education" to (Icons.Filled.School to BoomColors.Learn),
    "food" to (Icons.Filled.LunchDining to BoomColors.Warning),
    "news" to (Icons.Filled.Newspaper to Color(0xFFB8C0D0)),
    "finance" to (Icons.Filled.Savings to BoomColors.Warning),
    "rage_bait" to (Icons.Filled.LocalFireDepartment to BoomColors.Boom),
    "ads" to (Icons.Filled.Campaign to Color(0xFFD9DDE6)),
)

/** Scrollable body + pinned bottom actions. */
@Composable
private fun StepBody(bottom: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
            content = content)
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, content = bottom)
    }
}

@Composable
private fun Banner(id: Int, modifier: Modifier = Modifier) {
    PaperPanel(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Illustration(id, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    }
}

// ------------------------------------------------------------------ O-1 Welcome

@Composable
internal fun WelcomeStep(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 14.dp).clip(PillShape).background(BoomColors.Boom))
            Spacer(Modifier.width(8.dp))
            Text("WELCOME TO BOOMDOOM", style = MaterialTheme.typography.labelSmall, color = BoomColors.Boom)
        }
        Spacer(Modifier.height(14.dp))
        AccentHeadline("Turn doom\ninto ", "boom.", align = TextAlign.Start,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Secondary("Understand your scrolling habits and turn everyday breaks into learning.", align = TextAlign.Start)
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Illus.welcomeWatch to "See what\nyou watch", Illus.welcomeHabits to "Understand\nyour habits",
                Illus.welcomeLearn to "Replace scrolling\nwith learning").forEach { (img, label) ->
                PaperPanel(Modifier.weight(1f).aspectRatio(0.55f)) {
                    Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Illustration(img, Modifier.weight(1f).fillMaxWidth())
                        Text(label, style = MaterialTheme.typography.labelSmall, color = BoomColors.Ink, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = BoomColors.TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Secondary("Your feed stays private on your device.")
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Get Started", onStart, trailingArrow = true)
        Spacer(Modifier.height(16.dp))
    }
}

// ------------------------------------------------------------------ Name (personal greeting on Home)

@Composable
internal fun NameStep(s: OnboardingState, vm: OnboardingViewModel) {
    StepBody(bottom = {
        PrimaryButton("Continue", { vm.saveName() }, enabled = s.name.isNotBlank())
        TextLink("Skip", { vm.saveName(skip = true) })
    }) {
        AccentHeadline("First, what should we ", "call you?")
        Secondary("Just a first name or nickname. It stays on this phone.")
        Spacer(Modifier.height(24.dp))
        PaperPanel(Modifier.fillMaxWidth().height(170.dp)) {
            Illustration(Illus.welcomeHabits, Modifier.fillMaxSize().padding(12.dp))
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = s.name, onValueChange = vm::setName, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Your name") }, textStyle = MaterialTheme.typography.titleLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (s.name.isNotBlank()) vm.saveName() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BoomColors.Boom, cursorColor = BoomColors.Boom),
        )
        if (s.name.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("Nice to meet you, ${s.name.trim()} 👋", style = MaterialTheme.typography.titleMedium, color = BoomColors.TextPrimary)
        }
    }
}

// ------------------------------------------------------------------ O-2 Budget + check-ins

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BudgetStep(s: OnboardingState, vm: OnboardingViewModel) {
    var custom by remember { mutableStateOf<String?>(null) }
    when (custom) {
        "budget" -> NumberEntryDialog("Reels per day", s.budget, 10..2000, "reels", { custom = null }) { vm.setBudget(it); custom = null }
        "checkin" -> NumberEntryDialog("Check in every", s.checkIn, 3..100, "reels", { custom = null }) { vm.setCheckIn(it); custom = null }
    }
    StepBody(bottom = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = BoomColors.TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp)); Secondary("BoomDoom never locks your apps.")
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Continue", { vm.saveBudget() })
    }) {
        AccentHeadline("Your scroll ", "budget")
        Secondary("Set simple limits and stay in control of your time.")
        Banner(Illus.budget)
        BoomCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.VideoLibrary, Color(0xFF5A2A2A), BoomColors.Budget)
                Spacer(Modifier.width(10.dp))
                Text("How many reels a day?", style = MaterialTheme.typography.titleMedium)
            }
            Text("${s.budget}", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Slider(
                value = s.budget.coerceIn(50, 500).toFloat(), onValueChange = { vm.setBudget((it / 10).toInt() * 10) }, valueRange = 50f..500f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = BoomColors.Budget,
                    inactiveTrackColor = BoomColors.Border),
            )
            val presets = listOf(100, 150, 200, 300)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { v -> SelectChip("$v", s.budget == v, { vm.setBudget(v) }) }
                SelectChip("Custom", s.budget !in presets, { custom = "budget" })
            }
        }
        Spacer(Modifier.height(12.dp))
        BoomCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Notifications, Color(0xFF4A3A1A), BoomColors.Warning)
                Spacer(Modifier.width(10.dp))
                Text("Check in with me every…", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            val presets = listOf(10, 15, 25, 30)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { v -> SelectChip("$v reels", s.checkIn == v, { vm.setCheckIn(v) }) }
                SelectChip(if (s.checkIn in presets) "Custom" else "Custom · ${s.checkIn}", s.checkIn !in presets, { custom = "checkin" })
            }
            Spacer(Modifier.height(10.dp))
            Secondary("After every ${s.checkIn} reels, you'll see a short summary and a chance to learn.", align = TextAlign.Start)
        }
    }
}

@Composable
internal fun IconBadge(icon: ImageVector, bg: Color, tint: Color, size: Int = 36) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.6).dp))
    }
}

// ------------------------------------------------------------------ O-3 Labels

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabelsStep(s: OnboardingState, vm: OnboardingViewModel) {
    var adding by remember { mutableStateOf(false) }
    StepBody(bottom = {
        PrimaryButton("Continue", { vm.saveLabels() })
        TextLink("Skip", { vm.saveLabels(skip = true) })
    }) {
        AccentHeadline("Anything specific you want us to ", "spot?")
        Secondary("Help us identify the kind of content you often get pulled into.")
        Banner(Illus.labels)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4) {
            BuiltInLabel.entries.forEach { l ->
                val (icon, tint) = labelIcons.getValue(l.key)
                BoomCard(Modifier.weight(1f).height(86.dp), padding = 6.dp) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(l.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Secondary("We always recognize these.")
        Spacer(Modifier.height(14.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.customLabels.forEach { l ->
                Text("$l  ✕", style = MaterialTheme.typography.labelLarge, color = BoomColors.TextPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF123B38))
                        .border(1.5.dp, BoomColors.Learn, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { vm.removeLabel(l) }.padding(horizontal = 18.dp, vertical = 14.dp))
            }
            if (s.customLabels.size < 2) DashedButton("Add your own") { adding = true }
        }
    }
    if (adding) TextEntryDialog("Add a category", "e.g. Cricket", onDismiss = { adding = false }) { vm.addLabel(it); adding = false }
}

@Composable
internal fun DashedButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).border(BorderStroke(1.5.dp, Color(0xFF6B6F80)), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, null, tint = BoomColors.TextPrimary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = BoomColors.TextPrimary)
    }
}

@Composable
internal fun TextEntryDialog(title: String, hint: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(40) }, placeholder = { Text(hint) }, singleLine = true) },
        confirmButton = { TextButton({ onSave(text) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ O-4 Flags

@Composable
internal fun FlagsStep(s: OnboardingState, vm: OnboardingViewModel) {
    StepBody(bottom = {
        PrimaryButton("Save & Continue", { vm.saveFlags() })
        TextLink("Skip", { vm.saveFlags(skip = true) })
    }) {
        AccentHeadline("Anything you'd like to see ", "less", " of?")
        Secondary("Tell us what often feels like a time-waste for you.")
        Banner(Illus.flags)
        BoomCard(Modifier.fillMaxWidth(), padding = 8.dp) {
            val rows = BuiltInLabel.entries.map { it.key to it.displayName } + s.customLabels.map { it.lowercase() to it }
            rows.forEach { (key, name) ->
                val (icon, tint) = labelIcons[key] ?: (Icons.Filled.Tag to BoomColors.Learn)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    if (key in s.flagged) {
                        Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.Flag, null, tint = BoomColors.Warning, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Not worth my time", style = MaterialTheme.typography.bodySmall, color = BoomColors.TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Switch(key in s.flagged, { vm.toggleFlag(key) },
                        colors = SwitchDefaults.colors(checkedTrackColor = BoomColors.Boom, checkedThumbColor = Color.White))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BoomCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = BoomColors.TextSecondary)
                Spacer(Modifier.width(12.dp))
                Secondary("We'll show a small tag on the reel and highlight it in your insights.", align = TextAlign.Start)
            }
        }
    }
}

// ------------------------------------------------------------------ O-5 Topics

private val topicTagColors = listOf(Color(0xFFB9EDE3), Color(0xFFB9EDE3), Color(0xFFB9EDE3), Color(0xFFFFD9A0),
    Color(0xFFDCD3FA), Color(0xFFDCD3FA), Color(0xFFFFD1D6), Color(0xFFC9E3FB))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopicsStep(s: OnboardingState, vm: OnboardingViewModel) {
    var adding by remember { mutableStateOf(false) }
    StepBody(bottom = {
        PrimaryButton("Continue", { vm.saveTopics() }, enabled = s.topics.isNotEmpty() || s.customTopics.isNotEmpty())
    }) {
        AccentHeadline("What would you love to ", "learn?")
        Secondary("Pick a few topics and we'll turn your breaks into quick, fun learning sessions.")
        Spacer(Modifier.height(20.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
            PresetTopic.entries.forEachIndexed { i, t ->
                val selected = t.id in s.topics
                Box(Modifier.weight(1f).aspectRatio(1.6f).clip(CardShape)
                    .border(if (selected) 3.dp else 1.dp, if (selected) BoomColors.Learn else BoomColors.PaperBorder, CardShape)
                    .background(BoomColors.Paper).clickable(role = Role.Checkbox) { vm.toggleTopic(t.id) }) {
                    Illus.topics[t.id]?.let { Illustration(it, Modifier.fillMaxSize().padding(bottom = 26.dp), contentScale = ContentScale.Fit) }
                    Tag(t.displayName, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), color = topicTagColors[i])
                    if (selected) Icon(Icons.Filled.CheckCircle, "Selected", tint = BoomColors.LearnDeep,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        s.customTopics.forEach { Text("✓ $it", style = MaterialTheme.typography.titleSmall, color = BoomColors.Learn) }
        DashedButton("Add my own topic") { adding = true }
        Secondary("Fine-tune difficulty later in Learn → My topics")
    }
    if (adding) TextEntryDialog("Add a topic", "e.g. System Design", onDismiss = { adding = false }) {
        vm.addCustomTopic(it); adding = false
    }
}

// ------------------------------------------------------------------ O-6 Try a lesson

@Composable
internal fun TryLessonStep(s: OnboardingState, onPlay: (String) -> Unit, onSkip: () -> Unit) {
    val topic = s.topics.mapNotNull { PresetTopic.byId(it) }.firstOrNull()
    StepBody(bottom = {
        if (s.starterLessonId != null) {
            PrimaryButton("Play", { onPlay(s.starterLessonId) }, leading = { Icon(Icons.Filled.PlayArrow, null) })
        }
        TextLink("Skip for now", onSkip)
    }) {
        AccentHeadline("Try a ", "30-second", "\nlesson first?")
        Secondary("A quick, bite-sized lesson from the topics you chose.")
        Spacer(Modifier.height(16.dp))
        PaperPanel(Modifier.fillMaxWidth().aspectRatio(0.95f)) {
            Illustration(Illus.tryLesson, Modifier.fillMaxSize().padding(top = 50.dp, bottom = 20.dp))
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Tag(topic?.displayName ?: "Your topic")
                Row(Modifier.clip(PillShape).background(Color(0xFFE6E1D6)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = BoomColors.Ink, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp)); Text("30 sec", style = MaterialTheme.typography.labelSmall, color = BoomColors.Ink)
                }
            }
        }
        if (s.starterLessonId == null) {
            Spacer(Modifier.height(10.dp))
            Secondary("Starter lessons are available for the preset topics. Your custom topics will be ready soon.")
        }
    }
}

// ------------------------------------------------------------------ O-7 Permissions (+ O-8 guide)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PermissionsStep(s: OnboardingState, vm: OnboardingViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var guide by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshPermissions()
        // US-07: show the restricted-settings guide when Accessibility couldn't be enabled after returning twice
        if (s.accessibilityAttempts >= 2 && !vm.monitoring.accessibilityEnabled()) guide = true
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }

    StepBody(bottom = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = BoomColors.TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp)); Secondary("Nothing from your feed leaves this phone.")
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(if (s.accessibility) "Finish setup" else "Finish without monitoring", onDone)
    }) {
        AccentHeadline("Let's get BoomDoom\n", "ready.")
        Secondary("A few quick permissions to track your scrolling and keep you on track.")
        Banner(Illus.permissions)
        PermissionRow(Icons.Filled.Accessibility, Color(0xFF8EE3D2), "Accessibility", "Count reels as you scroll.", s.accessibility) {
            vm.accessibilityRequested(); ctx.startActivity(vm.monitoring.accessibilitySettingsIntent())
        }
        if (!s.accessibility) TextLink("Toggle greyed out?", { guide = true })
        PermissionRow(Icons.Filled.Layers, Color(0xFFC9C2F5), "Display over apps", "Show your budget, check-ins, and lessons.", s.overlay) {
            ctx.startActivity(vm.monitoring.overlaySettingsIntent())
        }
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionRow(Icons.Filled.Notifications, Color(0xFFFFE3A8), "Notifications (optional)", "Tell me when lessons are ready.",
                s.notifications) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        PermissionRow(Icons.Filled.Visibility, Color(0xFFFFD9A0), "Screen understanding", "Recognise content, only on this phone.",
            granted = false, comingSoon = true) {}
        PermissionRow(Icons.Filled.Mic, Color(0xFFC9C2F5), "Audio understanding (optional)", "Understand Hindi talking reels.",
            granted = false, comingSoon = true) {}
        if (!s.accessibility) {
            Spacer(Modifier.height(6.dp))
            Secondary("Without Accessibility, BoomDoom can't count reels. Learn still works.")
        }
    }

    if (guide) {
        ModalBottomSheet(onDismissRequest = { guide = false }, containerColor = BoomColors.Card) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Toggle greyed out?", style = MaterialTheme.typography.headlineSmall)
                Secondary("No worries! Android restricts apps installed outside an app store. Follow these steps:", align = TextAlign.Start)
                Spacer(Modifier.height(14.dp))
                listOf(
                    "Open BoomDoom's App info" to "Long-press the app icon and tap App info.",
                    "Tap ⋮ then 'Allow restricted settings'" to "On the App info screen, open the ⋮ menu.",
                    "Come back and turn on Accessibility" to "Return to BoomDoom and enable it.",
                ).forEachIndexed { i, (t, d) ->
                    BoomCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Row {
                            Box(Modifier.size(28.dp).clip(PillShape).background(BoomColors.Boom), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = BoomColors.Background, style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column { Text(t, style = MaterialTheme.typography.titleSmall); Secondary(d, align = TextAlign.Start) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Open App info", { guide = false; ctx.startActivity(vm.monitoring.appInfoIntent()) })
            }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, badge: Color, title: String, reason: String, granted: Boolean,
                          comingSoon: Boolean = false, onEnable: () -> Unit) {
    BoomCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, badge, BoomColors.Ink, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Secondary(reason, align = TextAlign.Start)
            }
            Spacer(Modifier.width(8.dp))
            when {
                comingSoon -> Text("Coming soon", style = MaterialTheme.typography.labelSmall, color = BoomColors.TextSecondary,
                    modifier = Modifier.clip(PillShape).background(BoomColors.CardRaised).padding(horizontal = 10.dp, vertical = 6.dp))
                granted -> Row(Modifier.clip(PillShape).background(Color(0xFF2E9E50)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp)); Text("Enabled", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                else -> Text("Enable", style = MaterialTheme.typography.labelMedium, color = BoomColors.Background,
                    modifier = Modifier.clip(PillShape).background(BoomColors.Boom).clickable(role = Role.Button, onClick = onEnable)
                        .padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

