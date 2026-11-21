package app.boomdoom.feature.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrackChanges
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.CardShape
import app.boomdoom.core.designsystem.Illus
import app.boomdoom.core.designsystem.Illustration
import app.boomdoom.core.designsystem.LearnButton
import app.boomdoom.core.designsystem.NumberEntryDialog
import app.boomdoom.core.designsystem.TimeEntryDialog
import app.boomdoom.core.designsystem.formatTime
import app.boomdoom.core.designsystem.PaperTheme
import app.boomdoom.core.designsystem.PillShape
import app.boomdoom.core.designsystem.SelectChip
import app.boomdoom.core.model.BuiltInLabel

enum class SettingsPage { GOALS, FLAGS, PRIVACY, PERMISSIONS, ABOUT }

// ------------------------------------------------------------------ shared bits

@Composable
private fun Page(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    PaperTheme {
        Column(Modifier.fillMaxSize().background(BoomColors.Paper).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(title, style = MaterialTheme.typography.headlineLarge)
            }
            content()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(BoomColors.PaperCard).border(1.dp, BoomColors.PaperBorder, CardShape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp), content = content)
}

@Composable
private fun MenuRow(icon: ImageVector, tint: Color, title: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = BoomColors.InkSecondary, modifier = Modifier.size(22.dp)) // one icon colour for every row
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
            }
            Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title }, enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = BoomColors.LearnDeep, checkedThumbColor = Color.White))
        }
    }
}

// ------------------------------------------------------------------ Y-1 You

@Composable
fun YouScreen(onOpen: (SettingsPage) -> Unit, onOpenTopics: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }
    Page("You", onBack = null) {
        Card(onClick = { editingName = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Illustration(Illus.welcomeHabits, Modifier.size(76.dp).clip(PillShape).background(BoomColors.LearnTint))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(s?.displayName?.ifBlank { null } ?: "Anonymous", style = MaterialTheme.typography.titleLarge)
                    Text("on this phone · tap to set your name", style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
                }
            }
        }
        MenuRow(Icons.Filled.TrackChanges, Color(0xFFE5484D), "Scrolling goals") { onOpen(SettingsPage.GOALS) }
        MenuRow(Icons.Filled.Flag, Color(0xFFFF7A6B), "Content & flags") { onOpen(SettingsPage.FLAGS) }
        MenuRow(Icons.Filled.MenuBook, BoomColors.LearnDeep, "Learning preferences", onOpenTopics)
        MenuRow(Icons.Filled.Security, Color(0xFF8A7CE0), "Privacy & data") { onOpen(SettingsPage.PRIVACY) }
        MenuRow(Icons.Filled.Layers, Color(0xFF4A90D9), "Permissions") { onOpen(SettingsPage.PERMISSIONS) }
        MenuRow(Icons.Filled.Info, BoomColors.LearnDeep, "About") { onOpen(SettingsPage.ABOUT) }
    }
    if (editingName) {
        var name by remember { mutableStateOf(s?.displayName ?: "") }
        AlertDialog(onDismissRequest = { editingName = false }, title = { Text("What should we call you?") },
            text = { OutlinedTextField(name, { name = it.take(30) }, singleLine = true, placeholder = { Text("Your first name") }) },
            confirmButton = { TextButton({ vm.setName(name); editingName = false }) { Text("Save") } },
            dismissButton = { TextButton({ editingName = false }) { Text("Cancel") } })
    }
}

// ------------------------------------------------------------------ Y-2 Scrolling goals

private enum class GoalDialog { BUDGET, CHECK_IN, OVER_BUDGET, RESET }

