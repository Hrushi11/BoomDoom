import java.util.Properties

// The only module allowed to talk to the backend (Tech Spec §3.2). Carries lesson data only.
plugins {
    id("boomdoom.android.library")
    id("boomdoom.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        // Set boomdoom.apiBaseUrl in android/local.properties (or -P) to your deployed API, e.g.
        // boomdoom.apiBaseUrl=https://1-2-3-4.sslip.io/   Default: the host machine from the emulator.
        val props = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val url = (findProperty("boomdoom.apiBaseUrl") as String?) ?: props.getProperty("boomdoom.apiBaseUrl")
            ?: "http://10.0.2.2:8000/"
        buildConfigField("String", "API_BASE_URL", "\"${url.trimEnd('/')}/\"")
        buildConfigField("String", "APP_VERSION", "\"0.1.0\"")
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.security.crypto)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
}
