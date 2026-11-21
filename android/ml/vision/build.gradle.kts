plugins { id("boomdoom.android.library") }
dependencies {
    implementation(project(":core:model"))
    // Add when classification is built (Tech Spec §5.3). Use an ONNX Runtime release with 16 KB-aligned
    // native libs (required on Android 15+ devices with 16 KB pages); 1.20.0 is not aligned.
    // implementation(libs.onnxruntime.android)
}
