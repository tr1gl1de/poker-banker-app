package app.feltbook.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import app.feltbook.feature.game.navigation.GameNavigator
import app.feltbook.feature.game.navigation.HomeRoute
import app.feltbook.feature.game.navigation.NewGameRoute
import app.feltbook.feature.game.navigation.TableRoute
import app.feltbook.feature.game.navigation.gameScreens
import app.feltbook.feature.handover.HandoverRoute
import app.feltbook.feature.handover.handoverScreen
import app.feltbook.feature.history.DebtsRoute
import app.feltbook.feature.history.GameDetailRoute
import app.feltbook.feature.history.GameTabGameDetailRoute
import app.feltbook.feature.history.HistoryNavigator
import app.feltbook.feature.history.HistoryRoute
import app.feltbook.feature.history.StatsRoute
import app.feltbook.feature.history.debtsScreen
import app.feltbook.feature.history.historyScreens
import app.feltbook.feature.settings.OddsRoute
import app.feltbook.feature.settings.SettingsRoute
import app.feltbook.feature.settings.odds.oddsScreen
import app.feltbook.feature.settings.settingsScreens
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

private fun NavHostController.gameNavigator(
    onOpenHistory: () -> Unit,
    onOpenGameDetail: (String) -> Unit,
): GameNavigator = object : GameNavigator {
    override fun openNewGame() = navigate(NewGameRoute) { launchSingleTop = true }

    override fun openTable(gameId: String) = navigate(TableRoute(gameId)) { launchSingleTop = true }

    override fun gameStarted(gameId: String) = navigate(TableRoute(gameId)) {
        popUpTo<HomeRoute> { inclusive = false }
        launchSingleTop = true
    }

    override fun openHistory() = onOpenHistory()

    override fun openGameDetail(gameId: String) = onOpenGameDetail(gameId)

    override fun openDebts() = navigate(DebtsRoute) { launchSingleTop = true }

    override fun openSettlement(gameId: String) = navigate(SettlementRoute(gameId)) { launchSingleTop = true }

    override fun openHandover(gameId: String) = navigate(HandoverRoute(gameId))

    override fun openReceiveHandover() = navigate(HandoverRoute()) { launchSingleTop = true }

    override fun backToHome() {
        popBackStack<HomeRoute>(inclusive = false)
    }

    override fun back() {
        popBackStack()
    }
}

/**
 * Переходы экранов истории, статистики и долгов. Карточка игры открывается в текущей вкладке: из Истории —
 * [GameDetailRoute], из Главной и Долгов — [GameTabGameDetailRoute], так «Назад» возвращает на исходный экран.
 */
private fun NavHostController.historyNavigator(): HistoryNavigator = object : HistoryNavigator {
    private fun inHistory(): Boolean = currentBackStackEntry?.destination?.hierarchy?.any {
        it.hasRoute<HistoryGraph>()
    } == true

    override fun openGameDetail(gameId: String) {
        if (inHistory()) navigate(GameDetailRoute(gameId)) else navigate(GameTabGameDetailRoute(gameId))
    }

    override fun openStats() = navigate(StatsRoute) { launchSingleTop = true }

    override fun openDebts() {
        if (inHistory()) openTab(Tab.GAME, current = Tab.HISTORY)
        navigate(DebtsRoute) { launchSingleTop = true }
    }

    override fun openTable(gameId: String) {
        if (inHistory()) openTab(Tab.GAME, current = Tab.HISTORY)
        navigate(TableRoute(gameId)) { launchSingleTop = true }
    }

    override fun gameDeleted() {
        val previous = previousBackStackEntry?.destination
        val showsUndo = previous?.hasRoute<HistoryRoute>() == true || previous?.hasRoute<DebtsRoute>() == true
        popBackStack()
        // С Главной snackbar «Отменить» показать негде — он есть на вкладке «История».
        if (!showsUndo) openTab(Tab.HISTORY, current = Tab.GAME)
    }

    override fun openHandoverTemplate(gameId: String) = navigate(HandoverRoute(gameId))

    override fun back() {
        popBackStack()
    }
}

/** Граф приложения (m3-ui-spec §8.1); шторки — состояние экранов, не маршруты. */
@Composable
fun FeltbookNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val history = navController.historyNavigator()
    val navigator = navController.gameNavigator(
        onOpenHistory = { navController.openTab(Tab.HISTORY, current = Tab.GAME) },
        onOpenGameDetail = history::openGameDetail,
    )
    val context = LocalContext.current
    val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
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
            debtsScreen(history)
            handoverScreen(
                onBack = { navController.popBackStack() },
                onHandedOver = {
                    navController.navigate(HomeRoute) {
                        popUpTo<HomeRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onGameAccepted = { gameId ->
                    navController.navigate(TableRoute(gameId)) {
                        popUpTo<HomeRoute> { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onTemplateAccepted = {
                    navController.navigate(NewGameRoute) {
                        popUpTo<HomeRoute> { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        navigation<HistoryGraph>(startDestination = HistoryRoute) {
            historyScreens(history)
        }
        navigation<SettingsGraph>(startDestination = SettingsRoute) {
            settingsScreens(
                versionName = versionName,
                onOpenOdds = { navController.navigate(OddsRoute) { launchSingleTop = true } },
                onOpenHistory = { navController.openTab(Tab.HISTORY, current = Tab.SETTINGS) },
            )
            oddsScreen(onBack = { navController.popBackStack() })
        }
    }
}
