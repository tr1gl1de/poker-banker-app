package app.feltbook.feature.history

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.feltbook.feature.history.debts.DebtsRouteScreen
import app.feltbook.feature.history.detail.GameDetailRouteScreen
import app.feltbook.feature.history.list.HistoryRouteScreen
import app.feltbook.feature.history.stats.StatsRouteScreen
import kotlinx.serialization.Serializable

/** История — старт вкладки «История» (нижняя панель видна, в т. ч. в мультивыборе). */
@Serializable
data object HistoryRoute

/** Карточка игры только для чтения; имя поля = аргумент `SavedStateHandle` в `GameDetailViewModel`. */
@Serializable
data class GameDetailRoute(val gameId: String)

/**
 * Та же карточка игры во вкладке «Игра» (из Главной и «Открытых долгов»): «Назад» возвращает туда, откуда открыли.
 * Отдельный маршрут, а не [GameDetailRoute], — у каждой вкладки свой стек. Имя поля = аргумент `GameDetailViewModel`.
 */
@Serializable
data class GameTabGameDetailRoute(val gameId: String)

/** Статистика (из Истории), панель скрыта. */
@Serializable
data object StatsRoute

/** Открытые долги (вкладка «Игра», панель видна). */
@Serializable
data object DebtsRoute

/** Переходы экранов истории, статистики и долгов; сами переходы описывает `:app`. */
interface HistoryNavigator {
    fun openGameDetail(gameId: String)

    fun openStats()

    fun openDebts()

    /** Игра переоткрыта для исправления — её стол (`TableRoute(gameId)` из `:feature:game`). */
    fun openTable(gameId: String)

    /**
     * Игра удалена из карточки — назад, на экран со snackbar «Удалено 1 игра» / «Отменить» (История или Долги);
     * если карточку открыли с Главной — на вкладку «История».
     */
    fun gameDeleted()

    fun back()
}

/** История, карточка игры и статистика — в граф вкладки «История». */
fun NavGraphBuilder.historyScreens(navigator: HistoryNavigator) {
    composable<HistoryRoute> {
        HistoryRouteScreen(onOpenGame = navigator::openGameDetail, onOpenStats = navigator::openStats)
    }
    gameDetailScreen(navigator)
    composable<StatsRoute> {
        StatsRouteScreen(onBack = navigator::back, onOpenDebts = navigator::openDebts)
    }
}

private fun NavGraphBuilder.gameDetailScreen(navigator: HistoryNavigator) {
    composable<GameDetailRoute> { GameDetailDestination(navigator) }
}

@Composable
private fun GameDetailDestination(navigator: HistoryNavigator) {
    GameDetailRouteScreen(
        onBack = navigator::back,
        onOpenTable = navigator::openTable,
        onDeleted = navigator::gameDeleted,
    )
}

/** «Открытые долги» и карточка игры [GameTabGameDetailRoute] — в граф вкладки «Игра». */
fun NavGraphBuilder.debtsScreen(navigator: HistoryNavigator) {
    composable<DebtsRoute> {
        DebtsRouteScreen(
            onBack = navigator::back,
            onOpenGame = navigator::openGameDetail,
            onOpenTable = navigator::openTable,
        )
    }
    composable<GameTabGameDetailRoute> { GameDetailDestination(navigator) }
}
