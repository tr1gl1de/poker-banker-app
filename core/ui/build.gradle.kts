// Тема «Сукно», токены, общие компоненты.
plugins {
    alias(libs.plugins.feltbook.android.library)
    alias(libs.plugins.feltbook.android.compose)
}

android {
    namespace = "app.feltbook.core.ui"
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
}
