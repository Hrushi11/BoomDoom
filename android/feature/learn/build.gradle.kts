plugins { id("boomdoom.android.feature") }
dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:player"))
    implementation(libs.media3.exoplayer)
}
