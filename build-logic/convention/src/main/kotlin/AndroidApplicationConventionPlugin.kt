import app.feltbook.buildlogic.CheckNoInternetPermissionTask
import app.feltbook.buildlogic.PrintVersionTask
import app.feltbook.buildlogic.configureAndroidCommon
import app.feltbook.buildlogic.disableReleaseUnitTests
import app.feltbook.buildlogic.feltbookVersion
import app.feltbook.buildlogic.libs
import app.feltbook.buildlogic.version
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("feltbook.quality")

            val version = feltbookVersion()

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig {
                    targetSdk = libs.version("targetSdk").toInt()
                    versionCode = version.code
                    versionName = version.releaseName
                }
                buildTypes {
                    getByName("debug") {
                        // dev и release ставятся рядом; подпись «Feltbook dev» — src/debug/res.
                        applicationIdSuffix = ".dev"
                        versionNameSuffix = version.devSuffix
                    }
                }
            }

            extensions.configure<ApplicationAndroidComponentsExtension> {
                disableReleaseUnitTests(this)
                onVariants { variant ->
                    val name = variant.name.replaceFirstChar { it.uppercase() }
                    val check =
                        tasks.register<CheckNoInternetPermissionTask>("check${name}NoInternetPermission") {
                            group = "verification"
                            description = "Проверяет, что в merged manifest варианта ${variant.name} нет INTERNET"
                            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                            report.set(layout.buildDirectory.file("reports/manifest/${variant.name}-no-internet.txt"))
                        }
                    tasks.named("check").configure { dependsOn(check) }
                }
            }

            tasks.register<PrintVersionTask>("printVersion") {
                versionCode.set(version.code)
                releaseVersionName.set(version.releaseName)
                devVersionName.set(version.devName)
            }
        }
}
