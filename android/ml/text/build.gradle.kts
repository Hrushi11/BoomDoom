plugins { id("boomdoom.android.library") }
dependencies {
    implementation(project(":core:model"))
    // 1.23.2: native libs are 16 KB page-aligned (verified). No onnxruntime-extensions: its 0.13.0 libs are
    // 4 KB-aligned, so the caption tokenizer is implemented in Kotlin (UnigramTokenizer).
    implementation(libs.onnxruntime.android)
    testImplementation("org.json:json:20240303") // Android's org.json is stubbed in JVM unit tests
}
