package app.boomdoom.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anonymous identity (Tech Spec §8.2): a random install secret that never leaves the phone (only its hash
 * is sent), plus the backend tokens. Stored in EncryptedSharedPreferences.
 */
@Singleton
class AuthStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        ctx, "auth",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val installSecretHash: String
        @Synchronized get() {
            val secret = prefs.getString("install_secret", null) ?: ByteArray(32).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }.also { prefs.edit { putString("install_secret", it) } }
            return MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()).joinToString("") { "%02x".format(it) }
        }

    val userId: String? get() = prefs.getString("user_id", null)
    val accessToken: String? get() = prefs.getString("access_token", null)
    val refreshToken: String? get() = prefs.getString("refresh_token", null)

    @Synchronized
    fun save(tokens: TokenPair) = prefs.edit {
        putString("user_id", tokens.userId); putString("access_token", tokens.accessToken)
        putString("refresh_token", tokens.refreshToken)
    }

    /** After account deletion: forget everything, including the install secret (fresh identity). */
    @Synchronized
    fun clear() = prefs.edit { clear() }
}
