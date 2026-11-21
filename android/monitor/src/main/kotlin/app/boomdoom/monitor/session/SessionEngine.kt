package app.boomdoom.monitor.session

/**
 * Pure Kotlin session state machine (Tech Spec §4.2). No Android dependencies, so it is fully
 * unit-testable. The caller feeds [Input]s and renders the returned [Effect]s.
 *
 *   IDLE --ReelsVisible--> ACTIVE --ReelsHidden--> PAUSED
 *   PAUSED --ReelsVisible (< 2 min)--> ACTIVE
 *   PAUSED --2 min / ScreenOff--> ENDED;  ACTIVE --DoneForNow / ScreenOff--> ENDED
 */
class SessionEngine(
    private val settings: SessionSettings,
    todayReelsSoFar: Int = 0,
) {
    enum class State { IDLE, ACTIVE, PAUSED, ENDED }

    sealed interface Input {
        val at: Long
        data class ReelsVisible(override val at: Long) : Input
        data class ReelsHidden(override val at: Long) : Input
        data class ReelTransition(override val at: Long) : Input
        data class ScreenOff(override val at: Long) : Input
        data class Tick(override val at: Long) : Input
        data class CheckInAnswered(override val at: Long, val action: CheckInAction) : Input
        data class DayRolledOver(override val at: Long) : Input
    }

    enum class CheckInAction { SWITCH_TO_LESSON, CONTINUE, DONE_FOR_NOW, SNOOZE }

    sealed interface Effect {
        data class SessionStarted(val at: Long) : Effect
        data class BarUpdated(val todayReels: Int, val budget: Int) : Effect
        data class ShowCheckIn(val sessionReels: Int) : Effect
        data class ShowBudgetCard(val todayReels: Int) : Effect
        data class SessionEnded(val at: Long, val sessionReels: Int, val reason: EndReason) : Effect
    }

    enum class EndReason { TIMEOUT, DONE, SCREEN_OFF }

    var state: State = State.IDLE
        private set
    var todayReels: Int = todayReelsSoFar
        private set
    var sessionReels: Int = 0
        private set
    var backoffMultiplier: Int = 1
        private set
    var snoozed: Boolean = false
        private set

    private var lastCheckInAtReel = 0
    private var consecutiveContinues = 0
    private var budgetCardShown = todayReelsSoFar >= settings.budget
    private var pausedAt: Long? = null

    val overBudget: Boolean get() = todayReels >= settings.budget

    fun nextCheckInAt(): Int {
        val base = if (overBudget) settings.overBudgetInterval.coerceAtLeast(MIN_OVER_BUDGET_INTERVAL)
        else settings.checkInInterval
        return lastCheckInAtReel + base * backoffMultiplier
    }

    fun handle(input: Input): List<Effect> = when (input) {
        is Input.ReelsVisible -> onVisible(input.at)
        is Input.ReelsHidden -> {
            if (state == State.ACTIVE) { state = State.PAUSED; pausedAt = input.at }
            emptyList()
        }
        is Input.ReelTransition -> onTransition(input.at)
        is Input.ScreenOff -> end(input.at, EndReason.SCREEN_OFF)
        is Input.Tick -> {
            val p = pausedAt
            if (state == State.PAUSED && p != null && input.at - p >= PAUSE_TIMEOUT_MS) end(input.at, EndReason.TIMEOUT)
            else emptyList()
        }
        is Input.CheckInAnswered -> onCheckIn(input)
        is Input.DayRolledOver -> {
            todayReels = 0
            budgetCardShown = false
            listOf(Effect.BarUpdated(todayReels, settings.budget))
        }
    }

    private fun onVisible(at: Long): List<Effect> = when (state) {
        State.IDLE, State.ENDED -> {
            startSession()
            state = State.ACTIVE
            listOf(Effect.SessionStarted(at), Effect.BarUpdated(todayReels, settings.budget))
        }
        State.PAUSED -> { state = State.ACTIVE; pausedAt = null; emptyList() }
        State.ACTIVE -> emptyList()
    }

    private fun onTransition(at: Long): List<Effect> {
        val effects = mutableListOf<Effect>()
        if (state != State.ACTIVE) effects += onVisible(at)
        sessionReels++
        todayReels++
        effects += Effect.BarUpdated(todayReels, settings.budget)

        if (!budgetCardShown && todayReels >= settings.budget) {
            // Budget crossing: show the budget card once, then use the over-budget interval.
            budgetCardShown = true
            lastCheckInAtReel = sessionReels
            effects += Effect.ShowBudgetCard(todayReels)
        } else if (!snoozed && sessionReels >= nextCheckInAt()) {
            lastCheckInAtReel = sessionReels
            effects += Effect.ShowCheckIn(sessionReels)
        }
        return effects
    }

    private fun onCheckIn(input: Input.CheckInAnswered): List<Effect> = when (input.action) {
        CheckInAction.CONTINUE -> {
            consecutiveContinues++
            if (consecutiveContinues >= 2) { backoffMultiplier *= 2; consecutiveContinues = 0 }
            emptyList()
        }
        CheckInAction.SNOOZE -> { snoozed = true; consecutiveContinues = 0; emptyList() }
        CheckInAction.SWITCH_TO_LESSON -> { consecutiveContinues = 0; emptyList() }
        CheckInAction.DONE_FOR_NOW -> end(input.at, EndReason.DONE)
    }

    private fun startSession() {
        sessionReels = 0
        lastCheckInAtReel = 0
        backoffMultiplier = 1
        consecutiveContinues = 0
        snoozed = false
        pausedAt = null
    }

    private fun end(at: Long, reason: EndReason): List<Effect> {
        if (state == State.IDLE || state == State.ENDED) return emptyList()
        state = State.ENDED
        return listOf(Effect.SessionEnded(at, sessionReels, reason))
    }

    companion object {
        const val PAUSE_TIMEOUT_MS = 2 * 60 * 1000L
        const val MIN_OVER_BUDGET_INTERVAL = 10
    }
}

/** Defaults from Tech Spec §1.2 / §4.2. */
data class SessionSettings(
    val budget: Int = 300,
    val checkInInterval: Int = 15,
    val overBudgetInterval: Int = 25,
)
