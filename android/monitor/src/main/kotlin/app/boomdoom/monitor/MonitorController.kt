package app.boomdoom.monitor

import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.boomdoom.core.data.repo.LearningRepository
import app.boomdoom.core.data.repo.PlayableLesson
import app.boomdoom.core.data.repo.TrackingRepository
import app.boomdoom.core.data.settings.SettingsRepository
import app.boomdoom.core.data.settings.UserSettings
import app.boomdoom.core.model.BuiltInLabel
import app.boomdoom.core.model.LiveState
import app.boomdoom.monitor.session.SessionEngine
import app.boomdoom.monitor.session.SessionEngine.CheckInAction
import app.boomdoom.monitor.session.SessionEngine.Effect
import app.boomdoom.monitor.session.SessionEngine.Input
import app.boomdoom.monitor.session.SessionSettings
import app.boomdoom.monitor.watcher.WatcherEvent
import app.boomdoom.overlay.CheckInUi
import app.boomdoom.overlay.OverlayManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Glue between the accessibility watcher, the pure [SessionEngine], local storage and the overlays.
 * All events go through one channel and are handled sequentially on the main thread.
 */
@Singleton
class MonitorController @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settingsRepo: SettingsRepository,
    private val tracking: TrackingRepository,
    private val learning: LearningRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val events = Channel<WatcherEvent>(Channel.UNLIMITED)
    private val overlays by lazy { OverlayManager(ctx, learning) }
    internal val lessonShowing: Boolean get() = overlays.lessonShowing

    private var settings = UserSettings()
    private var engine: SessionEngine? = null
    private var engineDay: String? = null
    private var sessionId: String? = null
    private var sessionDay: String? = null
    private var reelsVisible = false

    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()

    init {
        scope.launch { settingsRepo.settings.collect { onSettings(it) } }
        scope.launch { for (e in events) runCatching { handle(e) } }
        scope.launch { while (true) { delay(15_000); events.trySend(WatcherEvent.Tick(System.currentTimeMillis())) } }
    }

    fun submit(e: WatcherEvent) { events.trySend(e) }

    fun setConnected(connected: Boolean) {
        _live.value = _live.value.copy(connected = connected)
        if (!connected) submit(WatcherEvent.ScreenOff(System.currentTimeMillis()))
    }

    /** Demo mode (US-34): simulate entering Reels and swiping, so the whole loop can be shown without Instagram. */
    fun simulateReels(count: Int = 1) {
        val now = System.currentTimeMillis()
        submit(WatcherEvent.ReelsVisible(now))
        repeat(count) { submit(WatcherEvent.ReelTransition(now + it)) }
    }

    fun simulateLeaveReels() = submit(WatcherEvent.ReelsHidden(System.currentTimeMillis()))

    // ------------------------------------------------------------------ internals

    private fun active(s: UserSettings = settings) = s.onboardingDone && !s.monitoringPaused

    private suspend fun onSettings(s: UserSettings) {
        val wasActive = active()
        val goalsChanged = goals(settings) != goals(s)
        settings = s
        if (wasActive && !active(s)) {
            // US-10: pausing stops monitoring and removes overlays immediately
            reelsVisible = false
            overlays.hideAll()
            endSession(System.currentTimeMillis(), SessionEngine.EndReason.DONE)
        }
        // Only goal changes rebuild the engine; unrelated settings (e.g. sync flags) must not reset
        // the session's back-off and snooze state.
        if (goalsChanged) engine = null
    }

    private fun goals(s: UserSettings) =
        listOf(s.budget, s.checkInInterval, s.overBudgetInterval, s.backoffEnabled, s.resetHour, s.resetMinute, s.demoMode)

    private suspend fun engineFor(now: Long): SessionEngine {
        val day = tracking.dayKey(settings, now)
        if (engine == null || engineDay != day) {
            if (engineDay != null && engineDay != day) endSession(now, SessionEngine.EndReason.TIMEOUT)
            engine = SessionEngine(
                SessionSettings(
                    // Demo mode (Tech Spec §14.3): check-ins every 3 reels so the loop is quick to show.
                    budget = settings.budget, checkInInterval = if (settings.demoMode) 3 else settings.checkInInterval,
                    overBudgetInterval = settings.overBudgetInterval, backoffEnabled = settings.backoffEnabled,
                ),
                todayReelsSoFar = tracking.reelsToday(),
            )
            engineDay = day
            if (sessionId != null) engine!!.handle(Input.ReelsVisible(now)) // keep the open session going
        }
        return engine!!
    }

    private suspend fun handle(e: WatcherEvent) {
        if (e is WatcherEvent.ReelsHidden || e is WatcherEvent.ScreenOff) {
            reelsVisible = false
            overlays.hideAll()
        }
        if (!active()) return
        if (e is WatcherEvent.ReelTransition && lessonShowing) return
        val eng = engineFor(e.at)
        val input = when (e) {
            is WatcherEvent.ReelsVisible -> { reelsVisible = true; Input.ReelsVisible(e.at) }
            is WatcherEvent.ReelsHidden -> Input.ReelsHidden(e.at)
            is WatcherEvent.ReelTransition -> { reelsVisible = true; Input.ReelTransition(e.at) }
            is WatcherEvent.ScreenOff -> Input.ScreenOff(e.at)
            is WatcherEvent.Tick -> Input.Tick(e.at)
            is WatcherEvent.CaptionRead -> return
        }
        val effects = eng.handle(input)
        if (e is WatcherEvent.ReelTransition) recordReel(e.at)
        apply(effects)
        _live.value = _live.value.copy(reelsVisible = reelsVisible, sessionReels = eng.sessionReels)
    }

    private suspend fun recordReel(at: Long) {
        val sid = sessionId ?: return
        // Classification needs screen understanding (not in the POC yet): every reel is "other" until then.
        tracking.recordReel(sid, sessionDay ?: tracking.dayKey(settings, at), startedAt = at, watchMs = 0,
            label = BuiltInLabel.OTHER, confidence = 0f, flagged = false, signals = 0, budget = settings.budget,
            demo = settings.demoMode)
    }

    private suspend fun apply(effects: List<Effect>) {
        for (fx in effects) when (fx) {
            is Effect.SessionStarted -> {
                sessionDay = tracking.dayKey(settings, fx.at)
                sessionId = tracking.startSession(INSTAGRAM, fx.at, sessionDay!!)
            }
            is Effect.BarUpdated -> if (reelsVisible && !lessonShowing) overlays.showBar(fx.todayReels, fx.budget)
            is Effect.ShowCheckIn -> showCheckIn(fx.sessionReels)
            is Effect.ShowBudgetCard -> showBudget(fx.todayReels)
            is Effect.SessionEnded -> {
                sessionId?.let { tracking.endSession(it, fx.at, fx.reason.name.lowercase()) }
                sessionId = null
                overlays.hideAll()
            }
        }
    }

    private suspend fun endSession(at: Long, reason: SessionEngine.EndReason) {
        val sid = sessionId ?: return
        tracking.endSession(sid, at, reason.name.lowercase())
        sessionId = null
        engine = null
    }

    private suspend fun suggestion(): PlayableLesson? = learning.nextLessonId()
        ?.let { learning.playable(it) }?.takeIf { it.source != null }

    private suspend fun showLearningIntervention(showCard: (PlayableLesson?) -> Unit) {
        if (!active() || !reelsVisible || lessonShowing) return
        val lesson = suggestion()
        if (!active() || !reelsVisible || lessonShowing) return
        if (settings.learningNudgeEnabled) {
            showCard(lesson)
        } else if (lesson != null) {
            showLesson(lesson, requestedByUser = false)
        }
    }

    private suspend fun showCheckIn(sessionReels: Int) {
        if (overlays.cardShowing) return
        showLearningIntervention { lesson ->
            overlays.showCheckIn(
                CheckInUi(sessionReels, breakdown = emptyList(), lessonTopic = lesson?.topicName),
                onSwitch = { if (lesson != null) showLesson(lesson, requestedByUser = true) },
                onContinue = { answer(CheckInAction.CONTINUE) },
                onDone = { answer(CheckInAction.DONE_FOR_NOW); goHome() },
                onSnooze = { answer(CheckInAction.SNOOZE) },
            )
        }
    }

    private suspend fun showBudget(todayReels: Int) {
        showLearningIntervention { lesson ->
            overlays.showBudget(
                todayReels, settings.budget, lesson?.topicName,
                onLesson = { if (lesson != null) showLesson(lesson, requestedByUser = true) },
                onKeep = { /* keep scrolling: over-budget interval applies from here */ },
                onFinish = { answer(CheckInAction.DONE_FOR_NOW); goHome() },
            )
        }
    }

    private fun answer(action: CheckInAction) {
        scope.launch {
            val eng = engine ?: return@launch
            apply(eng.handle(Input.CheckInAnswered(System.currentTimeMillis(), action)))
        }
    }

    private fun showLesson(lesson: PlayableLesson, requestedByUser: Boolean) {
        if (!active() || !reelsVisible || lessonShowing) return
        val shown = overlays.showLesson(lesson) {
            if (active() && reelsVisible) engine?.let { overlays.showBar(it.todayReels, settings.budget) }
        }
        if (shown) {
            answer(CheckInAction.SWITCH_TO_LESSON)
        } else if (requestedByUser) {
            Toast.makeText(ctx, "Couldn't show the lesson over this screen.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goHome() {
        overlays.hideAll()
        ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        const val INSTAGRAM = "com.instagram.android"
    }
}
