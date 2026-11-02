package app.feltbook.feature.game.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.feltbook.feature.game.home.HomeRouteScreen
import app.feltbook.feature.game.newgame.NewGameRouteScreen
import app.feltbook.feature.game.table.TableRouteScreen
import kotlinx.serialization.Serializable

/** Главная вкладки «Игра». */
@Serializable
data object HomeRoute

@Serializable
data object NewGameRoute

/**
 * Стол. [cashOutPlayerId] — сразу открыть шторку выхода этого игрока (из итога игры: «За столом ещё 2 игрока»).
 * Имена полей = аргументы `SavedStateHandle` в `TableViewModel` (`gameId`, `cashOutPlayerId`).
 */
@Serializable
data class TableRoute(val gameId: String, val cashOutPlayerId: String? = null)

/** Колбэки навигации экранов вкладки «Игра»; сами переходы описывает `:app`. */
interface GameNavigator {
    fun openNewGame()

    fun openTable(gameId: String)

    /** Игра начата: стол поверх Главной (NewGame убирается из стека). */
    fun gameStarted(gameId: String)

    fun openHistory()

    fun openGameDetail(gameId: String)

    fun openDebts()

    fun openSettlement(gameId: String)

    fun openHandover(gameId: String)

    /** Игры больше нет (удалена/закрыта) — на Главную. */
    fun backToHome()

    fun back()
}

fun NavGraphBuilder.gameScreens(navigator: GameNavigator) {
    composable<HomeRoute> {
        HomeRouteScreen(
            onNewGame = navigator::openNewGame,
            onOpenTable = navigator::openTable,
            onOpenHistory = navigator::openHistory,
            onOpenGame = navigator::openGameDetail,
            onOpenDebts = navigator::openDebts,
        )
    }
    composable<NewGameRoute> {
        NewGameRouteScreen(
            onBack = navigator::back,
            onStarted = navigator::gameStarted,
            onOpenTable = navigator::openTable,
        )
    }
    composable<TableRoute> { entry ->
        val gameId = entry.toRoute<TableRoute>().gameId
        TableRouteScreen(
            onOpenSettlement = { navigator.openSettlement(gameId) },
            onOpenHandover = { navigator.openHandover(gameId) },
            onGameGone = navigator::backToHome,
        )
    }
}
