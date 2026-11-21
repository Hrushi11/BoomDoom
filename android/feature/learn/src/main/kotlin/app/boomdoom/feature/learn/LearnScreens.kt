package app.boomdoom.feature.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.boomdoom.core.data.db.LessonCacheEntity
import app.boomdoom.core.data.db.TopicEntity
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.CardShape
import app.boomdoom.core.designsystem.Illus
import app.boomdoom.core.designsystem.Illustration
import app.boomdoom.core.designsystem.LearnButton
import app.boomdoom.core.designsystem.PaperTheme
import app.boomdoom.core.designsystem.PillShape
import app.boomdoom.core.designsystem.PrimaryButton
import app.boomdoom.core.designsystem.TextLink
import app.boomdoom.core.model.Level
import app.boomdoom.core.model.PresetTopic
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun durationLabel(s: Double) = if (s >= 55) "${(s / 60).toInt().coerceAtLeast(1)} min" else "${s.toInt()} sec"

private fun illustrationFor(topic: TopicEntity?): Int =
    topic?.presetId?.let { Illus.topics[it] } ?: Illus.homeLesson

@Composable
private fun PaperCardBox(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier.clip(CardShape).background(BoomColors.PaperCard).border(1.dp, BoomColors.PaperBorder, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

// ------------------------------------------------------------------ L1 Learn tab

@Composable
fun LearnScreen(onPlay: (String) -> Unit, onOpenTopics: () -> Unit, onOpenLearned: () -> Unit, vm: LearnViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val topicsById = ui.topics.associateBy { it.id }
    PaperTheme {
        Column(Modifier.fillMaxSize().background(BoomColors.Paper).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Learn", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh lessons") }
                IconButton(onOpenTopics) { Icon(Icons.Filled.Tune, "My topics") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("All", ui.filter == null) { vm.setFilter(null) }
                ui.topics.forEach { t ->
                    FilterChip(PresetTopic.byId(t.presetId ?: "")?.shortName ?: t.name, ui.filter == t.id,
                        preparing = t.status == "preparing" && ui.lessons.none { it.topicId == t.id }) { vm.setFilter(t.id) }
                }
            }
            val upNext = ui.lessons.firstOrNull()
            if (upNext == null) {
                EmptyLearn(ui.topics.isEmpty(), onOpenTopics)
            } else {
                val topic = topicsById[upNext.topicId]
                PaperCardBox(Modifier.fillMaxWidth().height(210.dp), onClick = { onPlay(upNext.lessonId) }) {
                    Row(Modifier.fillMaxSize().padding(16.dp)) {
                        Column(Modifier.weight(1.2f)) {
                            Text("Up next · ${topic?.name ?: ""} · ${Level.of(upNext.level).label} · ${durationLabel(upNext.durationS)}",
                                style = MaterialTheme.typography.labelSmall, color = BoomColors.LearnDeep, maxLines = 2)
                            Spacer(Modifier.height(8.dp))
                            Text(upNext.concept, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            LearnButton("Play", { onPlay(upNext.lessonId) }, leading = { Icon(Icons.Filled.PlayArrow, null) })
                        }
                        Illustration(illustrationFor(topic), Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
            val more = ui.lessons.drop(1)
            if (more.isNotEmpty()) {
                SectionTitle("More short lessons")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    more.take(10).forEach { LessonTile(it, topicsById[it.topicId]) { onPlay(it.lessonId) } }
                }
            }
            if (ui.learned.isNotEmpty()) {
                SectionTitle("Learned", action = "See all", onAction = onOpenLearned)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ui.learned.take(6).forEach { r ->
                        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFE2F3EE)).clickable(onClick = onOpenLearned)
                            .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = BoomColors.LearnDeep)
                            Spacer(Modifier.width(8.dp))
                            Text(r.concept, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, preparing: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(PillShape).background(if (selected) Color(0xFF7EDFCD) else Color.White)
            .border(1.dp, if (selected) Color(0xFF7EDFCD) else Color(0xFF9A958B), PillShape)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (preparing) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = BoomColors.LearnDeep)
            Spacer(Modifier.width(6.dp))
        }
        Text(text + if (preparing) " · Preparing" else "", style = MaterialTheme.typography.labelMedium, color = BoomColors.Ink)
    }
}

@Composable
private fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            Row(Modifier.clickable(onClick = onAction), verticalAlignment = Alignment.CenterVertically) {
                Text(action, style = MaterialTheme.typography.labelMedium, color = BoomColors.LearnDeep)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = BoomColors.LearnDeep, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LessonTile(l: LessonCacheEntity, topic: TopicEntity?, onClick: () -> Unit) {
    PaperCardBox(Modifier.width(150.dp).height(170.dp), onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Illustration(illustrationFor(topic), Modifier.fillMaxWidth().height(72.dp))
            Spacer(Modifier.height(6.dp))
            Text(l.concept, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${topic?.name ?: ""} · ${durationLabel(l.durationS)}", style = MaterialTheme.typography.bodySmall,
                    color = BoomColors.InkSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.PlayCircle, null, tint = BoomColors.Learn)
            }
        }
    }
}

@Composable
private fun EmptyLearn(noTopics: Boolean, onOpenTopics: () -> Unit) {
    PaperCardBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Illustration(Illus.homeLesson, Modifier.fillMaxWidth().height(140.dp))
            Text(if (noTopics) "Pick a topic to start learning" else "Preparing your lessons…", style = MaterialTheme.typography.titleLarge)
            Text(if (noTopics) "Add topics you're curious about." else "New lessons usually take a few minutes. We'll show them here.",
                style = MaterialTheme.typography.bodyMedium, color = BoomColors.InkSecondary)
            Spacer(Modifier.height(12.dp))
            LearnButton(if (noTopics) "Choose topics" else "My topics", onOpenTopics)
        }
    }
}

