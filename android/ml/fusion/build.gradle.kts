// Pure Kotlin so fusion thresholds are unit-tested on the JVM (Tech Spec §5.6).
plugins { id("boomdoom.jvm.library") }
dependencies {
    implementation(project(":core:model"))
}
