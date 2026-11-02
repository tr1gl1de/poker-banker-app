import app.feltbook.buildlogic.configureAndroidCommon
import app.feltbook.buildlogic.libs
import app.feltbook.buildlogic.version
import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Модуль инструментальных тестов `com.android.test` (`:baselineprofile`): SDK, Java 17, lint, ktlint/detekt —
 * как у остальных модулей. Тесты лежат в `src/main` (так устроен `com.android.test`) и запускаются только
 * на устройстве; в `./gradlew build` модуль лишь компилируется.
 */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.android.test")
            pluginManager.apply("feltbook.quality")

            extensions.configure<TestExtension> {
                configureAndroidCommon(this)
                defaultConfig {
                    targetSdk = libs.version("targetSdk").toInt()
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }
        }
}
