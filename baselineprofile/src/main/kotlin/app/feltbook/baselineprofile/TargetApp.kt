package app.feltbook.baselineprofile

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/**
 * Пакет приложения под тестом. Его передаёт `build.gradle.kts` модуля (`targetAppId`) — это сборки
 * `nonMinifiedRelease`/`benchmarkRelease` с applicationId `app.feltbook.benchmark` (см. app/build.gradle.kts):
 * отдельное приложение рядом с настоящим Feltbook, его данные можно стирать.
 */
internal val targetPackage: String
    get() =
        InstrumentationRegistry.getArguments().getString("targetAppId")?.takeIf { it.isNotBlank() }
            ?: error("targetAppId не передан раннеру — запускайте через Gradle (см. baselineprofile/build.gradle.kts)")

/** Чистые данные перед прогоном: одинаковый путь «пустая Главная → новая игра → стол» на каждом запуске. */
internal fun clearTargetData() {
    val pkg = targetPackage
    // Защита от случайного стирания настоящих игр: чистим только отдельную benchmark-сборку.
    check(pkg.endsWith(BENCHMARK_SUFFIX)) { "Отказ стирать данные $pkg: ожидается *$BENCHMARK_SUFFIX" }
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("pm clear $pkg")
}

private const val BENCHMARK_SUFFIX = ".benchmark"
