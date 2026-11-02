@file:Suppress("MagicNumber") // Данные превью: суммы из макета Odds.

package app.feltbook.feature.settings.odds

import app.feltbook.core.odds.Card
import app.feltbook.core.odds.EquityResult
import app.feltbook.core.ui.component.AmountEntry

/** Состояния для превью и скриншотов; ауты, префлоп и шансы банка — настоящим расчётом `:core:odds`. */
internal object PreviewOddsStates {
    /** Итог как в макете: 38,4 / 1,2 / 60,4, 150 000 раздач. */
    private val MockupEquity =
        EquityResult(
            win = 0.384,
            tie = 0.012,
            lose = 0.604,
            tieShare = 0.005,
            samples = 150_000,
            exact = false,
            isFinal = true,
        )

    private fun state(
        input: OddsInput,
        equity: EquityResult? = null,
        calculating: Boolean = false,
        overlay: OddsOverlay = OddsOverlay(),
    ): OddsUiState = buildOddsState(input, overlay, EquityProgress(equity, calculating))

    private fun cards(text: String): List<Card> = Card.parseList(text)

    private val FlopInput =
        OddsInput(
            hole = cards("As Ks"),
            board = cards("Qs 7h 2s"),
            players = 6,
            position = "BTN",
            pot = 1_200,
            call = 300,
        )

    fun empty(): OddsUiState = state(OddsInput())

    /** Макет: A♠ K♠ на Q♠ 7♥ 2♠, 6 игроков, BTN, банк 1 200, ставка 300. */
    fun flop(): OddsUiState = state(FlopInput, MockupEquity)

    fun calculating(): OddsUiState =
        state(FlopInput.copy(board = cards("Qs 7h 2s 9d")), MockupEquity.copy(samples = 30_000, isFinal = false), true)

    fun preflop(): OddsUiState = state(
        OddsInput(hole = cards("Ah Qh"), players = 6, position = "CO", pot = 300, call = 100),
        MockupEquity.copy(win = 0.241, tie = 0.018, lose = 0.741),
    )

    fun riverTenPlayers(): OddsUiState = state(
        OddsInput(
            hole = cards("Td Th"),
            board = cards("Tc 8s 3h Kd 2c"),
            dead = cards("Ac 5d").toSet(),
            players = 10,
            position = "UTG+1",
            pot = 4_000,
            call = 2_000,
        ),
        EquityResult(
            win = 0.912,
            tie = 0.0,
            lose = 0.088,
            tieShare = 0.0,
            samples = 1_000_000,
            exact = true,
            isFinal = true,
        ),
    )

    fun incompleteFlop(): OddsUiState = state(FlopInput.copy(board = cards("Qs 7h")))

    fun pickerSlot(): OddsUiState =
        state(FlopInput, MockupEquity, overlay = OddsOverlay(picker = PickerState(CardSlot.Board(1), deadMode = false)))

    fun pickerDead(): OddsUiState = state(
        FlopInput.copy(dead = cards("Jc 9h").toSet()),
        MockupEquity,
        overlay = OddsOverlay(picker = PickerState(CardSlot.Hole(0), deadMode = true)),
    )

    fun amountEditor(): OddsUiState = state(
        FlopInput,
        MockupEquity,
        overlay = OddsOverlay(amount = AmountEditorUi(OddsAmountField.POT, AmountEntry.preset(1_200))),
    )
}
