plugins {
    id("boomdoom.android.library")
    id("boomdoom.android.hilt")
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":overlay"))
    implementation(project(":ml:fusion"))
    implementation(project(":ml:vision"))
    implementation(project(":ml:text"))
    implementation(project(":ml:speech"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose) // CaptureConsentActivity
}
