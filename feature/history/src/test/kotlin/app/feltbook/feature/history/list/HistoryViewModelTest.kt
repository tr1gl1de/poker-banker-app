package app.feltbook.feature.history.list

import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.feltbook.core.data.deletion.GameDeletionScheduler
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.ui.component.UNDO_SNACKBAR_MILLIS
import app.feltbook.feature.history.common.DeletionAction
import app.feltbook.feature.history.common.PreviewHistoryData
import app.feltbook.feature.history.common.PreviewHistoryData.add
import app.feltbook.feature.history.common.key
import app.feltbook.feature.history.testing.FakeGameRepository
import app.feltbook.feature.history.testing.MainDispatcherRule
import app.feltbook.feature.history.testing.TestClock
import app.feltbook.feature.history.testing.UtcZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val games = PreviewHistoryData.games()

    private fun TestScope.viewModel(
        repository: FakeGameRepository = FakeGameRepository(games),
        scope: CoroutineScope = backgroundScope,
    ): Pair<HistoryViewModel, FakeGameRepository> {
        val scheduler = GameDeletionScheduler(repository, scope)
        return HistoryViewModel(repository, scheduler, TestClock(PreviewHistoryData.now), UtcZone) to repository
    }

    private suspend fun ReceiveTurbine<HistoryUiState>.loaded(): HistoryUiState {
        var state = awaitItem()
        while (state.loading) state = awaitItem()
        return state
    }

    @Test
    fun closedGamesGroupedByMonthNewestFirst() {
        val running = PreviewHistoryData.started("Сегодня", PreviewHistoryData.now)
            .add("Андрей", 1_000, BuyInPayment.DEBT, PreviewHistoryData.now)
        val months =
            buildHistoryMonths(games + running, emptySet(), HistoryFilter.ALL, PreviewHistoryData.now, UtcZone.zone())

        assertEquals(listOf(2026 to 10, 2026 to 9), months.map { it.year to it.month })
        assertEquals(listOf("Четверг", "Субботний турнир", "Пятничный стол"), months[0].rows.map { it.title })
        assertFalse("the active game is not history", months.flatMap { it.rows }.any { it.title == "Сегодня" })
        val friday = months[0].rows.last()
        assertTrue(friday.hasOpenDebt)
        assertEquals(3_000L, friday.turnover)
        assertEquals(300L, friday.rake)
    }

    @Test
    fun monthFilterKeepsOnlyCurrentCalendarMonth() {
        // «Сейчас» — 2 ноября: в текущем месяце закрытых игр нет, за год — все четыре.
        val month = buildHistoryMonths(games, emptySet(), HistoryFilter.MONTH, PreviewHistoryData.now, UtcZone.zone())
        val year = buildHistoryMonths(games, emptySet(), HistoryFilter.YEAR, PreviewHistoryData.now, UtcZone.zone())
        assertTrue(month.isEmpty())
        assertEquals(4, year.sumOf { it.rows.size })
    }

    @Test
    fun longPressStartsSelectionAndClickToggles() = runTest {
        val (viewModel, _) = viewModel()
        val first = games[0].game.id.key()
        val second = games[1].game.id.key()
        viewModel.uiState.test {
            loaded()
            viewModel.onAction(HistoryAction.RowLongClick(first))
            assertEquals(setOf(first), awaitItem().selection)
            viewModel.onAction(HistoryAction.RowClick(second))
            assertEquals(setOf(first, second), awaitItem().selection)
            viewModel.onAction(HistoryAction.RowClick(first))
            assertEquals(setOf(second), awaitItem().selection)
            viewModel.onAction(HistoryAction.ClearSelection)
            assertFalse(awaitItem().selecting)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun clickWithoutSelectionOpensGame() = runTest {
        val (viewModel, _) = viewModel()
        val id = games[0].game.id.key()
        viewModel.events.test {
            viewModel.onAction(HistoryAction.RowClick(id))
            assertEquals(HistoryEvent.OpenGame(id), awaitItem())
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun deleteHidesRowsUntilWindowEndsAndUndoRestores() = runTest {
        val (viewModel, repository) = viewModel()
        val first = games[0].game.id.key()
        viewModel.uiState.test {
            loaded()
            viewModel.onAction(HistoryAction.RowLongClick(first))
            awaitItem()
            viewModel.onAction(HistoryAction.DeleteSelected)
            assertTrue(awaitItem().deleteDialog)
            viewModel.onAction(HistoryAction.ConfirmDelete)
            var state = awaitItem()
            while (state.pendingDeletion == null) state = awaitItem()
            assertFalse(state.months.flatMap { it.rows }.any { it.gameId == first })
            assertEquals(1, state.pendingDeletion?.count)
            assertTrue("hidden, not deleted", repository.deletedIds.isEmpty())

            viewModel.onAction(HistoryAction.Deletion(DeletionAction.Undo(checkNotNull(state.pendingDeletion).token)))
            state = awaitItem()
            assertTrue(state.months.flatMap { it.rows }.any { it.gameId == first })
            advanceTimeBy(UNDO_SNACKBAR_MILLIS * 2)
            runCurrent()
            assertTrue(repository.deletedIds.isEmpty())
            assertTrue(
                repository.games.value.values.any { it.game.status == GameStatus.CLOSED && it.game.id.key() == first },
            )
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun deleteCommitsAfterWindow() = runTest {
        val (viewModel, repository) = viewModel()
        val first = games[0].game.id.key()
        viewModel.uiState.test {
            loaded()
            viewModel.onAction(HistoryAction.RowLongClick(first))
            viewModel.onAction(HistoryAction.DeleteSelected)
            viewModel.onAction(HistoryAction.ConfirmDelete)
            runCurrent()
            advanceTimeBy(UNDO_SNACKBAR_MILLIS + 1)
            runCurrent()
            assertEquals(1, repository.deletedIds.size)
            assertFalse(repository.games.value.keys.any { it.key() == first })
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }
}
