package app.feltbook.feature.settings.odds

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.feltbook.feature.settings.OddsRoute

/** Калькулятор шансов на [OddsRoute] (без нижней панели); [onBack] — назад к настройкам. */
fun NavGraphBuilder.oddsScreen(onBack: () -> Unit) {
    composable<OddsRoute> { OddsRouteScreen(onBack = onBack) }
}
