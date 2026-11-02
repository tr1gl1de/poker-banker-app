package app.feltbook.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal val FELTBOOK_JAVA_VERSION = JavaVersion.VERSION_17
internal val FELTBOOK_JVM_TARGET = JvmTarget.JVM_17

/** Общая настройка Android-модулей: SDK, Java 17, lint, unit-тесты (Robolectric/JUnit4). */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()

        compileOptions.sourceCompatibility = FELTBOOK_JAVA_VERSION
        compileOptions.targetCompatibility = FELTBOOK_JAVA_VERSION

        testOptions.unitTests.isIncludeAndroidResources = true

        lint.apply {
            abortOnError = true
            warningsAsErrors = true
            checkDependencies = false
            // Версии зависимостей обновляются осознанно через libs.versions.toml, а не по подсказке lint.
            disable +=
                setOf(
                    "GradleDependency",
                    "NewerVersionAvailable",
                    "AndroidGradlePluginVersion",
                    "OldTargetApi",
                    "ObsoleteLintCustomCheck",
                )
        }
    }
    configureKotlinJvmTarget()
    configureTestForks()
    // Hilt/KSP генерируют test-исходники и в модулях без тестов (заглушки feature:*).
    tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
}

internal fun Project.configureKotlinJvmTarget() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(FELTBOOK_JVM_TARGET)
    }
}

/** Памяти мало: один форк на модуль, 1 ГБ кучи. */
internal fun Project.configureTestForks() {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "1g"
        maxParallelForks = 1
        // Robolectric на JDK 21: доступ к FileDescriptor через jdk.internal.access.
        jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
    }
}