// ------------------------------------------------------------------ L6 Learned

private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@Composable
fun LearnedScreen(onBack: () -> Unit, onRewatch: (String) -> Unit, vm: LearnedViewModel = hiltViewModel()) {
    val learned by vm.learned.collectAsStateWithLifecycle()
    val topics by vm.topics.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val shown = learned.filter { filter == null || it.topicId == filter }
    PaperTheme {
        Column(Modifier.fillMaxSize().background(BoomColors.Paper).statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Learned", style = MaterialTheme.typography.headlineLarge)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("All", filter == null) { filter = null }
                topics.filter { t -> learned.any { it.topicId == t.id } }.forEach { t -> FilterChip(t.name, filter == t.id) { filter = t.id } }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Finish a lesson to save your first takeaway.", style = MaterialTheme.typography.titleMedium,
                        color = BoomColors.InkSecondary)
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.lessonId }) { r ->
                    val open = expanded == r.lessonId
                    var canRewatch by remember(r.lessonId) { mutableStateOf(false) }
                    LaunchedEffect(r.lessonId) { canRewatch = vm.canRewatch(r.lessonId) }
                    PaperCardBox(Modifier.fillMaxWidth(), onClick = { expanded = if (open) null else r.lessonId }) {
                        Column(Modifier.padding(14.dp)) {
                            Row {
                                Illustration(illustrationFor(topics.firstOrNull { it.id == r.topicId }), Modifier.size(84.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.concept, style = MaterialTheme.typography.titleMedium)
                                    Text(r.recap, style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary,
                                        maxLines = if (open) 6 else 2, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.CalendarMonth, null, Modifier.size(14.dp), tint = BoomColors.InkSecondary)
                                        Spacer(Modifier.width(4.dp))
                                        Text(dateFmt.format(Instant.ofEpochMilli(r.savedAt).atZone(ZoneId.systemDefault())),
                                            style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary)
                                    }
                                }
                                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = BoomColors.LearnDeep)
                            }
                            if (open) {
                                Spacer(Modifier.height(10.dp))
                                if (canRewatch) LearnButton("Rewatch lesson", { onRewatch(r.lessonId) }, Modifier.fillMaxWidth(),
                                    leading = { Icon(Icons.Filled.PlayArrow, null) })
                                TextLink("Remove from Learned", { vm.remove(r.lessonId) }, modifier = Modifier.align(Alignment.CenterHorizontally))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ L7 My topics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(onBack: () -> Unit, vm: TopicsViewModel = hiltViewModel()) {
    val topics by vm.topics.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TopicEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    PaperTheme {
        Column(Modifier.fillMaxSize().background(BoomColors.Paper).statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("My topics", style = MaterialTheme.typography.headlineLarge)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(topics, key = { it.id }) { t ->
                    PaperCardBox(Modifier.fillMaxWidth(), onClick = { editing = t }) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Illustration(illustrationFor(t), Modifier.size(width = 64.dp, height = 44.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                LevelBadge(Level.of(t.level))
                                Icon(Icons.Filled.ChevronRight, null)
                            }
                            t.goal?.let { Text("Goal: $it", style = MaterialTheme.typography.bodySmall, color = BoomColors.InkSecondary) }
                            when {
                                t.status == "failed" -> Text("Couldn't prepare lessons. Tap to edit or try again later.",
                                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFD32F2F))
                                t.status == "preparing" && t.backendId != null -> {
                                    Text("Preparing lessons…", style = MaterialTheme.typography.labelSmall, color = BoomColors.InkSecondary)
                                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp), color = BoomColors.Warning)
                                }
                                t.backendId == null -> Text("Waiting to sync…", style = MaterialTheme.typography.labelSmall,
                                    color = BoomColors.InkSecondary)
                            }
                        }
                    }
                }
            }
            // Teal like every other action on the paper (learning) screens; coral is the dark Home accent.
            LearnButton("Add a topic", { adding = true }, Modifier.fillMaxWidth().padding(vertical = 12.dp),
                leading = { Icon(Icons.Filled.Add, null) })
        }
        if (adding || editing != null) {
            val t = editing
            ModalBottomSheet(onDismissRequest = { adding = false; editing = null }, containerColor = BoomColors.Paper) {
                TopicForm(
                    initial = t,
                    onSave = { name, level, goal ->
                        if (t == null) vm.add(name, level, goal) else vm.update(t.id, level, goal)
                        adding = false; editing = null
                    },
                    onRemove = t?.let { { vm.remove(it.id); editing = null } },
                )
            }
        }
    }
}

