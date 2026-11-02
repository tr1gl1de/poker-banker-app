plugins {
    alias(libs.plugins.feltbook.android.feature)
}

android {
    namespace = "app.feltbook.feature.history"
}

kotlin {
    compilerOptions {
        // Uuid и kotlin.time.Instant доменной модели (как в :core:domain).
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }
}
