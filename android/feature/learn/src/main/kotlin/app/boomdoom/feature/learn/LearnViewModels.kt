package app.boomdoom.feature.learn

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.boomdoom.core.data.db.LearnedRecapEntity
import app.boomdoom.core.data.db.LessonCacheEntity
import app.boomdoom.core.data.db.TopicEntity
import app.boomdoom.core.data.repo.LearningRepository
import app.boomdoom.core.data.repo.PlayableLesson
import app.boomdoom.core.model.Interaction
import app.boomdoom.core.model.Level
import app.boomdoom.core.network.LessonSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------------ Learn tab

data class LearnUi(
    val topics: List<TopicEntity> = emptyList(),
    val filter: String? = null,
    val lessons: List<LessonCacheEntity> = emptyList(),
    val learned: List<LearnedRecapEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LearnViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val learning: LearningRepository,
) : ViewModel() {
    private val filter = MutableStateFlow<String?>(null)

    val ui: StateFlow<LearnUi> = combine(
        learning.observeTopics(), filter, filter.flatMapLatest { learning.observeUnwatched(it) }, learning.observeLearned(),
    ) { topics, f, lessons, learned -> LearnUi(topics, f, lessons, learned) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnUi())

    fun setFilter(topicId: String?) { filter.value = topicId }
    fun refresh() = LessonSyncScheduler.syncNow(ctx)
    fun topicName(topicId: String) = ui.value.topics.firstOrNull { it.id == topicId }?.name ?: ""
}

// ------------------------------------------------------------------ My topics (L7)

@HiltViewModel
class TopicsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val learning: LearningRepository,
) : ViewModel() {
    val topics = learning.observeTopics().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, level: Level, goal: String?) = viewModelScope.launch {
        learning.addTopic(name, level, goal)
        LessonSyncScheduler.syncNow(ctx)
    }

    fun update(id: String, level: Level, goal: String?) = viewModelScope.launch {
        learning.updateTopic(id, level, goal)
        LessonSyncScheduler.syncNow(ctx)
    }

    fun remove(id: String) = viewModelScope.launch {
        learning.removeTopic(id)
        LessonSyncScheduler.syncNow(ctx)
    }
}

// ------------------------------------------------------------------ Learned (L6)

@HiltViewModel
class LearnedViewModel @Inject constructor(private val learning: LearningRepository) : ViewModel() {
    val learned = learning.observeLearned().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val topics = learning.observeTopics().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun canRewatch(lessonId: String): Boolean = learning.playable(lessonId)?.source != null
    fun remove(id: String) = viewModelScope.launch { learning.removeRecap(id) }
}

// ------------------------------------------------------------------ Player (L2–L5, L9)

sealed interface PlayerPhase {
    data object Loading : PlayerPhase
    data object Missing : PlayerPhase
    data object Playing : PlayerPhase
    data class Asking(val interaction: Interaction, val answer: Int? = null) : PlayerPhase
    data object Recap : PlayerPhase
}

data class PlayerUi(
    val lesson: PlayableLesson? = null,
    val phase: PlayerPhase = PlayerPhase.Loading,
    val nextLessonId: String? = null,
    val fromCheckIn: Boolean = false,
    val reported: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val learning: LearningRepository,
) : ViewModel() {
    private val lessonId: String = checkNotNull(handle["lessonId"])
    private val _ui = MutableStateFlow(PlayerUi(fromCheckIn = handle.get<String>("from") == "checkin"))
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val lesson = learning.playable(lessonId)
            if (lesson?.source == null) { _ui.update { it.copy(phase = PlayerPhase.Missing) }; return@launch }
            _ui.update { it.copy(lesson = lesson, phase = PlayerPhase.Playing) }
            learning.started(lesson)
        }
    }

    fun onInteractionPoint(i: Interaction) = _ui.update { it.copy(phase = PlayerPhase.Asking(i)) }

    fun answer(index: Int) {
        val phase = _ui.value.phase as? PlayerPhase.Asking ?: return
        if (phase.answer != null) return
        _ui.update { it.copy(phase = phase.copy(answer = index)) }
        val lesson = _ui.value.lesson ?: return
        val correct = phase.interaction.answerIndex?.let { it == index }
        viewModelScope.launch { learning.answered(lesson, phase.interaction.id, index, correct) }
    }

    fun resume() = _ui.update { it.copy(phase = PlayerPhase.Playing) }

    fun onEnded() {
        val lesson = _ui.value.lesson ?: return
        viewModelScope.launch {
            learning.completed(lesson) // idempotent (US-18)
            val next = learning.nextLessonId(lesson.row.topicId, excluding = lesson.row.lessonId)
            _ui.update { it.copy(phase = PlayerPhase.Recap, nextLessonId = next) }
        }
    }

    fun report(reason: String, note: String?, positionS: Double) {
        val lesson = _ui.value.lesson ?: return
        viewModelScope.launch { learning.report(lesson, reason, note, positionS) }
        _ui.update { it.copy(reported = true) }
    }

    fun clearReported() = _ui.update { it.copy(reported = false) }
}
