package app.boomdoom.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device classification models (Tech Spec §5.9), downloaded on Wi-Fi into files/models.
 * Written by the download worker (:core:network), read by the classifiers (:ml:*) via :monitor.
 */
@Singleton
class ModelFiles @Inject constructor(@ApplicationContext ctx: Context) {
    val dir: File = File(ctx.filesDir, "models")

    fun installed(): Boolean = REQUIRED.all { File(dir, it).let { f -> f.isFile && f.length() > 0 } }

    fun version(): String? = File(dir, VERSION_FILE).takeIf { it.isFile }?.readText()?.trim()

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    companion object {
        const val VERSION_FILE = "VERSION"
        val REQUIRED = listOf("caption.onnx", "caption.json", "caption_vocab.bin", "vision.onnx", "vision.json")
    }
}
