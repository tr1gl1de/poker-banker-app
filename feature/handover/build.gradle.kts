plugins {
    alias(libs.plugins.feltbook.android.feature)
}

android {
    namespace = "app.feltbook.feature.handover"
}

kotlin {
    compilerOptions {
        // Uuid и kotlin.time.Instant доменной модели (как в :core:domain).
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    // Передача по воздуху и файлом (ТЗ §6).
    implementation(projects.core.transfer)
    // Запрос разрешений в момент передачи и Sharesheet (rememberLauncherForActivityResult).
    implementation(libs.androidx.activity.compose)
}
