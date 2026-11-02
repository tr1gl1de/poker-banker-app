package app.feltbook.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.feltbook.feature.settings.main.SettingsRouteScreen
import kotlinx.serialization.Serializable

/** Настройки — старт вкладки «Настройки» (нижняя панель видна). */
@Serializable
data object SettingsRoute

/**
 * Калькулятор шансов (пасхалка, M6.2). Экран появится на этапе M6 — пока `:app` регистрирует для маршрута
 * заглушку; пункт в настройках виден только при `AppSettings.oddsCalculatorVisible`.
 */
@Serializable
data object OddsRoute

/**
 * Экран настроек. [versionName] — `BuildConfig.VERSION_NAME` приложения (подпись «Feltbook 1.0 · open source …»);
 * [onOpenOdds] — переход на [OddsRoute]; [onOpenHistory] — «К истории» из отчёта восстановления.
 */
fun NavGraphBuilder.settingsScreens(versionName: String, onOpenOdds: () -> Unit, onOpenHistory: () -> Unit) {
    composable<SettingsRoute> {
        SettingsRouteScreen(versionName = versionName, onOpenOdds = onOpenOdds, onOpenHistory = onOpenHistory)
    }
}
