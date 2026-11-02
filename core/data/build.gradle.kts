// Room, DataStore, репозитории, экспорт/импорт.
plugins {
    alias(libs.plugins.feltbook.android.library)
    alias(libs.plugins.feltbook.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

android {
    namespace = "app.feltbook.core.data"
}

room {
    // Схемы в репозитории — для тестов миграций.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
