// The only module allowed to talk to the backend (Tech Spec §3.2). Carries lesson data only.
plugins {
    id("boomdoom.android.library")
    id("boomdoom.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}
android {
    buildFeatures { buildConfig = true }
    defaultConfig { buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/\"") }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
}
