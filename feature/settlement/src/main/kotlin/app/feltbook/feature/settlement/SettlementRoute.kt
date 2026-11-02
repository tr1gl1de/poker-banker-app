package app.feltbook.feature.settlement

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Итог игры; имя поля = аргумент `SavedStateHandle` в [SettlementViewModel]. */
@Serializable
data class SettlementRoute(val gameId: String)

fun NavGraphBuilder.settlementScreen(
    onBack: () -> Unit,
    onClosed: () -> Unit,
    onCashOut: (gameId: String, playerId: String) -> Unit,
) {
    composable<SettlementRoute> {
        SettlementRouteScreen(onBack = onBack, onClosed = onClosed, onCashOut = onCashOut)
    }
}
