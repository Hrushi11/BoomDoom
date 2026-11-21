plugins { id("boomdoom.android.library") }
dependencies {
    implementation(project(":core:model"))
    // Speech runtime (Shruti or lipi-hn) chosen in spike S4; integrated via JNI.
}
