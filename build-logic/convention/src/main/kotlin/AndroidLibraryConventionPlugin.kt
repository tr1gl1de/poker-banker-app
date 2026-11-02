import app.feltbook.buildlogic.configureAndroidCommon
import app.feltbook.buildlogic.disableReleaseUnitTests
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("feltbook.quality")

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
            }
            extensions.configure<LibraryAndroidComponentsExtension> {
                disableReleaseUnitTests(this)
            }
        }
}