/** US-27 (design Y-2). Presets for speed, plus a custom value for every goal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st = s ?: return
    var budget by remember(st.budget) { mutableIntStateOf(st.budget) }
    var dialog by remember { mutableStateOf<GoalDialog?>(null) }
    val checkInPresets = listOf(5, 10, 15, 25, 50)
    Page("Scrolling goals", onBack) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily budget", style = MaterialTheme.typography.titleLarge)
                    Text("Maximum number of reels per day.", style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
                }
                EditablePill("$budget reels") { dialog = GoalDialog.BUDGET }
            }
            Slider(budget.coerceIn(50, 500).toFloat(), { budget = (it / 10).toInt() * 10 }, valueRange = 50f..500f,
                onValueChangeFinished = { vm.setGoals(budget = budget) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = BoomColors.LearnDeep))
            Text("Tap the number to type any value.", style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check in every", style = MaterialTheme.typography.titleLarge)
                    Text("A quick check-in on your progress.", style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
                }
                EditablePill("${st.checkInInterval} reels") { dialog = GoalDialog.CHECK_IN }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                checkInPresets.forEach { v ->
                    SelectChip("$v", st.checkInInterval == v, { vm.setGoals(checkIn = v) }, selectedColor = BoomColors.LearnDeep)
                }
                val custom = st.checkInInterval !in checkInPresets
                SelectChip(if (custom) "Custom · ${st.checkInInterval}" else "Custom", custom, { dialog = GoalDialog.CHECK_IN },
                    selectedColor = BoomColors.LearnDeep)
            }
        }
        ToggleRow("Ease off when I keep scrolling", "Two 'Continue scrolling' taps in a row double the check-in interval for that session.",
            st.backoffEnabled) { vm.setGoals(backoff = it) }
        Card {
            Text("Over-budget check-ins", style = MaterialTheme.typography.titleLarge)
            Text("After I cross my budget, check in again at this interval (never more often than every 10).",
                style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper("−") { vm.setGoals(overBudget = (st.overBudgetInterval - 5).coerceAtLeast(10)) }
                Text("${st.overBudgetInterval} reels", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clip(PillShape).clickable(role = Role.Button) { dialog = GoalDialog.OVER_BUDGET }
                        .padding(horizontal = 16.dp, vertical = 8.dp))
                Stepper("+") { vm.setGoals(overBudget = (st.overBudgetInterval + 5).coerceAtMost(100)) }
            }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("New day starts at", style = MaterialTheme.typography.titleLarge)
                    Text("When your daily budget and insights reset.", style = MaterialTheme.typography.bodySmall,
                        color = BoomColors.InkSecondary)
                }
                Row(Modifier.clip(PillShape).border(1.dp, Color(0xFF9A958B), PillShape)
                    .clickable(role = Role.Button) { dialog = GoalDialog.RESET }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(formatTime(st.resetHour, st.resetMinute), style = MaterialTheme.typography.titleSmall)
                    Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp))
                }
            }
        }
    }
    when (dialog) {
        GoalDialog.BUDGET -> NumberEntryDialog("Daily budget", st.budget, 10..2000, "reels", { dialog = null }) {
            vm.setGoals(budget = it); dialog = null
        }
        GoalDialog.CHECK_IN -> NumberEntryDialog("Check in every", st.checkInInterval, 3..100, "reels", { dialog = null }) {
            vm.setGoals(checkIn = it); dialog = null
        }
        GoalDialog.OVER_BUDGET -> NumberEntryDialog("Over-budget check-ins", st.overBudgetInterval, 10..100, "reels", { dialog = null }) {
            vm.setGoals(overBudget = it); dialog = null
        }
        GoalDialog.RESET -> TimeEntryDialog("New day starts at", st.resetHour, st.resetMinute, { dialog = null }) { h, m ->
            vm.setGoals(resetHour = h, resetMinute = m); dialog = null
        }
        null -> Unit
    }
}

/** Value pill with an edit affordance: tap to type a custom value. */
@Composable
private fun EditablePill(text: String, onClick: () -> Unit) = Row(
    Modifier.clip(PillShape).background(BoomColors.LearnTint).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = BoomColors.LearnDeep)
    Spacer(Modifier.width(4.dp))
    Icon(Icons.Filled.Edit, "Edit", Modifier.size(14.dp), tint = BoomColors.LearnDeep)
}

@Composable
private fun Stepper(label: String, onClick: () -> Unit) = Box(
    Modifier.size(40.dp).clip(PillShape).border(1.dp, Color(0xFF9A958B), PillShape).clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text(label, style = MaterialTheme.typography.titleLarge) }

// ------------------------------------------------------------------ Content & flags

@Composable
fun FlagsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st = s ?: return
    var adding by remember { mutableStateOf(false) }
    Page("Content & flags", onBack) {
        Text("Mark what feels like a time-waste. Flagged content gets a small tag on the reel and is highlighted in insights.",
            style = MaterialTheme.typography.bodyMedium, color = BoomColors.InkSecondary)
        (BuiltInLabel.entries.map { it.key to it.displayName } + st.customLabels.map { it.lowercase() to it }).forEach { (key, name) ->
            ToggleRow(name, "Not worth my time", key in st.flaggedLabels) { vm.toggleFlag(key) }
        }
        Card {
            Text("Your own categories (${st.customLabels.size}/2)", style = MaterialTheme.typography.titleMedium)
            st.customLabels.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton({ vm.setCustomLabels(st.customLabels - l) }) { Text("Remove") }
                }
            }
            if (st.customLabels.size < 2) TextButton({ adding = true }) { Text("+ Add your own") }
        }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("Add a category") },
            text = { OutlinedTextField(name, { name = it.take(30) }, singleLine = true, placeholder = { Text("e.g. Cricket") }) },
            confirmButton = { TextButton({ vm.setCustomLabels(st.customLabels + name.trim()); adding = false }, enabled = name.isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton({ adding = false }) { Text("Cancel") } })
    }
}

