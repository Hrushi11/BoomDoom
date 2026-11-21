package app.boomdoom.ml.text

/** EmbeddingGemma (ONNX, int8) + ML Kit OCR (Latin + Devanagari) (Tech Spec §5.4). */
interface TextClassifier {
    fun score(text: String, labelPrompts: Map<String, List<String>>): Map<String, Double>
}

interface CaptionOcr {
    /** Keeps only lines with height ≥ 2.5% of the frame (captions, not UI). */
    suspend fun read(rgba: ByteArray, width: Int, height: Int): String
}
