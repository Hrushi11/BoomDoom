plugins {
    id("boomdoom.android.library")
    id("boomdoom.android.compose")
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
}
