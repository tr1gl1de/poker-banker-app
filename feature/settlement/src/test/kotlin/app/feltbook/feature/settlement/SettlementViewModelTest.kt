package app.feltbook.feature.settlement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettlementViewModelTest {
    private lateinit var repository: FakeGameRepository
    private lateinit var viewModel: SettlementViewModel
    private lateinit var snapshot: GameSnapshot

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun open(game: GameSnapshot) {
        snapshot = game
        repository = FakeGameRepository(listOf(game))
        viewModel = SettlementViewModel(
            SavedStateHandle(mapOf(SettlementViewModel.ARG_GAME_ID to game.game.id.value.toString())),
            repository,
            TestClock(),
        )
    }

    @Test
    fun spec36Figures() {
        val state = buildSettlementUiState(PreviewSettlementData.snapshot(), TestClock().now())
        val settlement = checkNotNull(state.settlement)
        assertTrue(settlement.chipsReconciled)
        assertEquals(5_000L, settlement.chipsIssued)
        assertEquals(500L, settlement.rakeDue)
        assertEquals(353L, settlement.bankerIncome)
        assertEquals(853L, settlement.expectedCash)
        assertEquals(853L, settlement.cashOnHand)
        assertFalse(state.hasDebts)
        assertTrue(state.canClose)
    }

    @Test
    fun closeWritesRakePaidAndClosesGame() = runTest {
        open(PreviewSettlementData.snapshot())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.events.test {
            viewModel.onAction(SettlementAction.Close)
            assertEquals(SettlementEvent.Closed, awaitItem())
        }
        val closed = repository.snapshot(snapshot.game.id)
        assertEquals(GameStatus.CLOSED, closed.game.status)
        assertEquals(EntryType.RAKE_PAID, closed.entries.last().type)
        assertEquals(500L, closed.entries.last().amount)
    }

    @Test
    fun closeWithDebtIsAllowedByHold() = runTest {
        open(PreviewSettlementData.snapshot(vladPays = false))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.events.test {
            viewModel.onAction(SettlementAction.Close)
            assertEquals(SettlementEvent.Closed, awaitItem())
        }
        assertEquals(GameStatus.CLOSED, repository.snapshot(snapshot.game.id).game.status)
    }

    @Test
    fun chipsMismatchNeedsDialogConfirmation() = runTest {
        // Влад сдал 1 200 вместо 1 247: сдано на 47 фишек меньше.
        open(PreviewSettlementData.snapshot(vladChips = 1_200))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.events.test {
            viewModel.onAction(SettlementAction.Close)
            expectNoEvents()
            assertTrue(viewModel.uiState.value.chipsDialog)
            assertEquals(GameStatus.SETTLING, repository.snapshot(snapshot.game.id).game.status)

            viewModel.onAction(SettlementAction.ConfirmChipsMismatch)
            assertEquals(SettlementEvent.Closed, awaitItem())
        }
        assertFalse(viewModel.uiState.value.chipsDialog)
        assertEquals(GameStatus.CLOSED, repository.snapshot(snapshot.game.id).game.status)
    }

    @Test
    fun closeIgnoresStaleDisplayedState() = runTest {
        open(PreviewSettlementData.snapshot())
        // Без подписчика итог не показан — закрывать нечего.
        viewModel.onAction(SettlementAction.Close)
        assertEquals(GameStatus.SETTLING, repository.snapshot(snapshot.game.id).game.status)
    }

    @Test
    fun playersStillAtTableBlockClosing() {
        val state = PreviewSettlementData.stillPlaying()
        assertEquals(2, state.stillPlaying.size)
        assertFalse(state.canClose)
    }
}
