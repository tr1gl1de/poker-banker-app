import androidx.room.gradle.RoomExtension
import app.feltbook.buildlogic.lib
import app.feltbook.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room на KSP: плагины KSP и androidx.room, схемы в `schemas/` модуля (в git — для тестов миграций),
 * runtime/ktx/compiler и room-testing для тестов.
 */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room")

            extensions.configure<RoomExtension> {
                schemaDirectory(layout.projectDirectory.dir("schemas").asFile.path)
            }

            dependencies {
                add("implementation", libs.lib("androidx-room-runtime"))
                add("implementation", libs.lib("androidx-room-ktx"))
                add("ksp", libs.lib("androidx-room-compiler"))
                add("testImplementation", libs.lib("androidx-room-testing"))
            }
        }
}
