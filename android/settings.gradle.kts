pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BoomDoom"

include(":app")
include(":core:designsystem", ":core:data", ":core:network", ":core:model", ":core:common")
include(":monitor", ":overlay")
include(":ml:vision", ":ml:text", ":ml:speech", ":ml:fusion", ":ml:llm")
include(":feature:onboarding", ":feature:home", ":feature:learn", ":feature:insights", ":feature:settings")
