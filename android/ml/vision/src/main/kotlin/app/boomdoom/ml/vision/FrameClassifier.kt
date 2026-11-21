package app.boomdoom.ml.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer

/**
 * SigLIP 2 base/16 frame classifier with the label prompts baked into the graph (ml/export_onnx.py --stage
 * vision). Groups: the 8 categories, talking_head, and background ("none of these": selfies, pets, scenery,
 * cartoons, memes...). RGB is mapped to [-1, 1] at 224x224.
 *
 * Crop: on a portrait screen only the video area is classified. Instagram's header, right-hand action column
 * and bottom caption/username strip are cut away ([CROP_TOP], [CROP_BOTTOM], [CROP_RIGHT]); the full screen
 * dropped a clear food frame from 0.93 to 0.61.
 *
 * Calibration ([calibrate]): SigLIP's per-group sigmoid is tiny even for an obvious match (a clear food photo
 * is ~0.01), so it can never pass the fusion thresholds. Scores are a softmax over the recovered logits of
 * every group, times a soft gate on the best logit (top logit < ~-9 means the frame matches nothing).
 * background, talking_head and rage_bait take part in the softmax but are not reported as categories:
 * rage-bait is about a message's tone, which a single frame cannot show, so frames never vote for it (it
 * stays a sink so that heated-looking frames do not leak into other categories). talking_head is reported
 * as its raw sigmoid for the fusion weight rule. Measured on Reels-styled frames: food 0.93, rally speech
 * news 0.84; selfies, pets, scenery and cartoons -> Other. POC constants; re-tune on the labelled set.
 *
 * Files in [dir]: vision.onnx, vision.json. Not thread-safe: call from one background thread.
 */
class FrameClassifier(dir: File, threads: Int = 4) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(File(dir, "vision.onnx").absolutePath,
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) })
    val labels: List<String> = JSONObject(File(dir, "vision.json").readText()).getJSONArray("labels")
        .let { a -> List(a.length()) { a.getString(it) } }

    fun score(frame: Bitmap): Map<String, Double> {
        val data = pixels(frame)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIDE.toLong(), SIDE.toLong())).use { input ->
            session.run(mapOf("pixels" to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val row = (result[0].value as Array<FloatArray>)[0]
                return calibrate(labels.indices.associate { labels[it] to row[it].toDouble() })
            }
        }
    }

    override fun close() = session.close()

    companion object {
        const val SIDE = 224
        private const val TALKING_HEAD = "talking_head"
        /** Groups that compete in the softmax but are never reported as a category (see class doc). */
        private val HIDDEN = setOf(TALKING_HEAD, "background", "rage_bait")
        private const val GATE_LOGIT = -9.0
        private const val GATE_WIDTH = 1.0
        const val CROP_TOP = 0.08f
        const val CROP_BOTTOM = 0.78f
        const val CROP_RIGHT = 0.87f

        /** Raw SigLIP sigmoids per group -> calibrated category scores (see class doc). */
        fun calibrate(raw: Map<String, Double>): Map<String, Double> {
            if (raw.isEmpty()) return raw
            val logits = raw.mapValues { (_, p) ->
                val q = p.coerceIn(1e-12, 1 - 1e-7)
                kotlin.math.ln(q / (1 - q))
            }
            val top = logits.values.max()
            val exp = logits.mapValues { (_, z) -> kotlin.math.exp(z - top) }
            val sum = exp.values.sum()
            val gate = 1.0 / (1.0 + kotlin.math.exp(-(top - GATE_LOGIT) / GATE_WIDTH))
            return exp.filterKeys { it !in HIDDEN }.mapValues { (_, e) -> e / sum * gate } +
                raw.filterKeys { it == TALKING_HEAD }
        }

        val FILES = listOf("vision.onnx", "vision.json")

        internal fun pixels(image: Bitmap): FloatArray {
            val video = if (image.height > image.width) {
                val top = (image.height * CROP_TOP).toInt()
                Bitmap.createBitmap(image, 0, top, (image.width * CROP_RIGHT).toInt(), (image.height * CROP_BOTTOM).toInt() - top)
            } else image
            val square = Bitmap.createScaledBitmap(video, SIDE, SIDE, true)
            if (video !== image && video !== square) video.recycle()
            val plane = SIDE * SIDE
            val rgb = IntArray(plane)
            square.getPixels(rgb, 0, SIDE, 0, 0, SIDE, SIDE)
            if (square !== image) square.recycle()
            val data = FloatArray(plane * 3)
            for (i in 0 until plane) {
                data[i] = ((rgb[i] shr 16) and 255) / 127.5f - 1f
                data[i + plane] = ((rgb[i] shr 8) and 255) / 127.5f - 1f
                data[i + 2 * plane] = (rgb[i] and 255) / 127.5f - 1f
            }
            return data
        }
    }
}
