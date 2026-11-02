// Транспорт передачи комнаты: Nearby Connections, файл .feltbook через FileProvider/Sharesheet, приём файла.
// Формат пакета (DTO, FeltbookCodec, SHA-256) — в :core:domain (ADR 0007).
plugins {
    alias(libs.plugins.feltbook.android.library)
    alias(libs.plugins.feltbook.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.feltbook.core.transfer"
}

kotlin {
    compilerOptions {
        // Uuid и kotlin.time.Instant доменной модели (как в :core:domain).
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    api(projects.core.domain)
    implementation(libs.play.services.nearby)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
}
