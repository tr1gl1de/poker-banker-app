import app.feltbook.buildlogic.FELTBOOK_JAVA_VERSION
import app.feltbook.buildlogic.libs
import app.feltbook.buildlogic.version
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** ktlint + detekt в каждом модуле. Конфиг detekt — config/detekt/detekt.yml. */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure<KtlintExtension> {
                version.set(libs.version("ktlint"))
                filter {
                    exclude { it.file.path.contains("/build/") }
                }
            }

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
                source.setFrom(layout.projectDirectory.dir("src"))
            }

            tasks.withType<Detekt>().configureEach {
                jvmTarget = FELTBOOK_JAVA_VERSION.toString()
                exclude("**/build/**")
                reports {
                    html.required.set(true)
                    xml.required.set(false)
                    sarif.required.set(false)
                    txt.required.set(false)
                    md.required.set(false)
                }
            }

            // detekt 1.23.8 собран под Kotlin 2.0.21; KGP выравнивает org.jetbrains.kotlin:* до своей версии.
            configurations.matching { it.name == "detekt" }.configureEach {
                resolutionStrategy.eachDependency {
                    if (requested.group == "org.jetbrains.kotlin") useVersion(DETEKT_KOTLIN_VERSION)
                }
            }
        }

    private companion object {
        const val DETEKT_KOTLIN_VERSION = "2.0.21"
    }
}
