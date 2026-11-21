package app.boomdoom.ml.speech

/** Shruti or lipi-hn, chosen in spike S4 (Tech Spec §5.5). Transcripts are never stored. */
interface SpeechToText {
    /** @param pcm16 16 kHz mono PCM16, first 10–15 s of the reel. Caller zeroes the buffer after. */
    suspend fun transcribe(pcm16: ShortArray): String
}
