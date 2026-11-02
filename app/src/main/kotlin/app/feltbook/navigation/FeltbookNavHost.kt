package app.feltbook.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.feltbook.R
import app.feltbook.feature.game.navigation.GameNavigator
import app.feltbook.feature.game.navigation.HomeRoute
import app.feltbook.feature.game.navigation.NewGameRoute
import app.feltbook.feature.game.navigation.TableRoute
import app.feltbook.feature.game.navigation.gameScreens
import app.feltbook.feature.handover.HandoverScreen
import app.feltbook.feature.history.HistoryScreen
import app.feltbook.feature.settings.SettingsScreen
import app.feltbook.feature.settlement.SettlementRoute
import app.feltbook.feature.settlement.settlementScreen

/** Переход на вкладку с сохранением стека (`saveState/restoreState`); повтор — к корню вкладки. */
fun NavHostController.openTab(tab: Tab, current: Tab?) {
    if (tab == current) {
        when (tab) {
            Tab.GAME -> popBackStack<HomeRoute>(inclusive = false)
            Tab.HISTORY -> popBackStack<HistoryRoute>(inclusive = false)
            Tab.SETTINGS -> popBackStack<SettingsRoute>(inclusive = false)
        }
        return
    }
    val target: Any = when (tab) {
        Tab.GAME -> GameGraph
        Tab.HISTORY -> HistoryGraph
        Tab.SETTINGS -> SettingsGraph
    }
    navigate(target) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.gameNavigator(onOpenHistory: () -> Unit): GameNavigator = object : GameNavigator {
    override fun openNewGame() = navigate(NewGameRoute) { launchSingleTop = true }

    override fun openTable(gameId: String) = navigate(TableRoute(gameId)) { launchSingleTop = true }

    override fun gameStarted(gameId: String) = navigate(TableRoute(gameId)) {
        popUpTo<HomeRoute> { inclusive = false }
        launchSingleTop = true
    }

    override fun openHistory() = onOpenHistory()

    override fun openGameDetail(gameId: String) = navigate(GameDetailRoute(gameId))

    override fun openDebts() = navigate(DebtsRoute) { launchSingleTop = true }

    override fun openSettlement(gameId: String) = navigate(SettlementRoute(gameId)) { launchSingleTop = true }

    override fun openHandover(gameId: String) = navigate(HandoverRoute(gameId))

    override fun backToHome() {
        popBackStack<HomeRoute>(inclusive = false)
    }

    override fun back() {
        popBackStack()
    }
}

/** Граф приложения (m3-ui-spec §8.1); шторки — состояние экранов, не маршруты. */
@Composable
fun FeltbookNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val navigator = navController.gameNavigator(onOpenHistory = {
        navController.openTab(Tab.HISTORY, current = Tab.GAME)
    })
    NavHost(navController = navController, startDestination = GameGraph, modifier = modifier) {
        navigation<GameGraph>(startDestination = HomeRoute) {
            gameScreens(navigator)
            settlementScreen(
                onBack = { navController.popBackStack() },
                onClosed = {
                    navController.navigate(HomeRoute) {
                        popUpTo<HomeRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onCashOut = { gameId, playerId ->
                    navController.navigate(TableRoute(gameId, cashOutPlayerId = playerId)) {
                        popUpTo<TableRoute> { inclusive = true }
                    }
                },
            )
            composable<DebtsRoute> {
                PlaceholderScreen(title = stringResource(R.string.placeholder_debts), onBack = {
                    navController.popBackStack()
                })
            }
            composable<HandoverRoute> {
                PlaceholderScreen(title = stringResource(R.string.placeholder_handover), onBack = {
                    navController.popBackStack()
                }) {
                    HandoverScreen()
                }
            }
        }
        navigation<HistoryGraph>(startDestination = HistoryRoute) {
            composable<HistoryRoute> {
                PlaceholderScreen(title = stringResource(R.string.nav_history)) { HistoryScreen() }
            }
            composable<GameDetailRoute> {
                PlaceholderScreen(title = stringResource(R.string.placeholder_game_detail), onBack = {
                    navController.popBackStack()
                })
            }
        }
        navigation<SettingsGraph>(startDestination = SettingsRoute) {
            composable<SettingsRoute> {
                PlaceholderScreen(title = stringResource(R.string.nav_settings)) { SettingsScreen() }
            }
        }
    }
}
