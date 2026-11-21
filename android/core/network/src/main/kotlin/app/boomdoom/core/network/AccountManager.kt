package app.boomdoom.core.network

import android.content.Context
import androidx.work.WorkManager
import app.boomdoom.core.data.LocalData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** US-29: delete the account on the backend, then wipe everything on this phone. */
@Singleton
class AccountManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val api: BoomDoomApi,
    private val auth: AuthStore,
    private val local: LocalData,
) {
    /** @return true when the backend accepted the deletion (or there was never a backend account). */
    suspend fun deleteAccount(): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.userId
        if (userId != null) {
            val ok = runCatching { api.deleteAccount(userId) }.isSuccess
            if (!ok) return@withContext false
        }
        WorkManager.getInstance(ctx).cancelAllWork()
        local.wipe()
        auth.clear()
        File(ctx.filesDir, "lessons").deleteRecursively()
        true
    }
}
