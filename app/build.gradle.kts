plugins {
    alias(libs.plugins.feltbook.android.application)
    alias(libs.plugins.feltbook.android.compose)
    alias(libs.plugins.feltbook.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Подпись release — только секретами CI (release.yml декодирует keystore и задаёт переменные окружения).
// Без них release собирается неподписанным (app-release-unsigned.apk) — артефакт для проверки.
val keystorePath: String? = providers.environmentVariable("FELTBOOK_KEYSTORE_FILE").orNull

android {
    namespace = "app.feltbook"

    defaultConfig {
        applicationId = "app.feltbook"
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
    implementation(libs.androidx.profileinstaller)
}
