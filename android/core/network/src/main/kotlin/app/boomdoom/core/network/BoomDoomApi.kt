package app.boomdoom.core.network

import app.boomdoom.core.model.LessonManifest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** API v1 (Tech Spec §8.3). There is deliberately no endpoint that accepts reel, label or feed data. */
interface BoomDoomApi {
    @POST("v1/users") suspend fun register(@Body body: RegisterRequest): TokenPair
    @POST("v1/auth/refresh") suspend fun refresh(@Body body: RefreshRequest): TokenPair

    @PUT("v1/users/{id}/lesson-profile")
    suspend fun putProfile(@Path("id") userId: String, @Body body: LessonProfileRequest): JsonObject

    @GET("v1/topics") suspend fun topics(): List<TopicDto>
    @POST("v1/topics") suspend fun createTopic(@Body body: TopicCreate): TopicStatusDto
    @DELETE("v1/topics/{id}") suspend fun deleteTopic(@Path("id") topicId: String)

    @GET("v1/feed") suspend fun feed(@Query("topic_id") topicId: String?, @Query("limit") limit: Int = 10): List<LessonManifest>
    @POST("v1/events") suspend fun events(@Body body: EventBatch): JsonObject
    @POST("v1/lessons/{id}/reports") suspend fun report(@Path("id") lessonId: String, @Body body: ReportRequest)

    @PUT("v1/users/{id}/push-token") suspend fun pushToken(@Path("id") userId: String, @Body body: PushToken)
    @DELETE("v1/users/{id}") suspend fun deleteAccount(@Path("id") userId: String): JsonObject
    @GET("v1/config") suspend fun config(): JsonObject
}

@Serializable data class RegisterRequest(
    @SerialName("install_secret_hash") val installSecretHash: String,
    @SerialName("app_version") val appVersion: String,
    val locale: String,
)
@Serializable data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)
@Serializable data class TokenPair(
    @SerialName("user_id") val userId: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)
@Serializable data class TopicIn(val name: String, val level: String, val goal: String? = null, val type: String)
@Serializable data class LessonProfileRequest(
    val language: String, @SerialName("voice_id") val voiceId: String,
    @SerialName("length_s") val lengthS: Int, val topics: List<TopicIn>,
)
@Serializable data class TopicCreate(val name: String, val level: String, val goal: String? = null)
@Serializable data class TopicStatusDto(@SerialName("topic_id") val topicId: String, val status: String)
@Serializable data class TopicDto(
    @SerialName("topic_id") val topicId: String, val name: String, val level: String,
    val goal: String?, val status: String, @SerialName("ready_count") val readyCount: Int,
)
@Serializable data class LessonEvent(
    @SerialName("event_id") val eventId: String, val type: String,
    @SerialName("lesson_id") val lessonId: String, val at: String, val data: JsonObject? = null,
)
@Serializable data class EventBatch(val events: List<LessonEvent>)
@Serializable data class ReportRequest(val reason: String, val note: String? = null, @SerialName("position_s") val positionS: Double)
@Serializable data class PushToken(@SerialName("fcm_token") val fcmToken: String)
