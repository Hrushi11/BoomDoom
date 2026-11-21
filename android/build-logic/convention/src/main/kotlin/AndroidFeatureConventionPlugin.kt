import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Tech Spec §3.2: feature modules depend on core:* only and expose screens + ViewModels. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("boomdoom.android.library")
        pluginManager.apply("boomdoom.android.compose")
        pluginManager.apply("boomdoom.android.hilt")
        dependencies {
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:data"))
            add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", catalog.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", catalog.findLibrary("hilt-navigation-compose").get())
            add("implementation", catalog.findLibrary("androidx-activity-compose").get())
        }
    }
}