// ------------------------------------------------------------------ Y-4 Privacy (+ Y-5 dialogs)

@Composable
fun PrivacyScreen(onBack: () -> Unit, onAccountDeleted: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st = s ?: return
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    Page("Privacy & data", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard(Icons.Filled.Lock, "On this phone", "Reels, labels, insights, audio and transcripts never leave your phone.",
                Color(0xFFEAF7EE), Modifier.weight(1f))
            InfoCard(Icons.Filled.CloudSync, "Sent to BoomDoom", "Only your learning topics and lesson activity.",
                BoomColors.PaperCard, Modifier.weight(1f))
        }
        ToggleRow("Pause monitoring", "Temporarily stop tracking your reels.", st.monitoringPaused) { vm.setPaused(it) }
        ToggleRow("Audio understanding", "On-device speech-to-text for talking reels. Coming soon.", false, enabled = false) {}
        ToggleRow("Topic suggestions from my feed", "Suggest lessons based on what you watch. Coming soon.", false, enabled = false) {}
        DangerRow(Icons.Filled.Delete, "Delete tracking history", outlined = true) { confirm = "history" }
        DangerRow(Icons.Filled.PersonRemove, "Delete account", outlined = false) { confirm = "account" }
    }
    when (confirm) {
        "history" -> ConfirmDialog("Delete tracking history?",
            "This removes your reel counts, categories and insights from this phone. Lessons you've learned stay.",
            onCancel = { confirm = null }) {
            vm.deleteHistory(); confirm = null
            Toast.makeText(ctx, "Tracking history deleted", Toast.LENGTH_SHORT).show()
        }
        "account" -> ConfirmDialog("Delete account?",
            "This deletes your lessons and progress on BoomDoom's servers and clears everything on this phone. This can't be undone.",
            onCancel = { confirm = null }, working = working) {
            working = true
            vm.deleteAccount { ok ->
                working = false; confirm = null
                if (ok) onAccountDeleted()
                else Toast.makeText(ctx, "Couldn't reach BoomDoom. Check your connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String, bg: Color, modifier: Modifier) {
    Column(modifier.clip(CardShape).background(bg).border(1.dp, BoomColors.PaperBorder, CardShape).padding(14.dp)) {
        Icon(icon, null, tint = Color(0xFF8A7CE0))
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
    }
}

@Composable
private fun DangerRow(icon: ImageVector, title: String, outlined: Boolean, onClick: () -> Unit) {
    val red = Color(0xFFD32F2F)
    Row(Modifier.fillMaxWidth().clip(CardShape).background(if (outlined) Color.Transparent else Color(0xFFFDE8E6))
        .border(1.dp, if (outlined) red else Color.Transparent, CardShape).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = red)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = red)
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, onCancel: () -> Unit, working: Boolean = false, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel, containerColor = BoomColors.Paper,
        icon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFD32F2F)) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(body, textAlign = TextAlign.Center) },
        confirmButton = { TextButton(onConfirm, enabled = !working) { Text(if (working) "Deleting…" else "Delete", color = Color(0xFFD32F2F)) } },
        dismissButton = { TextButton(onCancel, enabled = !working) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ Permissions (US-32)

@Composable
fun PermissionsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val m = vm.monitoring
    val s by vm.settings.collectAsStateWithLifecycle()
    @Suppress("UNUSED_EXPRESSION") tick
    Page("Permissions", onBack) {
        PermissionCard("Accessibility", "Counts reels as you scroll. Required for tracking.", m.accessibilityEnabled()) {
            ctx.startActivity(m.accessibilitySettingsIntent())
        }
        PermissionCard("Display over apps", "Shows your budget bar, check-ins, and lessons over Instagram.", m.overlayAllowed()) {
            ctx.startActivity(m.overlaySettingsIntent())
        }
        PermissionCard("Notifications", "Tells you when new lessons are ready.", m.notificationsAllowed()) {
            ctx.startActivity(m.appInfoIntent())
        }
        ScreenUnderstandingCard(m)
        // How lessons may appear over Instagram, next to the other over-the-app permissions (moved from You).
        s?.let { settings ->
            ToggleRow(
                title = "Nudge for learning content",
                body = "Ask before playing a lesson over Reels. Turn off to play it automatically.",
                checked = settings.learningNudgeEnabled,
                onChange = { vm.setLearningNudgeEnabled(it) },
            )
        }
        Card {
            Text("Accessibility toggle greyed out?", style = MaterialTheme.typography.titleMedium)
            Text("Open App info → ⋮ → Allow restricted settings, then come back and turn on Accessibility.",
                style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
            Spacer(Modifier.height(8.dp))
            LearnButton("Open App info", { ctx.startActivity(m.appInfoIntent()) })
        }
    }
}

/**
 * Content understanding (ml/README.md): download the on-device models on Wi-Fi, then grant screen capture.
 * Android shows a persistent notification while capture runs and may ask again after a restart (US-07).
 */
@Composable
private fun ScreenUnderstandingCard(m: app.boomdoom.core.data.MonitoringGateway) {
    val ctx = LocalContext.current
    val active by m.captureActive.collectAsStateWithLifecycle()
    val download by remember { app.boomdoom.core.network.ModelDownloadWorker.progress(ctx) }
        .collectAsStateWithLifecycle(app.boomdoom.core.network.ModelDownloadState.IDLE to 0f)
    var installed by remember { mutableStateOf(m.modelsInstalled()) }
    LaunchedEffect(download.first) { installed = m.modelsInstalled() }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.let { if (r.resultCode == android.app.Activity.RESULT_OK) m.startScreenCapture(r.resultCode, it) }
    }
    val running = download.first == app.boomdoom.core.network.ModelDownloadState.RUNNING ||
        download.first == app.boomdoom.core.network.ModelDownloadState.WAITING
    Card {
        Text("Screen understanding", style = MaterialTheme.typography.titleMedium)
        Text("Recognises what you watch (food, finance, rage-bait…) entirely on this phone. Frames and captions are " +
            "analysed in memory and never stored or sent.", style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
        Spacer(Modifier.height(10.dp))
        when {
            !installed && running -> {
                Text(if (download.second > 0f) "Downloading models… ${(download.second * 100).toInt()}%"
                    else "Waiting for Wi-Fi to download models (~240 MB)…", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator({ download.second }, Modifier.fillMaxWidth(), color = BoomColors.LearnDeep)
            }
            !installed -> {
                if (download.first == app.boomdoom.core.network.ModelDownloadState.FAILED) {
                    Text("Download failed. Check your connection and try again.", style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD32F2F))
                }
                LearnButton("Download models (~240 MB, Wi-Fi)", { app.boomdoom.core.network.ModelDownloadWorker.start(ctx) })
            }
            !active -> LearnButton("Turn on", { consent.launch(m.screenCaptureIntent()) })
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", color = Color(0xFF2E9E50), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton({ m.stopScreenCapture() }) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, granted: Boolean, onFix: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
            }
            if (granted) Text("Enabled", color = Color(0xFF2E9E50), style = MaterialTheme.typography.labelLarge)
            else TextButton(onFix) { Text("Enable") }
        }
    }
}

