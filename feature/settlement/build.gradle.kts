plugins {
    alias(libs.plugins.feltbook.android.feature)
}

android {
    namespace = "app.feltbook.feature.settlement"
}

feltbookTests {
    // Заглушка: тестов пока нет, а Hilt/KSP генерируют test-исходники. TODO(M3): убрать, когда появятся тесты.
    allowNoDiscoveredTests = true
}
