package app.feltbook.feature.settings.odds

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import app.feltbook.core.odds.Card
import app.feltbook.core.odds.EquityCalculator
import app.feltbook.core.odds.EquityRequest
import app.feltbook.core.odds.EquitySettings
import app.feltbook.core.odds.HandCategory
import app.feltbook.core.odds.PotOddsVerdict
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.feature.settings.MainDispatcherRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric — ради `SavedStateHandle.set` (проверка типов значений обращается к классам Android). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OddsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val created = mutableListOf<OddsViewModel>()

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
    }

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        dispatcher: CoroutineDispatcher = main.dispatcher,
    ): OddsViewModel = OddsViewModel(handle, OddsEngine(Settings, dispatcher)).also(created::add)

    private fun OddsViewModel.pick(slot: CardSlot, vararg cards: String) {
        onAction(OddsAction.OpenSlot(slot))
        cards.forEach { onAction(OddsAction.PickCard(Card.parse(it))) }
    }

    private fun OddsViewModel.amount(field: OddsAmountField, value: Long) {
        onAction(OddsAction.OpenAmount(field))
        onAction(OddsAction.AmountInput(AmountEntry(value)))
        onAction(OddsAction.ConfirmAmount)
    }

    private val OddsViewModel.state: OddsUiState get() = uiState.value

    private fun <T : Any> notNull(value: T?): T = checkNotNull(value) { "ожидалось значение" }

    @Test
    fun holeCardsFillInOrderAndPickerCloses() {
        val viewModel = viewModel()
        viewModel.onAction(OddsAction.OpenSlot(CardSlot.Hole(0)))
        viewModel.onAction(OddsAction.PickCard(Card.parse("As")))
        assertEquals(CardSlot.Hole(1), viewModel.state.picker?.slot)
        viewModel.onAction(OddsAction.PickCard(Card.parse("Ks")))
        assertNull(viewModel.state.picker)
        assertEquals(Card.parseList("As Ks"), viewModel.state.hole)
        assertEquals(EquityStatus.READY, viewModel.state.status)
    }

    @Test
    fun occupiedCardIsUnavailableAndIgnored() {
        val viewModel = viewModel()
        viewModel.pick(CardSlot.Hole(0), "As", "Ks")
        viewModel.onAction(OddsAction.OpenSlot(CardSlot.Board(0)))
        val picker = notNull(viewModel.state.picker)
        assertTrue(Card.parse("As") in picker.unavailable)
        viewModel.onAction(OddsAction.PickCard(Card.parse("As")))
        assertTrue(viewModel.state.board.isEmpty())
        assertEquals(CardSlot.Board(0), viewModel.state.picker?.slot)
    }

    @Test
    fun flopPickerStaysOpenUntilThreeCards() {
        val viewModel = viewModel()
        viewModel.pick(CardSlot.Hole(0), "As", "Ks")
        viewModel.onAction(OddsAction.OpenSlot(CardSlot.Board(0)))
        viewModel.onAction(OddsAction.PickCard(Card.parse("Qs")))
        assertEquals(EquityStatus.INCOMPLETE_BOARD, viewModel.state.status)
        assertNull(viewModel.state.equity)
        viewModel.onAction(OddsAction.PickCard(Card.parse("7h")))
        assertEquals(CardSlot.Board(2), viewModel.state.picker?.slot)
        viewModel.onAction(OddsAction.PickCard(Card.parse("2s")))
        assertNull(viewModel.state.picker)
        assertEquals(EquityStatus.READY, viewModel.state.status)
    }

    @Test
    fun replacingAndRemovingCards() {
        val viewModel = viewModel()
        viewModel.pick(CardSlot.Hole(0), "As", "Ks")
        viewModel.pick(CardSlot.Board(0), "Qs", "7h", "2s")
        viewModel.pick(CardSlot.Board(1), "8d")
        assertEquals(Card.parseList("Qs 8d 2s"), viewModel.state.board)
        assertNull(viewModel.state.picker)

        viewModel.onAction(OddsAction.OpenSlot(CardSlot.Board(0)))
        assertTrue(notNull(viewModel.state.picker).canRemove)
        viewModel.onAction(OddsAction.RemoveCard)
        assertEquals(Card.parseList("8d 2s"), viewModel.state.board)
        assertEquals(EquityStatus.INCOMPLETE_BOARD, viewModel.state.status)

        viewModel.onAction(OddsAction.OpenSlot(CardSlot.Hole(1)))
        viewModel.onAction(OddsAction.RemoveCard)
        assertEquals(listOf(Card.parse("As"), null), viewModel.state.hole)
        assertEquals(EquityStatus.NEED_HOLE, viewModel.state.status)
    }

    @Test
    fun equityMatchesCalculatorWithSameSeedAndFinishes() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            viewModel.pick(CardSlot.Hole(0), "As", "Ks")
            viewModel.pick(CardSlot.Board(0), "Qs", "7h", "2s")
            var state = awaitItem()
            while (state.board.size < 3 || state.calculating || state.equity == null) state = awaitItem()
            val request = EquityRequest(Card.parseList("As Ks"), Card.parseList("Qs 7h 2s"), players = 6)
            val expected = EquityCalculator.calculate(request, Settings, main.dispatcher)
            val equity = notNull(state.equity)
            assertEquals(expected, equity)
            assertFalse(equity.exact)
            assertTrue(equity.isFinal)
            assertEquals(Settings.samples.toLong(), equity.samples)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun calculationShowsProgressThenFinalResult() = runTest {
        val compute = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(dispatcher = compute)
        viewModel.uiState.test {
            viewModel.pick(CardSlot.Hole(0), "Ah", "Ad")
            var state = awaitItem()
            while (!state.calculating) state = awaitItem()
            assertNull(state.equity)
            testScheduler.advanceUntilIdle()
            while (state.calculating || state.equity == null) state = awaitItem()
            assertTrue(notNull(state.equity).isFinal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun inputChangeCancelsRunningCalculation() = runTest {
        val compute = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(dispatcher = compute)
        viewModel.pick(CardSlot.Hole(0), "Ah", "Ad")
        assertTrue(viewModel.state.calculating)
        // Прежний расчёт (6 игроков) ещё не начался — смена числа игроков отменяет его.
        viewModel.onAction(OddsAction.MorePlayers)
        testScheduler.advanceUntilIdle()

        val request = EquityRequest(Card.parseList("Ah Ad"), players = 7)
        val expected = EquityCalculator.calculate(request, Settings, compute)
        assertEquals(7, viewModel.state.players)
        assertFalse(viewModel.state.calculating)
        assertEquals(expected, viewModel.state.equity)
    }

    @Test
    fun riverHeadsUpIsExact() {
        val viewModel = viewModel()
        repeat(4) { viewModel.onAction(OddsAction.FewerPlayers) }
        viewModel.pick(CardSlot.Hole(0), "Td", "Th")
        viewModel.pick(CardSlot.Board(0), "Tc", "8s", "3h")
        viewModel.pick(CardSlot.Board(3), "Kd")
        viewModel.pick(CardSlot.Board(4), "2c")
        val equity = notNull(viewModel.state.equity)
        assertTrue(equity.exact)
        assertEquals(0.0, equity.winMargin, 0.0)
        assertFalse(viewModel.state.calculating)
        assertEquals(HandCategory.THREE_OF_A_KIND, viewModel.state.hand?.category)
        assertNull(viewModel.state.outs)
    }

    @Test
    fun outsOnFlopAndPreflopStrength() {
        val viewModel = viewModel()
        viewModel.pick(CardSlot.Hole(0), "As", "Ks")
        val preflop = notNull(viewModel.state.preflop)
        assertEquals("AKs", preflop.strength.handClass.label)
        assertEquals("BTN", preflop.guide.position.label)
        assertTrue(preflop.inGuide)
        assertNull(viewModel.state.outs)

        viewModel.pick(CardSlot.Board(0), "Qs", "7h", "2s")
        assertNull(viewModel.state.preflop)
        val outs = notNull(viewModel.state.outs)
        assertEquals(9, outs.outs.getValue(HandCategory.FLUSH).size)
        assertEquals(HandCategory.FLUSH to 9, outs.mainDraw())
        notNull(outs.byRiver)
        assertEquals(HandCategory.HIGH_CARD, viewModel.state.hand?.category)
    }

    @Test
    fun potOddsVerdictComparesEquity() {
        val viewModel = viewModel()
        assertNull(viewModel.state.potOdds)
        repeat(4) { viewModel.onAction(OddsAction.FewerPlayers) }
        viewModel.pick(CardSlot.Hole(0), "Ah", "Ad")
        viewModel.amount(OddsAmountField.POT, 1_200)
        viewModel.amount(OddsAmountField.CALL, 300)
        assertEquals(0.2, notNull(viewModel.state.potOdds).required, 1e-12)
        assertEquals(PotOddsVerdict.PAYS_OFF, viewModel.state.verdict)

        viewModel.amount(OddsAmountField.POT, 100)
        viewModel.amount(OddsAmountField.CALL, 10_000)
        assertEquals(PotOddsVerdict.DOES_NOT_PAY, viewModel.state.verdict)
        assertNull(viewModel.state.amountEditor)
    }

    @Test
    fun amountSheetStartsFromCurrentValueAndCloseKeepsValue() {
        val viewModel = viewModel()
        viewModel.amount(OddsAmountField.POT, 500)
        viewModel.onAction(OddsAction.OpenAmount(OddsAmountField.POT))
        assertEquals(AmountEntry.preset(500), viewModel.state.amountEditor?.entry)
        viewModel.onAction(OddsAction.AmountInput(AmountEntry(9)))
        viewModel.onAction(OddsAction.CloseAmount)
        assertEquals(500L, viewModel.state.pot)
    }

    @Test
    fun positionFollowsPlayerCount() {
        val viewModel = viewModel()
        assertEquals(listOf("UTG", "MP", "CO", "BTN", "SB", "BB"), viewModel.state.positions.map { it.label })
        repeat(4) { viewModel.onAction(OddsAction.FewerPlayers) }
        assertEquals(2, viewModel.state.players)
        assertFalse(viewModel.state.canRemovePlayer)
        assertEquals("SB/BTN", viewModel.state.position)
        viewModel.onAction(OddsAction.FewerPlayers)
        assertEquals(2, viewModel.state.players)
        viewModel.onAction(OddsAction.MorePlayers)
        assertEquals("BTN", viewModel.state.position)

        repeat(2) { viewModel.onAction(OddsAction.MorePlayers) }
        viewModel.onAction(OddsAction.SelectPosition("UTG"))
        assertEquals("UTG", viewModel.state.position)
        viewModel.onAction(OddsAction.FewerPlayers)
        assertEquals("CO", viewModel.state.position)
        viewModel.onAction(OddsAction.SelectPosition("UTG+2"))
        assertEquals("CO", viewModel.state.position)

        repeat(10) { viewModel.onAction(OddsAction.MorePlayers) }
        assertEquals(10, viewModel.state.players)
        assertFalse(viewModel.state.canAddPlayer)
        assertEquals(10, viewModel.state.positions.size)
    }

    @Test
    fun deadCardsToggleAndBlockOtherSlots() {
        val viewModel = viewModel()
        viewModel.pick(CardSlot.Hole(0), "As")
        viewModel.onAction(OddsAction.ClosePicker)
        viewModel.onAction(OddsAction.OpenDeadCards)
        val picker = notNull(viewModel.state.picker)
        assertTrue(picker.deadMode)
        assertTrue(Card.parse("As") in picker.unavailable)
        viewModel.onAction(OddsAction.PickCard(Card.parse("As")))
        viewModel.onAction(OddsAction.PickCard(Card.parse("Jc")))
        viewModel.onAction(OddsAction.PickCard(Card.parse("9h")))
        viewModel.onAction(OddsAction.PickCard(Card.parse("9h")))
        assertEquals(listOf(Card.parse("Jc")), viewModel.state.dead)
        viewModel.onAction(OddsAction.ClosePicker)

        viewModel.onAction(OddsAction.OpenSlot(CardSlot.Hole(1)))
        assertTrue(Card.parse("Jc") in notNull(viewModel.state.picker).unavailable)
        viewModel.onAction(OddsAction.PickCard(Card.parse("Jc")))
        assertNull(viewModel.state.hole[1])

        viewModel.onAction(OddsAction.PickerTab(dead = true))
        viewModel.onAction(OddsAction.ClearDead)
        assertTrue(viewModel.state.dead.isEmpty())
    }

    @Test
    fun tooManyDeadCardsBlockEquity() {
        val viewModel = viewModel()
        repeat(4) { viewModel.onAction(OddsAction.MorePlayers) }
        viewModel.pick(CardSlot.Hole(0), "As", "Ks")
        viewModel.onAction(OddsAction.OpenDeadCards)
        // 10 игроков: нужно 5 + 18 = 23 карты из 50 − мёртвые; 28 мёртвых — уже нельзя.
        Card.parseList("2c 2d 2h 2s 3c 3d 3h 3s 4c 4d 4h 4s 5c 5d 5h 5s 6c 6d 6h 6s 7c 7d 7h 7s 8c 8d 8h 8s")
            .forEach { viewModel.onAction(OddsAction.PickCard(it)) }
        assertEquals(EquityStatus.TOO_MANY_DEAD, viewModel.state.status)
        assertNull(viewModel.state.equity)
    }

    @Test
    fun resetClearsInputAndSheets() {
        val viewModel = viewModel()
        viewModel.pick(CardSlot.Hole(0), "As", "Ks")
        viewModel.amount(OddsAmountField.CALL, 300)
        viewModel.onAction(OddsAction.MorePlayers)
        viewModel.onAction(OddsAction.OpenDeadCards)
        viewModel.onAction(OddsAction.Reset)
        val state = viewModel.state
        assertEquals(listOf(null, null), state.hole)
        assertEquals(OddsInput.DEFAULT_PLAYERS, state.players)
        assertEquals(0L, state.call)
        assertNull(state.picker)
        assertNull(state.equity)
        assertEquals(EquityStatus.NEED_HOLE, state.status)
    }

    @Test
    fun inputSurvivesRecreationThroughSavedState() {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.pick(CardSlot.Hole(0), "As", "Ks")
        first.pick(CardSlot.Board(0), "Qs", "7h", "2s")
        first.onAction(OddsAction.FewerPlayers)
        first.onAction(OddsAction.SelectPosition("CO"))
        first.amount(OddsAmountField.POT, 1_200)

        val second = viewModel(handle).state
        assertEquals(Card.parseList("As Ks"), second.hole)
        assertEquals(Card.parseList("Qs 7h 2s"), second.board)
        assertEquals(5, second.players)
        assertEquals("CO", second.position)
        assertEquals(1_200L, second.pot)
        assertNull(second.picker)
    }

    @Test
    fun corruptSavedStateIsIgnored() {
        val handle = SavedStateHandle()
        handle["odds_hole"] = intArrayOf(51, 51)
        handle["odds_board"] = intArrayOf(99, 0)
        handle["odds_players"] = 42
        handle["odds_position"] = "XYZ"
        val state = viewModel(handle).state
        assertEquals(listOf(Card.fromId(51), null), state.hole)
        assertEquals(listOf(Card.fromId(0)), state.board)
        assertEquals(10, state.players)
        assertEquals("BTN", state.position)
    }

    private companion object {
        val Settings = EquitySettings(samples = 20_000, batchSize = 5_000, seed = 42L)
    }
}
