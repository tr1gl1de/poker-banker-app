package app.feltbook.feature.history.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import app.feltbook.core.data.deletion.GameDeletionScheduler
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Journal
import app.feltbook.core.domain.engine.JournalItem
import app.feltbook.core.domain.engine.Ledger
import app.feltbook.core.domain.engine.OperationKind
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.format.UiText
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.PreviewHistoryData
import app.feltbook.feature.history.common.PreviewHistoryData.add
import app.feltbook.feature.history.common.key
import app.feltbook.feature.history.testing.FakeGameRepository
import app.feltbook.feature.history.testing.MainDispatcherRule
import app.feltbook.feature.history.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.viewModel(snapshot: GameSnapshot, others: List<GameSnapshot> = emptyList()) =
        FakeGameRepository(listOf(snapshot) + others).let { repository ->
            GameDetailViewModel(
                savedStateHandle = SavedStateHandle(mapOf(ARG_GAME_ID to snapshot.game.id.key())),
                gameRepository = repository,
                deletions = GameDeletionScheduler(repository, backgroundScope),
                clock = TestClock(PreviewHistoryData.now),
            ) to repository
        }

    @Test
    fun readOnlyCardShowsSettlementAndFullJournal() {
        val snapshot = PreviewHistoryData.thursdayPartial()
        val state = buildGameDetailUiState(snapshot, PreviewHistoryData.now, dialog = null, busy = false)

        assertEquals("Четверг", state.title)
        assertEquals(2, state.playerCount)
        assertTrue(state.hasOpenDebt)
        assertTrue(state.canReopen)
        val settlement = checkNotNull(state.settlement)
        assertTrue(settlement.chipsReconciled)
        assertEquals(600L, settlement.shortage)
        val boris = state.players.first { it.name == "Борис" }
        assertEquals(-600L, boris.outstanding)
        // Все операции журнала (без строк REVERSAL), новые сверху: погашение 600 — первое.
        assertEquals(Journal.items(snapshot).size, state.journal.size)
        assertEquals(600L, state.journal.first().amount)
    }

    @Test
    fun journalRowsStartWithPlayerNameAndNeverSayStorno() {
        val rows = JournalRows.of(PreviewHistoryData.saturdayTournament())
        val (withName, withoutName) =
            rows.partition { (it.title as UiText.Res).id == R.string.detail_journal_row_player }
        // Без игрока — только «Рейк отдан заведению» при закрытии.
        assertEquals(listOf(R.string.detail_journal_rake_paid), withoutName.map { (it.title as UiText.Res).id })
        assertTrue(withName.isNotEmpty())
        val rebuy = withName.first { row ->
            val title = row.title as UiText.Res
            (title.args[1] as? UiText.Concat)?.parts?.firstOrNull()?.let { (it as UiText.Res).id } ==
                R.string.detail_journal_buy_in_rebuy
        }
        assertEquals(1_000L, rebuy.amount)
        assertEquals("Андрей", (rebuy.title as UiText.Res).args[0])
    }

    @Test
    fun reopenTurnsGameActiveWithEditedMark() = runTest {
        val snapshot = PreviewHistoryData.fridayWithDebt()
        val (viewModel, repository) = viewModel(snapshot)
        viewModel.events.test {
            viewModel.onAction(GameDetailAction.Reopen)
            viewModel.onAction(GameDetailAction.ConfirmReopen)
            assertEquals(GameDetailEvent.OpenTable(snapshot.game.id.key()), awaitItem())
        }
        val game = repository.snapshot(snapshot.game.id).game
        assertEquals(GameStatus.RUNNING, game.status)
        assertTrue(game.editedAfterClose)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun reopenWhileAnotherGameRunsIsRejected() = runTest {
        val snapshot = PreviewHistoryData.fridayWithDebt()
        val running = PreviewHistoryData.started("Сегодня", PreviewHistoryData.now)
            .add("Андрей", 1_000, BuyInPayment.DEBT, PreviewHistoryData.now)
        val (viewModel, repository) = viewModel(snapshot, listOf(running))
        viewModel.events.test {
            viewModel.onAction(GameDetailAction.Reopen)
            viewModel.onAction(GameDetailAction.ConfirmReopen)
            val event = awaitItem() as GameDetailEvent.Message
            assertEquals(UiText.Res(UiR.string.error_another_game_active), event.text)
        }
        assertEquals(GameStatus.CLOSED, repository.snapshot(snapshot.game.id).game.status)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun closedGamePurchaseCannotBeReversedButLatePaymentCan() = runTest {
        val snapshot = PreviewHistoryData.thursdayPartial()
        val (viewModel, repository) = viewModel(snapshot)
        val operations = Journal.items(snapshot).filterIsInstance<JournalItem.Operation>()
        val purchase = operations.first { it.kind == OperationKind.FIRST_BUY_IN }
        val payment = operations.first { it.kind == OperationKind.PAYMENT }
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()

            viewModel.events.test {
                viewModel.onAction(GameDetailAction.JournalHold(purchase.operationId.key()))
                val message = awaitItem() as GameDetailEvent.Message
                assertEquals(UiText.Res(R.string.detail_journal_not_reversible_closed), message.text)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.onAction(GameDetailAction.JournalHold(payment.operationId.key()))
            state = awaitItem()
            val dialog = state.dialog as DetailDialog.Reverse
            assertEquals(payment.operationId.key(), dialog.operationId)
            viewModel.onAction(GameDetailAction.ReverseReason("ошиблись суммой"))
            awaitItem()
            viewModel.onAction(GameDetailAction.ConfirmReverse)
            cancelAndIgnoreRemainingEvents()
        }
        val after = repository.snapshot(snapshot.game.id)
        val reversal = after.entries.last()
        assertEquals(EntryType.REVERSAL, reversal.type)
        assertEquals("ошиблись суммой", reversal.note)
        assertFalse(Ledger.effective(after.entries).any { it.operationId == payment.operationId })
        assertEquals(GameStatus.CLOSED, after.game.status)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun deleteIsDeferredAndLeavesCard() = runTest {
        val snapshot = PreviewHistoryData.homeSeptember()
        val (viewModel, repository) = viewModel(snapshot)
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            viewModel.events.test {
                viewModel.onAction(GameDetailAction.Delete)
                viewModel.onAction(GameDetailAction.ConfirmDelete)
                assertEquals(GameDetailEvent.Deleted, awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue("deferred: nothing deleted yet", repository.deletedIds.isEmpty())
        assertNotNull(repository.games.value[snapshot.game.id])
        viewModel.viewModelScope.cancel()
    }
}
