package app.feltbook.feature.game.home

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.feature.game.common.key
import app.feltbook.feature.game.table.PreviewTableData
import app.feltbook.feature.game.testing.FakeGameRepository
import app.feltbook.feature.game.testing.FakePlayerNameRepository
import app.feltbook.feature.game.testing.MainDispatcherRule
import app.feltbook.feature.game.testing.TestClock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun activeGameCardShowsTableSummary() {
        val running = PreviewTableData.running()
        val state = buildHomeUiState(listOf(running), emptyList(), running.game.createdAt + 134.minutes)
        val active = checkNotNull(state.active)
        assertEquals(running.game.id.key(), active.gameId)
        assertEquals(134.minutes, active.elapsed)
        assertEquals(2, active.playersAtTable) // Андрей и Влад; Борис вышел
        assertEquals(5_000L - 3_753L, active.chipsInPlay) // выдано 5 000, Борис сдал 3 753
        assertEquals(2_300L + 2_300L, active.playersOwe)
        assertEquals(500L, active.rakeDue)
        assertFalse(active.settling)
    }

    @Test
    fun pastGamesAndOpenDebts() {
        val old = PreviewTableData.oldGameWithDebt()
        val state = buildHomeUiState(listOf(old), listOf(PlayerName("Влад", old.game.createdAt)), old.game.createdAt)
        assertNull(state.active)
        assertEquals(1, state.pastGames.size)
        assertTrue(state.pastGames.single().hasOpenDebt)
        val debts = checkNotNull(state.openDebts)
        assertEquals(1_053L, debts.total)
        assertEquals(1, debts.people)
        assertEquals(1, debts.games)
    }

    @Test
    fun newGameWhileGameIsRunningShowsDialog() = runTest {
        val repository = FakeGameRepository(listOf(PreviewTableData.running()))
        val viewModel = HomeViewModel(repository, FakePlayerNameRepository(), TestClock())
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            viewModel.onAction(HomeAction.NewGame)
            assertTrue(awaitItem().alreadyRunningDialog)
            viewModel.onAction(HomeAction.DismissDialog)
            assertFalse(awaitItem().alreadyRunningDialog)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun newGameWithoutActiveGameDoesNotShowDialog() = runTest {
        val viewModel = HomeViewModel(FakeGameRepository(), FakePlayerNameRepository(), TestClock())
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            viewModel.onAction(HomeAction.NewGame)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }
}
