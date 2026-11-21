plugins {
    id("boomdoom.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
}
