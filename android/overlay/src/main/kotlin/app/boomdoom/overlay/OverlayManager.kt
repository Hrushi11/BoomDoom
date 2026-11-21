package app.boomdoom.overlay

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import app.boomdoom.core.data.repo.LearningRepository
import app.boomdoom.core.data.repo.PlayableLesson
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the overlays shown on top of the reels app (Tech Spec §4.3). Must be called on the main thread.
 * Every show* returns false when the overlay permission is missing; counting continues regardless.
 */
class OverlayManager(ctx: Context, private val learning: LearningRepository) {
    private val bar = OverlayWindow(ctx, interactive = false, windowGravity = TOP)
    private val flag = OverlayWindow(ctx, interactive = false, windowGravity = TOP)
    private val captureChip = OverlayWindow(ctx, interactive = false, windowGravity = TOP_END, chip = true)
    private val card = OverlayWindow(ctx, interactive = true, windowGravity = CENTER)
    private val lesson = OverlayWindow(ctx, interactive = true, windowGravity = CENTER)
    private val reels = mutableIntStateOf(0)
    private val budget = mutableIntStateOf(300)
    private val scope = MainScope()
    private var flagJob: Job? = null

    val cardShowing: Boolean get() = card.isShowing
    val lessonShowing: Boolean get() = lesson.isShowing

    fun showBar(todayReels: Int, dailyBudget: Int): Boolean {
        updateBar(todayReels, dailyBudget)
        return bar.show { BudgetBar(reels.intValue, budget.intValue) }
    }

    fun updateBar(todayReels: Int, dailyBudget: Int) {
        reels.intValue = todayReels
        budget.intValue = dailyBudget
    }

    /** "Turn on" under the budget bar while screen understanding is off (Android ends it on every restart). */
    fun showCaptureChip(onTap: () -> Unit): Boolean = captureChip.show { CaptureChip(onTap) }

    fun hideCaptureChip() = captureChip.hide()

    fun showFlag(label: String, millis: Long = 2500) {
        flagJob?.cancel()
        flag.hide()
        if (!flag.show { FlagTag(label) }) return
        flagJob = scope.launch { delay(millis); flag.hide() }
    }

    fun showCheckIn(ui: CheckInUi, onSwitch: () -> Unit, onContinue: () -> Unit, onDone: () -> Unit, onSnooze: () -> Unit): Boolean {
        card.hide()
        return card.show {
            CheckInCard(ui, onSwitch = onSwitch, onContinue = { card.hide(); onContinue() },
                onDone = { card.hide(); onDone() }, onSnooze = { card.hide(); onSnooze() })
        }
    }

    fun showBudget(reelsToday: Int, dailyBudget: Int, lessonTopic: String?, onLesson: () -> Unit, onKeep: () -> Unit,
                   onFinish: () -> Unit): Boolean {
        card.hide()
        return card.show {
            BudgetCard(reelsToday, dailyBudget, lessonTopic, onLesson = onLesson,
                onKeep = { card.hide(); onKeep() }, onFinish = { card.hide(); onFinish() })
        }
    }

    fun showLesson(playable: PlayableLesson, onDismiss: () -> Unit): Boolean {
        if (lessonShowing) return false
        val dismiss = {
            if (lessonShowing) {
                lesson.hide()
                onDismiss()
            }
        }
        if (!lesson.show(onBack = dismiss) { ReelLessonPlayer(playable, learning, scope, dismiss) }) return false
        card.hide()
        bar.hide()
        flagJob?.cancel()
        flag.hide()
        return true
    }

    fun hideAll() {
        captureChip.hide()
        flagJob?.cancel()
        bar.hide(); flag.hide(); card.hide(); lesson.hide()
    }
}
