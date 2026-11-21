package app.boomdoom.overlay

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the overlays shown on top of the reels app (Tech Spec §4.3). Must be called on the main thread.
 * Every show* returns false when the overlay permission is missing; counting continues regardless.
 */
class OverlayManager(private val ctx: Context) {
    private val bar = OverlayWindow(ctx, interactive = false, windowGravity = TOP)
    private val flag = OverlayWindow(ctx, interactive = false, windowGravity = TOP)
    private val card = OverlayWindow(ctx, interactive = true, windowGravity = CENTER)
    private val reels = mutableIntStateOf(0)
    private val budget = mutableIntStateOf(300)
    private val scope = MainScope()
    private var flagJob: Job? = null

    val cardShowing: Boolean get() = card.isShowing

    fun showBar(todayReels: Int, dailyBudget: Int): Boolean {
        updateBar(todayReels, dailyBudget)
        return bar.show { BudgetBar(reels.intValue, budget.intValue) }
    }

    fun updateBar(todayReels: Int, dailyBudget: Int) {
        reels.intValue = todayReels
        budget.intValue = dailyBudget
    }

    fun hideBar() = bar.hide()

    fun showFlag(label: String, millis: Long = 2500) {
        flagJob?.cancel()
        flag.hide()
        if (!flag.show { FlagTag(label) }) return
        flagJob = scope.launch { delay(millis); flag.hide() }
    }

    fun showCheckIn(ui: CheckInUi, onSwitch: () -> Unit, onContinue: () -> Unit, onDone: () -> Unit, onSnooze: () -> Unit): Boolean {
        card.hide()
        return card.show {
            CheckInCard(ui, onSwitch = { card.hide(); onSwitch() }, onContinue = { card.hide(); onContinue() },
                onDone = { card.hide(); onDone() }, onSnooze = { card.hide(); onSnooze() })
        }
    }

    fun showBudget(reelsToday: Int, dailyBudget: Int, lessonTopic: String?, onLesson: () -> Unit, onKeep: () -> Unit,
                   onFinish: () -> Unit): Boolean {
        card.hide()
        return card.show {
            BudgetCard(reelsToday, dailyBudget, lessonTopic, onLesson = { card.hide(); onLesson() },
                onKeep = { card.hide(); onKeep() }, onFinish = { card.hide(); onFinish() })
        }
    }

    fun hideCard() = card.hide()

    fun hideAll() {
        flagJob?.cancel()
        bar.hide(); flag.hide(); card.hide()
    }
}
