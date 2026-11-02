package app.feltbook.feature.history.debts

import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.feltbook.core.data.deletion.GameDeletionScheduler
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.feature.history.common.PreviewHistoryData
import app.feltbook.feature.history.common.PreviewHistoryData.id
import app.feltbook.feature.history.testing.FakeGameRepository
import app.feltbook.feature.history.testing.FakePlayerNameRepository
import app.feltbook.feature.history.testing.FakeSettingsRepository
import app.feltbook.feature.history.testing.MainDispatcherRule
import app.feltbook.feature.history.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebtsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val friday = PreviewHistoryData.fridayWithDebt()
    private val thursday = PreviewHistoryData.thursdayPartial()

    private fun TestScope.viewModel(repository: FakeGameRepository): DebtsViewModel = DebtsViewModel(
        gameRepository = repository,
        names = FakePlayerNameRepository(PreviewHistoryData.directory()),
        settings = FakeSettingsRepository(AppSettings(lastBankerName = "Миша")),
        deletions = GameDeletionScheduler(repository, backgroundScope),
        clock = TestClock(PreviewHistoryData.now),
    )

    private suspend fun ReceiveTurbine<DebtsUiState>.loaded(): DebtsUiState {
        var state = awaitItem()
        while (state.loading) state = awaitItem()
        return state
    }

    @Test
    fun summaryAndCardsOldestFirst() = runTest {
        val viewModel = viewModel(FakeGameRepository(listOf(thursday, friday)))
        viewModel.uiState.test {
            val state = loaded()
            assertEquals(1_053L + 600L, state.total)
            assertEquals(2, state.people)
            assertEquals(2, state.games)
            assertEquals(listOf("Влад", "Борис"), state.cards.map { it.playerName })
            val boris = state.cards.last()
            assertEquals(800L, boris.paidPart) // «отдал 800 из 1 400»
            assertEquals(1_400L, boris.owedAtExit)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun repayWritesPaymentIntoOldGameAndUndoReverses() = runTest {
        val repository = FakeGameRepository(listOf(friday))
        val viewModel = viewModel(repository)
        val vlad = friday.id("Влад")
        viewModel.uiState.test {
            val card = loaded().cards.single()
            viewModel.events.test {
                viewModel.onAction(DebtsAction.Pay(card.key))
                viewModel.onAction(DebtsAction.ConfirmPay)
                val event = awaitItem() as DebtsEvent.Undoable

                val paid = repository.snapshot(friday.game.id)
                val payment = paid.entries.last()
                assertEquals(EntryType.PAYMENT, payment.type)
                assertEquals(1_053L, payment.amount)
                assertEquals(vlad, payment.playerId)
                assertEquals("Миша", payment.bankerName)
                assertEquals(GameStatus.CLOSED, paid.game.status)
                assertFalse("repayment is not an edit after close", paid.game.editedAfterClose)
                assertEquals(PlayerStatus.CASHED_OUT, GameStates.compute(paid).player(vlad)?.status)

                viewModel.onAction(DebtsAction.Undo(event.token))
                val reverted = repository.snapshot(friday.game.id)
                assertEquals(EntryType.REVERSAL, reverted.entries.last().type)
                assertEquals(PlayerStatus.LEFT_WITH_DEBT, GameStates.compute(reverted).player(vlad)?.status)
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun partialRepaymentKeepsRestAsDebt() = runTest {
        val repository = FakeGameRepository(listOf(friday))
        val viewModel = viewModel(repository)
        viewModel.uiState.test {
            val card = loaded().cards.single()
            viewModel.onAction(DebtsAction.Pay(card.key))
            assertEquals(1_053L, awaitItem().paySheet?.amount)
            viewModel.onAction(DebtsAction.PayEntry(AmountEntry(2_000)))
            assertTrue("more than the debt", checkNotNull(awaitItem().paySheet).exceedsDebt)
            viewModel.onAction(DebtsAction.PayHalf)
            assertEquals(527L, awaitItem().paySheet?.amount) // половина с округлением вверх
            viewModel.onAction(DebtsAction.ConfirmPay)
            var state = awaitItem()
            while (state.paySheet != null || state.busy) state = awaitItem()
            assertEquals(1_053L - 527L, state.cards.single().amount)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun forgiveClosesDebtWithJournalEntry() = runTest {
        val repository = FakeGameRepository(listOf(friday))
        val viewModel = viewModel(repository)
        viewModel.uiState.test {
            val card = loaded().cards.single()
            viewModel.onAction(DebtsAction.Forgive(card.key))
            assertEquals(card.key, awaitItem().forgiveDialog?.key)
            viewModel.onAction(DebtsAction.ConfirmForgive)
            var state = awaitItem()
            while (state.cards.isNotEmpty()) state = awaitItem()
            assertNull(state.forgiveDialog)
            assertTrue(state.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
        val forgiven = repository.snapshot(friday.game.id).entries.last()
        assertEquals(EntryType.DEBT_FORGIVEN, forgiven.type)
        assertEquals(1_053L, forgiven.amount)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun forgiveReducesOldGameIncomeAndUndoRestoresDebt() = runTest {
        val repository = FakeGameRepository(listOf(friday))
        val viewModel = viewModel(repository)
        val vlad = friday.id("Влад")
        val before = GameStates.compute(friday).table
        viewModel.uiState.test {
            val card = loaded().cards.single()
            viewModel.events.test {
                viewModel.onAction(DebtsAction.Forgive(card.key))
                viewModel.onAction(DebtsAction.ConfirmForgive)
                val event = awaitItem() as DebtsEvent.Undoable

                val forgiven = GameStates.compute(repository.snapshot(friday.game.id)).table
                assertEquals(before.bankerIncome - 1_053L, forgiven.bankerIncome)
                val banker = forgiven.incomeByBanker.single { it.bankerName == friday.game.currentBankerName }
                assertEquals(1_053L, banker.forgiven)
                assertEquals(before.incomeByBanker.single().net - 1_053L, banker.net)

                viewModel.onAction(DebtsAction.Undo(event.token))
                val reverted = repository.snapshot(friday.game.id)
                assertEquals(EntryType.REVERSAL, reverted.entries.last().type)
                val state = GameStates.compute(reverted)
                assertEquals(PlayerStatus.LEFT_WITH_DEBT, state.player(vlad)?.status)
                assertEquals(1_053L, state.player(vlad)?.debt)
                assertEquals(before.bankerIncome, state.table.bankerIncome)
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }
}
