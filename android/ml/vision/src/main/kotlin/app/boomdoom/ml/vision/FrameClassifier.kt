package app.boomdoom.ml.vision

/**
 * SigLIP 2 image encoder via ONNX Runtime (Tech Spec §5.3). Label text embeddings are precomputed
 * and cached in Room, so per-frame cost is the image encoder only. Target ≤ 150 ms/frame.
 */
interface FrameClassifier {
    /** @param rgba 448-px short-side frame, RGBA_8888. Never retained after the call. */
    fun embed(rgba: ByteArray, width: Int, height: Int): FloatArray

    /** p(label) = σ(scale · <img, txt> + bias), max over the label's prompts. */
    fun score(imageEmbedding: FloatArray, labelEmbeddings: Map<String, List<FloatArray>>): Map<String, Double>
}
