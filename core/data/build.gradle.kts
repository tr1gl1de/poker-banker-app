// Room, DataStore, репозитории, экспорт/импорт.
plugins {
    alias(libs.plugins.feltbook.android.library)
    alias(libs.plugins.feltbook.room)
    alias(libs.plugins.feltbook.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.feltbook.core.data"

    // Экспортированные схемы Room — ассеты unit-тестов: MigrationTestHelper читает их под Robolectric.
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        // Uuid и kotlin.time.Instant/Clock доменной модели (как в :core:domain).
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
