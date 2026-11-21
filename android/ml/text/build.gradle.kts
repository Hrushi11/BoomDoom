plugins { id("boomdoom.android.library") }
dependencies {
    implementation(project(":core:model"))
    // Add when classification is built (Tech Spec §5.4). Both must ship 16 KB-aligned native libs:
    // implementation(libs.onnxruntime.android)
    // implementation(libs.mlkit.text.devanagari)
}
