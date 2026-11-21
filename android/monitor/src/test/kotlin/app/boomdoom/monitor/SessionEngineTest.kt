package app.boomdoom.monitor

import app.boomdoom.monitor.session.SessionEngine
import app.boomdoom.monitor.session.SessionEngine.CheckInAction
import app.boomdoom.monitor.session.SessionEngine.Effect
import app.boomdoom.monitor.session.SessionEngine.Input
import app.boomdoom.monitor.session.SessionEngine.State
import app.boomdoom.monitor.session.SessionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {
    private var t = 0L
    private fun SessionEngine.swipe(n: Int): List<Effect> =
        (1..n).flatMap { handle(Input.ReelTransition(++t * 1000)) }

    private fun List<Effect>.checkIns() = filterIsInstance<Effect.ShowCheckIn>().map { it.sessionReels }

    @Test fun checkInEvery15Reels() {
        val e = SessionEngine(SessionSettings())
        e.handle(Input.ReelsVisible(0))
        assertEquals(listOf(15, 30, 45), e.swipe(45).checkIns())
    }

    @Test fun twoContinuesInARowDoubleTheInterval() {
        val e = SessionEngine(SessionSettings())
        e.handle(Input.ReelsVisible(0))
        e.swipe(15); e.handle(Input.CheckInAnswered(t, CheckInAction.CONTINUE))
        e.swipe(15); e.handle(Input.CheckInAnswered(t, CheckInAction.CONTINUE))
        assertEquals(2, e.backoffMultiplier)
        assertEquals(listOf(60), e.swipe(30).checkIns())
    }

    @Test fun snoozeSilencesUntilSessionEnds() {
        val e = SessionEngine(SessionSettings())
        e.handle(Input.ReelsVisible(0))
        e.swipe(15); e.handle(Input.CheckInAnswered(t, CheckInAction.SNOOZE))
        assertTrue(e.swipe(60).checkIns().isEmpty())
        e.handle(Input.ScreenOff(t))
        e.handle(Input.ReelsVisible(t + 1))
        assertEquals(listOf(15), e.swipe(15).checkIns())
    }

    @Test fun budgetCardOnceThenOverBudgetInterval() {
        val e = SessionEngine(SessionSettings(budget = 20, checkInInterval = 15, overBudgetInterval = 25), todayReelsSoFar = 10)
        e.handle(Input.ReelsVisible(0))
        val effects = e.swipe(40)
        assertEquals(1, effects.filterIsInstance<Effect.ShowBudgetCard>().size)
        // budget crossed at session reel 10; next check-in 25 later
        assertEquals(listOf(35), effects.checkIns())
    }

    @Test fun overBudgetIntervalHasFloorOf10() {
        val e = SessionEngine(SessionSettings(budget = 1, overBudgetInterval = 3), todayReelsSoFar = 5)
        e.handle(Input.ReelsVisible(0))
        assertEquals(10, e.nextCheckInAt())
    }

    @Test fun pausedSessionEndsAfterTwoMinutes() {
        val e = SessionEngine(SessionSettings())
        e.handle(Input.ReelsVisible(0))
        e.handle(Input.ReelsHidden(1_000))
        assertTrue(e.handle(Input.Tick(60_000)).isEmpty())
        val ended = e.handle(Input.Tick(1_000 + SessionEngine.PAUSE_TIMEOUT_MS))
        assertEquals(SessionEngine.EndReason.TIMEOUT, (ended.single() as Effect.SessionEnded).reason)
        assertEquals(State.ENDED, e.state)
    }

    @Test fun returningWithinTwoMinutesResumesSameSession() {
        val e = SessionEngine(SessionSettings())
        e.handle(Input.ReelsVisible(0))
        e.swipe(5)
        e.handle(Input.ReelsHidden(t * 1000))
        val effects = e.handle(Input.ReelsVisible(t * 1000 + 30_000))
        assertEquals(State.ACTIVE, e.state)
        assertEquals(5, e.sessionReels)
        assertEquals(listOf(Effect.BarUpdated(5, 300)), effects)
    }
}
