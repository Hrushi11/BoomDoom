plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

/**
 * Tech Spec §3.2: "feed never leaves the phone" is structural. Modules that see feed data must not
 * depend on the network module or any HTTP client. Runs in CI (`./gradlew checkFeedIsolation`).
 */
val feedModules = listOf(":monitor", ":overlay", ":ml:vision", ":ml:text", ":ml:speech", ":ml:fusion", ":ml:llm")
val forbidden = listOf(":core:network", "com.squareup.retrofit2", "com.squareup.okhttp3", "io.ktor")

tasks.register("checkFeedIsolation") {
    group = "verification"
    description = "Fails if a feed-handling module can reach the network stack."
    notCompatibleWithConfigurationCache("Resolves other projects' classpaths")
    val graph = provider {
        feedModules.associateWith { path ->
            val p = project(path)
            val cfg = p.configurations.findByName("releaseRuntimeClasspath")
                ?: p.configurations.getByName("runtimeClasspath")
            cfg.incoming.resolutionResult.allComponents.map { it.id.displayName }
        }
    }
    doLast {
        val violations = graph.get().flatMap { (module, deps) ->
            deps.filter { d -> forbidden.any { f -> d.contains(f) } }.map { "$module -> $it" }
        }
        check(violations.isEmpty()) { "Feed isolation violated:\n" + violations.joinToString("\n") }
        println("Feed isolation OK for ${feedModules.size} modules")
    }
}
