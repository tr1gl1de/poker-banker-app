// Генератор Baseline Profile и макробенчмарк холодного старта для :app.
// Устройство: бенчмарк — API 28+, генерация профиля — API 33+ или root. В CI только компилируется.
//   ./gradlew :app:generateBaselineProfile                         → app/src/release/generated/baselineProfiles
//   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  StartupBenchmark (без профиля vs с профилем)
plugins {
    alias(libs.plugins.feltbook.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.feltbook.baselineprofile"

    defaultConfig {
        // Macrobenchmark — API 28+; сбор профиля без root — только API 33+.
        minSdk = 28
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // Подключённый телефон (Redmi по USB/adb) или запущенный эмулятор. Managed devices — по желанию, см. README.
    useConnectedDevices = true
}

androidComponents {
    onVariants { variant ->
        // applicationId цели (app.feltbook.benchmark) — в аргумент раннера, тесты не хардкодят пакет.
        val loader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { loader.load(it)?.applicationId.orEmpty() },
        )
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
