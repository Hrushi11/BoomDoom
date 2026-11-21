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
 * vision). Output: sigmoid score per label (8 built-ins + talking_head). Preprocessing matches the Pixel 9
 * benchmark: whole frame scaled to 224x224, RGB to [-1, 1].
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
                return labels.indices.associate { labels[it] to row[it].toDouble() }
            }
        }
    }

    override fun close() = session.close()

    companion object {
        const val SIDE = 224
        val FILES = listOf("vision.onnx", "vision.json")

        internal fun pixels(image: Bitmap): FloatArray {
            val square = Bitmap.createScaledBitmap(image, SIDE, SIDE, true)
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
