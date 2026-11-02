package app.feltbook.feature.settings.odds

import app.feltbook.core.odds.Card
import app.feltbook.core.odds.HandEvaluator
import app.feltbook.core.odds.OutsCalculator
import app.feltbook.core.odds.Positions
import app.feltbook.core.odds.PotOdds
import app.feltbook.core.odds.PreflopTable

private const val TURN_CARDS = 4

/** Состояние экрана из ввода, открытых шторок и хода расчёта: ауты, шансы банка и префлоп — синхронно (мгновенно). */
internal fun buildOddsState(input: OddsInput, overlay: OddsOverlay, progress: EquityProgress): OddsUiState {
    val hole = input.holeCards
    val status = input.status
    val ready = status == EquityStatus.READY
    return OddsUiState(
        hole = input.hole,
        board = input.board,
        dead = input.dead.sorted(),
        players = input.players,
        positions = Positions.forPlayers(input.players),
        position = input.position,
        pot = input.pot,
        call = input.call,
        status = status,
        equity = progress.result.takeIf { ready },
        calculating = ready && progress.calculating,
        hand = hole?.let { summary(it + input.board) },
        outs = hole?.takeIf { input.board.size == OddsInput.FLOP || input.board.size == TURN_CARDS }
            ?.let { OutsCalculator.outs(it, input.board, input.dead) },
        preflop = hole?.takeIf { input.board.isEmpty() }?.let {
            PreflopUi(PreflopTable.strength(it), Positions.guide(input.players, input.position))
        },
        potOdds = PotOdds.ofOrNull(input.pot, input.call),
        picker = overlay.picker?.let { pickerUi(it, input) },
        amountEditor = overlay.amount,
    )
}

private fun summary(cards: List<Card>): HandSummary =
    HandSummary(category = HandEvaluator.category(cards), topRank = cards.maxOf { it.rank })

private fun pickerUi(picker: PickerState, input: OddsInput): CardPickerUi {
    val placed = input.hole.filterNotNull().toSet() + input.board
    return if (picker.deadMode || picker.slot == null) {
        CardPickerUi(
            slot = picker.slot,
            deadMode = true,
            unavailable = placed,
            selected = input.dead,
            canRemove = false,
            deadCount = input.dead.size,
        )
    } else {
        val slotCard = input.cardAt(picker.slot)
        CardPickerUi(
            slot = picker.slot,
            deadMode = false,
            unavailable = input.used - setOfNotNull(slotCard),
            selected = setOfNotNull(slotCard),
            canRemove = slotCard != null,
            deadCount = input.dead.size,
        )
    }
}
