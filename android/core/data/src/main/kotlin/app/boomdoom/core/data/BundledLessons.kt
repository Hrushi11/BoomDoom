package app.boomdoom.core.data

import android.content.Context
import app.boomdoom.core.data.repo.LessonJson
import app.boomdoom.core.model.LessonManifest
import kotlinx.serialization.Serializable

/** Starter lessons shipped in assets/lessons (US-06A, Tech Spec §7.3). */
object BundledLessons {
    @Serializable private data class Entry(val manifest: String, val video: String)

    fun load(ctx: Context): List<Pair<LessonManifest, String>> = runCatching {
        val index = ctx.assets.open("lessons/index.json").bufferedReader().use { it.readText() }
        LessonJson.decodeFromString<List<Entry>>(index).map { e ->
            val m = ctx.assets.open(e.manifest).bufferedReader().use { it.readText() }
            LessonJson.decodeFromString<LessonManifest>(m) to e.video
        }
    }.getOrDefault(emptyList())
}
