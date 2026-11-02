plugins {
    alias(libs.plugins.feltbook.android.application)
    alias(libs.plugins.feltbook.android.compose)
    alias(libs.plugins.feltbook.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

// Подпись release — только секретами CI (release.yml декодирует keystore и задаёт переменные окружения).
// Без них release собирается неподписанным (app-release-unsigned.apk) — артефакт для проверки.
val keystorePath: String? = providers.environmentVariable("FELTBOOK_KEYSTORE_FILE").orNull

android {
    namespace = "app.feltbook"

    defaultConfig {
        applicationId = "app.feltbook"
    }

    // Интерфейс только на русском (ТЗ §9): ресурсы других языков из библиотек не попадают в APK.
    androidResources {
        localeFilters += listOf("ru")
    }

    // Язык один и задаётся в коде (RussianLocale) — языковые сплиты AAB не нужны.
    bundle {
        language {
            enableSplit = false
        }
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("FELTBOOK_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("FELTBOOK_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("FELTBOOK_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

// Baseline Profile (ТЗ §9: холодный старт ≤ 1 с, 60 fps на столе). Профиль генерируется на устройстве
// (`./gradlew :app:generateBaselineProfile`, модуль :baselineprofile) и лежит в src/release/generated/ —
// обычная сборка его только потребляет, устройство для неё не нужно.
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    // Startup profile → раскладка dex: классы холодного старта в первом dex-файле.
    dexLayoutOptimization = true
}

// Сборки для генерации профиля и бенчмарков (их создаёт плагин baselineprofile из release) ставятся
// отдельным приложением app.feltbook.benchmark с debug-подписью: не конфликтуют с подписанным Feltbook
// на телефоне и не трогают его данные (генератор стирает данные перед прогоном).
androidComponents {
    onVariants { variant ->
        if (variant.buildType in setOf("nonMinifiedRelease", "benchmarkRelease")) {
            variant.applicationId.set("app.feltbook.benchmark")
            variant.signingConfig.setConfig(android.signingConfigs.getByName("debug"))
        }
    }
}

kotlin {
    compilerOptions {
        // Uuid и kotlin.time.Instant доменной модели (как в :core:domain).
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.transfer)
    implementation(projects.core.ui)
    implementation(projects.feature.game)
    implementation(projects.feature.settlement)
    implementation(projects.feature.history)
    implementation(projects.feature.settings)
    implementation(projects.feature.handover)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    // Устанавливает Baseline Profile при установке не из Play (APK из Releases, adb).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.baselineprofile)

    testImplementation(libs.kotlinx.coroutines.test)
}
