import app.feltbook.buildlogic.FELTBOOK_JAVA_VERSION
import app.feltbook.buildlogic.FELTBOOK_JVM_TARGET
import app.feltbook.buildlogic.configureTestForks
import app.feltbook.buildlogic.lib
import app.feltbook.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Чистый Kotlin Multiplatform (ADR 0001): сейчас только `jvm()`, код в commonMain/commonTest.
 * Тесты — kotlin.test, на JVM исполняются JUnit Platform (JUnit 5). Покрытие — Kover.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("org.jetbrains.kotlinx.kover")
            pluginManager.apply("feltbook.quality")

            extensions.configure<KotlinMultiplatformExtension> {
                jvm {
                    compilerOptions.jvmTarget.set(FELTBOOK_JVM_TARGET)
                }
                sourceSets.getByName("commonTest").dependencies {
                    implementation(kotlin("test"))
                }
                sourceSets.getByName("jvmTest").dependencies {
                    implementation(project.dependencies.platform(libs.lib("junit-bom")))
                    runtimeOnly(libs.lib("junit-platform-launcher"))
                }
            }

            tasks.withType<JavaCompile>().configureEach {
                sourceCompatibility = FELTBOOK_JAVA_VERSION.toString()
                targetCompatibility = FELTBOOK_JAVA_VERSION.toString()
            }
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }
            configureTestForks()
        }
}
