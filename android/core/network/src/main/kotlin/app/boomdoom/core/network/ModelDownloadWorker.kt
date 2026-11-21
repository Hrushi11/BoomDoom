package app.boomdoom.core.network

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.boomdoom.core.data.ModelFiles
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads the on-device classification models (Tech Spec §5.9, ~240 MB) on an unmetered network with the
 * battery not low. Each file goes to a .part file, is SHA-256 verified, then moved into place, so a partial or
 * corrupt download is never loaded. Models are not user data; no feed data is sent.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val api: BoomDoomApi,
    private val models: ModelFiles,
) : CoroutineWorker(ctx, params) {

    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val manifest = runCatching { api.models() }.getOrElse { return@withContext retryOrFail(it) }
        if (manifest.files.isEmpty()) return@withContext Result.failure(workDataOf("error" to "no models published"))
        if (manifest.version != null && manifest.version == models.version() && models.installed()) {
            return@withContext Result.success()
        }
        models.dir.mkdirs()
        var done = 0L
        for (f in manifest.files) {
            val target = File(models.dir, f.name)
            if (target.isFile && target.length() == f.size && sha256(target) == f.sha256) { done += f.size; continue }
            val part = File(models.dir, "${f.name}.part")
            val ok = runCatching {
                http.newCall(Request.Builder().url(f.url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code} for ${f.name}")
                    part.outputStream().use { out ->
                        val input = resp.body!!.byteStream()
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            if (isStopped) error("stopped")
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            setProgress(workDataOf("done" to done, "total" to manifest.totalBytes))
                        }
                    }
                }
                check(sha256(part) == f.sha256) { "checksum mismatch for ${f.name}" }
                check(part.renameTo(target)) { "could not move ${f.name}" }
            }
            if (ok.isFailure) {
                part.delete()
                return@withContext retryOrFail(ok.exceptionOrNull()!!)
            }
        }
        manifest.version?.let { File(models.dir, ModelFiles.VERSION_FILE).writeText(it) }
        Result.success()
    }

    private fun retryOrFail(e: Throwable): Result {
        Log.w(TAG, "model download failed: ${e.message}")
        return if (runAttemptCount < 5) Result.retry() else Result.failure(workDataOf("error" to (e.message ?: "failed")))
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ModelDownload"
        private const val WORK = "model-download"

        fun start(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, req)
        }

        /** Download state + fraction (0..1) for the UI, without exposing WorkManager types. */
        fun progress(ctx: Context): Flow<Pair<ModelDownloadState, Float>> =
            WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(WORK).map { infos ->
                val info = infos.lastOrNull()
                val done = info?.progress?.getLong("done", 0L) ?: 0L
                val total = info?.progress?.getLong("total", 0L) ?: 0L
                val state = when (info?.state) {
                    null, WorkInfo.State.CANCELLED -> ModelDownloadState.IDLE
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ModelDownloadState.WAITING
                    WorkInfo.State.RUNNING -> ModelDownloadState.RUNNING
                    WorkInfo.State.SUCCEEDED -> ModelDownloadState.DONE
                    WorkInfo.State.FAILED -> ModelDownloadState.FAILED
                }
                state to if (total > 0) done.toFloat() / total else 0f
            }
    }
}

enum class ModelDownloadState { IDLE, WAITING, RUNNING, DONE, FAILED }
