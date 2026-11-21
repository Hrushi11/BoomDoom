plugins { id("boomdoom.android.library") }
dependencies {
    implementation(project(":core:model"))
    implementation(libs.onnxruntime.android) // 1.23.2: 16 KB page-aligned
}
