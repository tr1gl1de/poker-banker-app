// Room, DataStore, репозитории, экспорт/импорт.
plugins {
    alias(libs.plugins.feltbook.android.library)
    alias(libs.plugins.feltbook.room)
    alias(libs.plugins.feltbook.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.feltbook.core.data"
}

feltbookTests {
    // Тестов пока нет, а Hilt/KSP генерируют test-исходники. TODO(M3): убрать, когда появятся тесты.
    allowNoDiscoveredTests = true
}

dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
