package app.boomdoom.core.network.di

import app.boomdoom.core.network.AuthStore
import app.boomdoom.core.network.BoomDoomApi
import app.boomdoom.core.network.BuildConfig
import app.boomdoom.core.network.RegisterRequest
import app.boomdoom.core.network.RefreshRequest
import app.boomdoom.core.network.TokenPair
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private fun retrofit(client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /** Unauthenticated client used only for register/refresh (avoids recursion in the authenticator). */
    @Provides @Singleton @Named("auth")
    fun authApi(): BoomDoomApi = retrofit(
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build(),
    ).create(BoomDoomApi::class.java)

    @Provides @Singleton
    fun okHttp(store: AuthStore, @Named("auth") authApi: BoomDoomApi): OkHttpClient {
        fun obtain(): TokenPair? = runBlocking {
            val refresh = store.refreshToken
            val pair = refresh?.let { runCatching { authApi.refresh(RefreshRequest(it)) }.getOrNull() }
                ?: runCatching {
                    authApi.register(RegisterRequest(store.installSecretHash, BuildConfig.APP_VERSION,
                        Locale.getDefault().toLanguageTag()))
                }.getOrNull()
            pair?.also { store.save(it) }
        }

        val addToken = Interceptor { chain ->
            val token = store.accessToken ?: synchronized(store) { store.accessToken ?: obtain()?.accessToken }
            val req = chain.request().newBuilder().apply { token?.let { header("Authorization", "Bearer $it") } }.build()
            chain.proceed(req)
        }
        val refreshOn401 = object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                if (response.priorResponse != null) return null // already retried once
                val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
                val token = synchronized(store) {
                    // another thread may have refreshed meanwhile
                    store.accessToken?.takeIf { it != sent } ?: obtain()?.accessToken
                } ?: return null
                return response.request.newBuilder().header("Authorization", "Bearer $token").build()
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(addToken)
            .authenticator(refreshOn401)
            .build()
    }

    @Provides @Singleton
    fun api(client: OkHttpClient): BoomDoomApi = retrofit(client).create(BoomDoomApi::class.java)
}
