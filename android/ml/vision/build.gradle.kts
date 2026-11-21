plugins { id("boomdoom.android.library") }
dependencies {
    implementation(project(":core:model"))
    implementation(libs.onnxruntime.android)
}