// ------------------------------------------------------------------ About + demo mode (US-34)

@Composable
fun AboutScreen(onBack: () -> Unit, versionName: String, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    Page("About", onBack) {
        Card {
            Text("BoomDoom", style = MaterialTheme.typography.titleLarge)
            Text("Turn doom into boom.", style = MaterialTheme.typography.bodyMedium, color = BoomColors.InkSecondary)
            Spacer(Modifier.height(8.dp))
            Text("Version $versionName", style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary,
                modifier = Modifier.clickable {
                    taps++
                    if (taps == 7) {
                        vm.setDemo(!(s?.demoMode ?: false)); taps = 0
                        Toast.makeText(ctx, if (s?.demoMode == true) "Demo mode off" else "Demo mode on", Toast.LENGTH_SHORT).show()
                    }
                }.padding(vertical = 6.dp))
        }
        Card {
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            Text("Everything BoomDoom learns about your feed stays on this phone. The lesson service only receives your learning topics and lesson activity.",
                style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
        }
        if (s?.demoMode == true) {
            Card {
                Text("Demo mode", style = MaterialTheme.typography.titleLarge)
                Text("Simulate Reels without Instagram. Check-ins come every 3 reels; demo data is tagged and can be reset.",
                    style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LearnButton("+1 reel", { vm.simulate(1) })
                    LearnButton("+5 reels", { vm.simulate(5) })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ vm.leaveReels() }) { Text("Leave Reels") }
                    TextButton({ vm.resetDemo() }) { Text("Reset demo") }
                    TextButton({ vm.setDemo(false) }) { Text("Turn off") }
                }
            }
        }
    }
}
