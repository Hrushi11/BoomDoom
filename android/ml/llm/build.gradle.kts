plugins { id("boomdoom.android.library") }
dependencies {
    implementation(project(":core:model"))
    // llama.cpp JNI bridge for LFM2-VL-450M (Tech Spec §5.8); NDK build added after spike.
}
