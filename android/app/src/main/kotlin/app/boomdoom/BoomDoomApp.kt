package app.boomdoom

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.boomdoom.core.data.BundledLessons
import app.boomdoom.core.data.repo.LearningRepository
import app.boomdoom.core.data.repo.TrackingRepository
import app.boomdoom.core.data.settings.SettingsRepository
import app.boomdoom.core.network.AuthStore
import app.boomdoom.core.network.LessonSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BoomDoomApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var learning: LearningRepository
    @Inject lateinit var tracking: TrackingRepository
    @Inject lateinit var auth: AuthStore
    @Inject lateinit var models: app.boomdoom.core.data.ModelFiles

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            tracking.applyRetention()                             // Tech Spec §6.2
            learning.importBundled(BundledLessons.load(this@BoomDoomApp)) // starter lessons for chosen topics
            if (settings.current().onboardingDone) {
                LessonSyncScheduler.schedulePeriodic(this@BoomDoomApp)
                LessonSyncScheduler.syncNow(this@BoomDoomApp)
                if (!models.installed()) app.boomdoom.core.network.ModelDownloadWorker.start(this@BoomDoomApp)
            }
        }
    }
}
