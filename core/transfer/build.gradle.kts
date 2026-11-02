// Nearby Connections, формат пакета .feltbook, проверка целостности.
plugins {
    alias(libs.plugins.feltbook.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.feltbook.core.transfer"
}

dependencies {
    api(projects.core.domain)
    implementation(libs.play.services.nearby)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
}
