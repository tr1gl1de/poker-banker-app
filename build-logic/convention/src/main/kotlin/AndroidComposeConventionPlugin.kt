import app.feltbook.buildlogic.lib
import app.feltbook.buildlogic.libs
import com.android.build.api.dsl.CommonExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Compose для Android-модуля (application или library) + скриншот-тесты Roborazzi на Robolectric (JUnit4).
 * Эталонные PNG — в `src/test/screenshots` (в git): `recordRoborazziDebug` / `verifyRoborazziDebug`.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("io.github.takahirom.roborazzi")

            extensions.configure<CommonExtension>("android") {
                buildFeatures.compose = true
            }

            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
            }

            dependencies {
                val bom = platform(libs.lib("compose-bom"))
                add("implementation", bom)
                add("implementation", libs.lib("compose-ui"))
                add("implementation", libs.lib("compose-foundation"))
                add("implementation", libs.lib("compose-material3"))
                add("implementation", libs.lib("compose-ui-tooling-preview"))
                add("debugImplementation", libs.lib("compose-ui-tooling"))
                add("debugImplementation", libs.lib("compose-ui-test-manifest"))

                add("testImplementation", bom)
                add("testImplementation", libs.lib("junit4"))
                add("testImplementation", libs.lib("robolectric"))
                add("testImplementation", libs.lib("compose-ui-test-junit4"))
                add("testImplementation", libs.lib("roborazzi"))
                add("testImplementation", libs.lib("roborazzi-compose"))
                add("testImplementation", libs.lib("roborazzi-junit-rule"))
            }
        }
}
