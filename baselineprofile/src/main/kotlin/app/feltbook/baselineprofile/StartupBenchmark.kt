package app.feltbook.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Холодный старт до Главной (ТЗ §9: ≤ 1 с), без профиля и с Baseline Profile:
 *
 *     ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 *
 * Сборка `benchmarkRelease` — минифицирована R8, как release. Метрики timeToInitialDisplayMs/timeToFullDisplayMs —
 * в выводе Gradle и в baselineprofile/build/outputs/connected_android_test_additional_output/.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
    ) {
        startToHome()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
