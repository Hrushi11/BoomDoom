plugins {
    id("boomdoom.android.library")
    id("boomdoom.android.compose")
}
dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
