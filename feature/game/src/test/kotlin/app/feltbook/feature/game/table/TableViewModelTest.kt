package app.feltbook.feature.game.table

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.repository.commit
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.feature.game.common.key
import app.feltbook.feature.game.common.toGameId
import app.feltbook.feature.game.table.PreviewTableData.id
import app.feltbook.feature.game.testing.FakeGameRepository
import app.feltbook.feature.game.testing.FakePlayerNameRepository
import app.feltbook.feature.game.testing.MainDispatcherRule
import app.feltbook.feature.game.testing.TestClock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TableViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = TestClock(PreviewTableData.start + 2.hours)
    private lateinit var repository: FakeGameRepository
    private lateinit var viewModel: TableViewModel
    private lateinit var gameKey: String

    private fun open(
        snapshot: GameSnapshot = PreviewTableData.running(),
        extra: List<GameSnapshot> = emptyList(),
        cashOut: String? = null,
    ) {
        repository = FakeGameRepository(listOf(snapshot) + extra)
        gameKey = snapshot.game.id.key()
        val args = buildMap<String, Any?> {
            put(TableViewModel.ARG_GAME_ID, gameKey)
            if (cashOut != null) put(TableViewModel.ARG_CASH_OUT, cashOut)
        }
        val names = FakePlayerNameRepository(listOf(PlayerName("Влад", PreviewTableData.start - 30.hours)))
        viewModel = TableViewModel(SavedStateHandle(args), repository, names, clock)
    }

    private val snapshot: GameSnapshot get() = repository.snapshot(gameKey.toGameId())

    private fun playerKey(name: String): String = snapshot.id(name).key()

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
    }

    @Test
    fun rowsFollowSeatingAndStatuses() {
        open()
        val state = buildTableUiState(snapshot, clock.now(), TableInputs())
        assertEquals(listOf("Андрей", "Влад", "Борис"), state.rows.map { it.name })
        val andrey = state.rows.first()
        assertEquals(1, andrey.rebuyCount)
        assertEquals(-2_300L, andrey.outstanding)
        assertEquals(PlayerStatus.CASHED_OUT, state.rows.last().status)
        assertEquals(500L, state.board.rakeDue)
        assertEquals(353L, state.board.bankerIncome) // сборы 300 + чаевые 53
    }

    @Test
    fun rebuyCommitsAndUndoReverses() = runTest {
        open()
        val andrey = playerKey("Андрей")
        viewModel.events.test {
            viewModel.onAction(TableAction.SwipeBuyIn(andrey))
            viewModel.onAction(TableAction.ChipSelect(1_000))
            viewModel.onAction(TableAction.ConfirmBuyIn)
            assertEquals(TableEvent.ConfirmHaptic, awaitItem())
            val undo = awaitItem() as TableEvent.Undoable
            assertNull(viewModel.currentUiState().sheet)
            val state = GameStates.compute(snapshot).player(snapshot.id("Андрей"))!!
            assertEquals(3_000L, state.chipsBought)
            assertEquals(2, state.rebuyCount)

            viewModel.onAction(TableAction.Undo(undo.token))
            val after = GameStates.compute(snapshot).player(snapshot.id("Андрей"))!!
            assertEquals(2_000L, after.chipsBought)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun secondTapOnStaleSnapshotIsIgnored() = runTest {
        open()
        val vlad = playerKey("Влад")
        viewModel.onAction(TableAction.SwipeBuyIn(vlad))
        viewModel.onAction(TableAction.ChipSelect(500))
        // Пока пишем, другой «тап» успел записать докуп по тому же снимку: наш — STALE_SEQ.
        repository.beforeCommit = {
            val current = snapshot
            commit(Commands.buyIn(current, current.id("Влад"), 500, BuyInPayment.DEBT, Stamp(clock.now())))
        }
        viewModel.events.test {
            viewModel.onAction(TableAction.ConfirmBuyIn)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.commitCount)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun belowMinimumCannotBeConfirmed() {
        open()
        viewModel.onAction(TableAction.SwipeBuyIn(playerKey("Влад")))
        viewModel.onAction(TableAction.EntryChange(AmountEntry(300)))
        val sheet = viewModel.currentUiState().sheet as BuyInUi
        assertTrue(sheet.belowMin)
        assertFalse(sheet.canConfirm)
        viewModel.onAction(TableAction.ConfirmBuyIn)
        assertEquals(0, repository.commitCount)
    }

    @Test
    fun newPlayerWithOldDebtDefaultsToPaidNow() {
        val table = PreviewTableData.started()
        open(table, extra = listOf(PreviewTableData.oldGameWithDebt()))
        viewModel.onAction(TableAction.AddPlayer)
        viewModel.onAction(TableAction.NameChange("Влад"))
        viewModel.onAction(TableAction.EntryChange(AmountEntry(1_000)))
        val ui = viewModel.currentUiState().sheet as NewPlayerUi
        assertNotNull(ui.debt)
        assertEquals(1_053L, ui.debt!!.total)
        assertEquals(BuyInPayment.PAID, ui.payment)
        viewModel.onAction(TableAction.ConfirmBuyIn)
        val vlad = GameStates.compute(snapshot).player(snapshot.id("Влад"))!!
        assertEquals(0L, vlad.debt)
        assertEquals(1_200L, vlad.paid)
    }

    @Test
    fun cashOutWithTipToBankerPaysOut() = runTest {
        open()
        val vlad = playerKey("Влад")
        viewModel.events.test {
            viewModel.onAction(TableAction.SwipeCashOut(vlad))
            viewModel.onAction(TableAction.EntryChange(AmountEntry(2_550)))
            viewModel.onAction(TableAction.KeypadDone)
            viewModel.onAction(TableAction.TipSelect(TipChoice.ROUND_100))
            viewModel.onAction(TableAction.SettleFull)
            val event = awaitItem() as TableEvent.Undoable
            val token = event.token as UndoToken.Reverse
            assertEquals(2, token.operations.size) // расчёт и выход
            cancelAndIgnoreRemainingEvents()
        }
        val state = GameStates.compute(snapshot).player(snapshot.id("Влад"))!!
        // W = 2 550 − 50 = 2 500; начислено 2 300 → выплачено 200.
        assertEquals(PlayerStatus.CASHED_OUT, state.status)
        assertEquals(2_500L, state.withdrawal)
        assertEquals(200L, state.paidOut)
        assertEquals(50L, state.tipsToBanker)
    }

    @Test
    fun earlyExitAsksForConfirmation() {
        clock.current = PreviewTableData.start + 30.minutes
        open()
        viewModel.onAction(TableAction.SwipeCashOut(playerKey("Влад")))
        viewModel.onAction(TableAction.EntryChange(AmountEntry(2_000)))
        viewModel.onAction(TableAction.SettleNone)
        assertEquals(0, repository.commitCount)
        assertTrue(viewModel.currentUiState().dialog is DialogUi.EarlyExit)
        viewModel.onAction(TableAction.DialogConfirm)
        val exit = snapshot.entries.last { it.type == EntryType.CASH_OUT }
        assertTrue(exit.earlyExit)
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, GameStates.compute(snapshot).player(snapshot.id("Влад"))!!.status)
    }

    @Test
    fun cashOutParameterOpensSheet() {
        val running = PreviewTableData.running()
        open(running, cashOut = running.id("Влад").key())
        assertTrue(viewModel.currentUiState().sheet is CashOutUi)
    }

    @Test
    fun finishMovesToSettling() = runTest {
        open()
        viewModel.events.test {
            viewModel.onAction(TableAction.Finish)
            assertEquals(TableEvent.OpenSettlement, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(GameStatus.SETTLING, snapshot.game.status)
    }

    @Test
    fun journalHoldReversesAfterDialog() {
        open()
        val andrey = playerKey("Андрей")
        viewModel.onAction(TableAction.RowClick(andrey))
        val card = viewModel.currentUiState().sheet as PlayerCardUi
        val rebuy = card.journal.first() // новые сверху: докуп 1 000
        viewModel.onAction(TableAction.JournalHold(andrey, rebuy.operationId))
        assertTrue(viewModel.currentUiState().dialog is DialogUi.Reverse)
        viewModel.onAction(TableAction.ReasonChange("ошибся"))
        viewModel.onAction(TableAction.DialogConfirm)
        val reversal = snapshot.entries.last()
        assertEquals(EntryType.REVERSAL, reversal.type)
        assertEquals("ошибся", reversal.note)
        assertEquals(1_000L, GameStates.compute(snapshot).player(snapshot.id("Андрей"))!!.chipsBought)
    }

    @Test
    fun firstBuyInWithLaterRebuyIsNotReversible() = runTest {
        open()
        val andrey = playerKey("Андрей")
        viewModel.onAction(TableAction.RowClick(andrey))
        val card = viewModel.currentUiState().sheet as PlayerCardUi
        val first = card.journal.last()
        viewModel.events.test {
            viewModel.onAction(TableAction.JournalHold(andrey, first.operationId))
            assertTrue(awaitItem() is TableEvent.Message)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(viewModel.currentUiState().dialog)
    }
}
