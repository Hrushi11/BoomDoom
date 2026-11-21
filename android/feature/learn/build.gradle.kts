plugins { id("boomdoom.android.feature") }
dependencies {
    implementation(project(":core:network"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.datasource)
}
