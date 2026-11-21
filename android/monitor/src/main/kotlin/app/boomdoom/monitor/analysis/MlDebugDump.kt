package app.boomdoom.monitor.analysis

import android.graphics.Bitmap
import android.util.Log
import app.boomdoom.core.model.ClassifiedReel
import app.boomdoom.core.model.Signal
import org.json.JSONObject
import java.io.File

/**
 * Developer diagnostics for on-device classification. OFF unless the marker file `files/ml_debug` exists, which
 * can only be created with `adb shell run-as <pkg> touch files/ml_debug` (debuggable builds only). When on, the
 * newest [KEEP] reels' frame (as captured), caption text and per-signal scores are written to files/ml_debug_out/
 * so a developer can compare them with the desktop reference (ml/reel_classifier.py). Delete the marker to stop
 * and the folder to wipe; nothing here ever leaves the phone.
 */
internal class MlDebugDump(private val filesDir: File) {
    private val marker get() = File(filesDir, "ml_debug")
    val enabled get() = marker.exists()
    /** Reels to keep: the marker's content if it is a number (the collector writes e.g. "400"), else [KEEP]. */
    private val keep get() = runCatching { marker.readText().trim().toInt() }.getOrDefault(KEEP)

    fun hasFrame(reelId: String) = File(out, "${reelId.take(8)}.jpg").exists()
    private val out get() = File(filesDir, "ml_debug_out").apply { mkdirs() }

    fun frame(reelId: String, frame: Bitmap) {
        if (!enabled) return
        runCatching { File(out, "${reelId.take(8)}.jpg").outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
    }

    fun record(reelId: String, caption: String?, scores: Map<Signal, Map<String, Double>>, result: ClassifiedReel) {
        if (!enabled) return
        val top = scores.entries.joinToString(" | ") { (sig, m) ->
            "$sig " + m.entries.sortedByDescending { it.value }.take(3).joinToString { "${it.key}=${"%.2f".format(it.value)}" }
        }
        Log.i(TAG, "reel ${reelId.take(8)} -> ${result.primaryLabel} ${"%.2f".format(result.confidence)} :: $top :: caption ${caption?.length ?: 0} chars")
        runCatching {
            File(out, "${reelId.take(8)}.json").writeText(JSONObject().apply {
                put("caption_text", caption ?: JSONObject.NULL)
                put("label", result.primaryLabel); put("confidence", result.confidence)
                put("sponsored", caption?.startsWith("[sponsored]") == true); put("at", System.currentTimeMillis())
                scores.forEach { (sig, m) -> put("scores_" + sig.name.lowercase(), JSONObject(m as Map<*, *>)) }
            }.toString(1))
            out.listFiles()!!.sortedByDescending { it.lastModified() }.drop(keep * 2).forEach { it.delete() }
        }
    }

    private companion object {
        const val TAG = "MlDebug"
        const val KEEP = 30
    }
}
