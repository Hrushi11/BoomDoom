package app.boomdoom.ml.text

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

/**
 * XLM-R SentencePiece Unigram tokenizer in plain Kotlin.
 *
 * Replaces the onnxruntime-extensions tokenizer graph, whose native libraries are not 16 KB page-aligned
 * (required on Android 15+ devices with 16 KB pages, e.g. Pixel 9). Matches Hugging Face's
 * XLMRobertaTokenizerFast with add_special_tokens=false: NFKC-style normalization, whitespace split,
 * "▁" word prefix, Viterbi segmentation over piece scores, unknown characters fused into one <unk>.
 * Ids are in the model's (fairseq-shifted) id space, so they feed the ONNX model directly.
 */
class UnigramTokenizer private constructor(
    private val index: HashMap<String, Int>,
    private val scores: FloatArray,
    private val maxPieceChars: Int,
) {
    private val unkScore = scores.filterIndexed { i, _ -> i > SPECIALS }.min() - UNK_PENALTY

    fun encode(text: String): IntArray {
        val out = ArrayList<Int>(text.length / 3 + 4)
        for (word in normalize(text).split(' ')) {
            if (word.isNotEmpty()) segment(SPACE + word, out)
        }
        return out.toIntArray()
    }

    /** Viterbi over code-point boundaries: best[i] = best score of any segmentation of word[0, i). */
    private fun segment(word: String, out: MutableList<Int>) {
        val bounds = codePointBounds(word)
        val n = bounds.size - 1
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }.also { it[0] = 0f }
        val fromCp = IntArray(n + 1)
        val tokenAt = IntArray(n + 1)
        for (i in 0 until n) {
            if (best[i] == Float.NEGATIVE_INFINITY) continue
            var hasSingle = false
            var j = i + 1
            while (j <= n && j - i <= maxPieceChars) {
                val id = index[word.substring(bounds[i], bounds[j])]
                if (id != null) {
                    if (j == i + 1) hasSingle = true
                    val s = best[i] + scores[id]
                    if (s > best[j]) { best[j] = s; fromCp[j] = i; tokenAt[j] = id }
                }
                j++
            }
            if (!hasSingle) { // character not in the vocabulary: one <unk> for it
                val s = best[i] + unkScore
                if (s > best[i + 1]) { best[i + 1] = s; fromCp[i + 1] = i; tokenAt[i + 1] = UNK }
            }
        }
        val ids = ArrayList<Int>()
        var k = n
        while (k > 0) { ids.add(tokenAt[k]); k = fromCp[k] }
        ids.reverse()
        for (id in ids) if (!(id == UNK && out.isNotEmpty() && out.last() == UNK)) out.add(id)
    }

    companion object {
        const val UNK = 3
        private const val SPECIALS = 3 // ids 0..3 are <s> <pad> </s> <unk>
        private const val UNK_PENALTY = 10f
        private const val SPACE = "▁"

        /** Reads caption_vocab.bin (see ml/export_onnx.py export_vocab). */
        fun load(input: InputStream): UnigramTokenizer {
            val data = DataInputStream(input.buffered()).use { it.readBytes() }
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val count = buf.int
            val scores = FloatArray(count)
            val index = HashMap<String, Int>(count * 2)
            var maxChars = 1
            for (id in 0 until count) {
                scores[id] = buf.float
                val len = buf.short.toInt() and 0xFFFF
                val piece = String(data, buf.position(), len, Charsets.UTF_8)
                buf.position(buf.position() + len)
                if (id > SPECIALS && id != count - 1) { // skip specials and the trailing <mask>
                    index.putIfAbsent(piece, id)
                    maxChars = maxOf(maxChars, piece.codePointCount(0, piece.length))
                }
            }
            return UnigramTokenizer(index, scores, maxChars)
        }

        /** Approximates SentencePiece nmt_nfkc: NFKC, drop control chars, all whitespace to single spaces. */
        internal fun normalize(text: String): String {
            val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
            val sb = StringBuilder(nfkc.length)
            var lastSpace = true
            var i = 0
            while (i < nfkc.length) {
                val cp = nfkc.codePointAt(i)
                i += Character.charCount(cp)
                when {
                    Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> {
                        if (!lastSpace) sb.append(' ')
                        lastSpace = true
                    }
                    Character.getType(cp) == Character.CONTROL.toInt() || Character.getType(cp) == Character.FORMAT.toInt() &&
                        cp != 0x200D -> Unit // drop control/format chars, keep zero-width joiner (emoji sequences)
                    else -> { sb.appendCodePoint(cp); lastSpace = false }
                }
            }
            return sb.toString().trimEnd()
        }

        private fun codePointBounds(s: String): IntArray {
            val n = s.codePointCount(0, s.length)
            val out = IntArray(n + 1)
            var off = 0
            for (k in 1..n) { off = s.offsetByCodePoints(off, 1); out[k] = off }
            return out
        }
    }
}
