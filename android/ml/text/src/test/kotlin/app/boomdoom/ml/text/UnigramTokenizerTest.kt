package app.boomdoom.ml.text

import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Parity with Hugging Face's tokenizer on English, Devanagari Hindi, romanized Hinglish, emoji, URLs,
 * full-width text and whitespace edge cases. Vectors come from ml/export_onnx.py's tokenizer; the vocab
 * (ml/out/phone/caption_vocab.bin) is produced by `python ml/export_onnx.py --stage caption`.
 */
class UnigramTokenizerTest {
    private val vocab = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "ml/out/phone/caption_vocab.bin") }.firstOrNull { it.exists() }

    @Test fun matchesHuggingFaceOnReferenceCorpus() {
        assumeTrue("caption_vocab.bin not exported; skipping", vocab != null)
        val tok = vocab!!.inputStream().use { UnigramTokenizer.load(it) }
        val vectors = JSONArray(javaClass.getResource("/tokenizer_vectors.json")!!.readText())
        var exact = 0
        var tokens = 0
        var tokenMismatch = 0
        val failures = mutableListOf<String>()
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val expected = v.getJSONArray("ids").let { a -> IntArray(a.length()) { a.getInt(it) } }
            val actual = tok.encode(v.getString("text"))
            tokens += expected.size
            if (expected.contentEquals(actual)) exact++ else {
                tokenMismatch += maxOf(expected.size, actual.size) - expected.zip(actual).count { (a, b) -> a == b }
                if (failures.size < 8) failures += "\"${v.getString("text")}\"\n  hf=${expected.toList()}\n  kt=${actual.toList()}"
            }
        }
        val exactRate = exact.toDouble() / vectors.length()
        println("tokenizer parity: $exact/${vectors.length()} texts exact (${"%.1f".format(exactRate * 100)}%), " +
            "$tokenMismatch/$tokens tokens differ")
        failures.forEach(::println)
        assertTrue("parity too low: $exactRate", exactRate >= 0.97)
    }
}
