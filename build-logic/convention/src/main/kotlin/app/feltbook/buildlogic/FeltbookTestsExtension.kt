package app.feltbook.buildlogic

import org.gradle.api.provider.Property

/**
 * Настройки тестов модуля (`feltbookTests { … }` в build.gradle.kts).
 *
 * [allowNoDiscoveredTests] — opt-in для модулей-заглушек: Hilt/KSP генерируют test-исходники и там, где
 * тестов ещё нет, и Gradle падает с «no tests discovered». В остальных модулях пустой прогон — ошибка
 * (сломанный раннер не должен выглядеть зелёным).
 * TODO(M3): убрать opt-in из модулей, когда в них появятся тесты, затем и само свойство.
 */
abstract class FeltbookTestsExtension {
    abstract val allowNoDiscoveredTests: Property<Boolean>
}