@Composable
private fun LevelBadge(level: Level) {
    val bg = when (level) { Level.BEGINNER -> Color(0xFFD7F0DE); Level.INTERMEDIATE -> Color(0xFFE5DEFA); Level.ADVANCED -> Color(0xFFFFE0C2) }
    Text(level.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clip(PillShape).background(bg)
        .padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun TopicForm(initial: TopicEntity?, onSave: (String, Level, String?) -> Unit, onRemove: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var level by remember { mutableStateOf(initial?.let { Level.of(it.level) } ?: Level.BEGINNER) }
    var goal by remember { mutableStateOf(initial?.goal ?: "") }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(if (initial == null) "Add a topic" else initial.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (initial == null) {
            Text("Topic name", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. System Design") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
        }
        Text("Difficulty level", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Level.entries.forEach { l ->
                val sel = l == level
                Box(Modifier.weight(1f).clip(PillShape).background(if (sel) Color(0xFFCDEFE8) else Color.White)
                    .border(if (sel) 2.dp else 1.dp, if (sel) BoomColors.LearnDeep else Color(0xFF9A958B), PillShape)
                    .clickable(role = Role.RadioButton) { level = l }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text((if (sel) "✓ " else "") + l.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text("Goal (optional)", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(goal, { goal = it.take(200) }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Prepare for backend interviews") })
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (initial == null) "Create topic" else "Save", { onSave(name, level, goal.ifBlank { null }) },
            enabled = initial != null || name.isNotBlank())
        if (onRemove != null) TextLink("Remove topic", onRemove, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

