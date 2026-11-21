// Local data only: Room, DataStore, repositories. No network here (see :core:network).
plugins {
    id("boomdoom.android.library")
    id("boomdoom.android.hilt")
}
android {
    defaultConfig { ksp { arg("room.schemaLocation", "$projectDir/schemas") } }
}
dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
}
