plugins {
    alias(libs.plugins.feltbook.android.feature)
}

android {
    namespace = "app.feltbook.feature.game"
}

kotlin {
    compilerOptions {
        // Uuid и kotlin.time.Instant доменной модели (как в :core:domain).
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    // Стол на планшете: ListDetailPaneScaffold (m3-ui-spec §9).
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
}
