package app.boomdoom.ml.text

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.Closeable
import java.io.File

/**
 * Zero-shot caption classifier: multilingual MiniLM NLI (ml/export_onnx.py --stage caption).
 * For each label it scores P(entailment) of "<caption> </s></s> This video is about <topic>." — the same
 * encoding as the Pixel 9 benchmark (ml/android/ReelBenchmark.java), with tokenization done in Kotlin.
 *
 * Files in [dir]: caption.onnx, caption.json (labels + hypothesis ids), caption_vocab.bin.
 * Not thread-safe: call from one background thread.
 */
class CaptionClassifier(dir: File, customLabels: List<String> = emptyList(), threads: Int = 4) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val tokenizer = File(dir, "caption_vocab.bin").inputStream().use { UnigramTokenizer.load(it) }
    val labels: List<String>
    private val hypotheses: List<IntArray>

    init {
        val meta = JSONObject(File(dir, "caption.json").readText())
        val builtIn = meta.getJSONArray("labels").let { a -> List(a.length()) { a.getString(it) } }
        val builtInIds = meta.getJSONArray("hypothesis_ids").let { a ->
            List(a.length()) { r -> a.getJSONArray(r).let { row -> IntArray(row.length()) { row.getInt(it) } } }
        }
        val custom = customLabels.map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() !in builtIn }
        labels = builtIn + custom.map { it.lowercase() }
        // Custom labels: same hypothesis template as export_onnx.py, tokenized on the phone.
        hypotheses = builtInIds + custom.map { tokenizer.encode("This video is about ${it.lowercase()}.") }
        session = env.createSession(File(dir, "caption.onnx").absolutePath, OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
        })
    }

    /** P(entailment) per label, in [labels] order. Empty/blank captions return an empty map. */
    fun score(caption: String): Map<String, Double> {
        val pieces = tokenizer.encode(caption.take(4096))
        if (pieces.isEmpty()) return emptyMap()
        val width = hypotheses.maxOf { minOf(MAX_TOKENS, pieces.size + it.size + 4) }
        val ids = Array(hypotheses.size) { LongArray(width) { PAD } }
        val mask = Array(hypotheses.size) { LongArray(width) }
        hypotheses.forEachIndexed { row, hyp ->
            // XLM-R pair: <s> caption </s></s> hypothesis </s>, caption truncated first.
            val count = minOf(pieces.size, width - hyp.size - 4)
            var pos = 0
            ids[row][pos++] = BOS
            for (i in 0 until count) ids[row][pos++] = pieces[i].toLong()
            ids[row][pos++] = EOS; ids[row][pos++] = EOS
            for (t in hyp) ids[row][pos++] = t.toLong()
            ids[row][pos++] = EOS
            for (i in 0 until pos) mask[row][i] = 1L
        }
        OnnxTensor.createTensor(env, ids).use { idT ->
            OnnxTensor.createTensor(env, mask).use { maskT ->
                session.run(mapOf("input_ids" to idT, "attention_mask" to maskT)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val probs = result[0].value as FloatArray
                    return labels.indices.associate { labels[it] to probs[it].toDouble() }
                }
            }
        }
    }

    override fun close() = session.close()

    companion object {
        private const val MAX_TOKENS = 256
        private const val BOS = 0L
        private const val PAD = 1L
        private const val EOS = 2L
        val FILES = listOf("caption.onnx", "caption.json", "caption_vocab.bin")
    }
}
