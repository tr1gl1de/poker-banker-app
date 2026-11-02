package app.feltbook.feature.settings.odds

import androidx.compose.runtime.Immutable
import app.feltbook.core.odds.Card
import app.feltbook.core.odds.EquityRequest
import app.feltbook.core.odds.EquityResult
import app.feltbook.core.odds.HandCategory
import app.feltbook.core.odds.OutsResult
import app.feltbook.core.odds.Position
import app.feltbook.core.odds.PositionGuide
import app.feltbook.core.odds.PotOdds
import app.feltbook.core.odds.PotOddsVerdict
import app.feltbook.core.odds.PreflopStrength
import app.feltbook.core.odds.Rank
import app.feltbook.core.ui.component.AmountEntry

/** Поле суммы калькулятора. */
enum class OddsAmountField { POT, CALL }

/** Тройка: сет (карманная пара), трипс (одна своя карта) или тройка целиком на столе. */
enum class TripsKind { SET, TRIPS, BOARD }

/** Текущая комбинация: категория, старшая карта («старшая карта, туз») и вид тройки. */
@Immutable
data class HandSummary(val category: HandCategory, val topRank: Rank, val trips: TripsKind? = null)

/** Префлоп: сила руки и ориентир позиции (не совет). */
@Immutable
data class PreflopUi(val strength: PreflopStrength, val guide: PositionGuide) {
    val inGuide: Boolean get() = guide.includes(strength)
}

/**
 * Шторка выбора карты. [slot] = `null` — открыта сразу на «Мёртвых картах» (вкладки карты нет).
 * [unavailable] — заняты в другом месте (alpha 0.3, недоступны); [selected] — карта слота или мёртвые карты.
 */
@Immutable
data class CardPickerUi(
    val slot: CardSlot?,
    val deadMode: Boolean,
    val unavailable: Set<Card>,
    val selected: Set<Card>,
    val canRemove: Boolean,
    val deadCount: Int,
)

/** Шторка суммы «Банк» / «Ставка» со своей клавиатурой. */
@Immutable
data class AmountEditorUi(val field: OddsAmountField, val entry: AmountEntry)

/**
 * Экран калькулятора шансов (m3-ui-spec §5.14). Проценты — `Double` (это шансы, не деньги); банк и ставка — `Long`.
 * [equity] при [calculating] — промежуточный (или прежний) итог: экран рисует его приглушённо.
 */
@Immutable
data class OddsUiState(
    val hole: List<Card?> = listOf(null, null),
    val board: List<Card> = emptyList(),
    val dead: List<Card> = emptyList(),
    val players: Int = OddsInput.DEFAULT_PLAYERS,
    val positions: List<Position> = emptyList(),
    val position: String = OddsInput.DEFAULT_POSITION,
    val pot: Long = 0L,
    val call: Long = 0L,
    val status: EquityStatus = EquityStatus.NEED_HOLE,
    val equity: EquityResult? = null,
    val calculating: Boolean = false,
    val hand: HandSummary? = null,
    val outs: OutsResult? = null,
    val preflop: PreflopUi? = null,
    val potOdds: PotOdds? = null,
    val picker: CardPickerUi? = null,
    val amountEditor: AmountEditorUi? = null,
) {
    val opponents: Int get() = players - 1

    /** Карманная пара — ауты на тройку называются «на сет», иначе «на трипс». */
    val pocketPair: Boolean
        get() = hole.all { it != null } && hole.mapNotNull { it?.rank }.distinct().size == 1
    val canRemovePlayer: Boolean get() = players > EquityRequest.MIN_PLAYERS
    val canAddPlayer: Boolean get() = players < EquityRequest.MAX_PLAYERS

    /** Сравнение эквити с нужной долей; `null` — нет ставки или эквити. */
    val verdict: PotOddsVerdict? get() = potOdds?.let { odds -> equity?.let { odds.verdict(it.equity) } }

    /** Индекс следующей карты стола или `null`, если стол полон. */
    val nextBoardIndex: Int? get() = board.size.takeIf { it < EquityRequest.BOARD_CARDS }
}

sealed interface OddsAction {
    data class OpenSlot(val slot: CardSlot) : OddsAction

    data object OpenDeadCards : OddsAction

    /** Вкладка шторки: карта слота или «Мёртвые карты». */
    data class PickerTab(val dead: Boolean) : OddsAction

    data class PickCard(val card: Card) : OddsAction

    data object RemoveCard : OddsAction

    data object ClearDead : OddsAction

    data object ClosePicker : OddsAction

    data object FewerPlayers : OddsAction

    data object MorePlayers : OddsAction

    data class SelectPosition(val label: String) : OddsAction

    data class OpenAmount(val field: OddsAmountField) : OddsAction

    data class AmountInput(val entry: AmountEntry) : OddsAction

    data object ConfirmAmount : OddsAction

    data object CloseAmount : OddsAction

    /** «Сбросить» — всё к начальному состоянию без подтверждения: ничего не сохраняется. */
    data object Reset : OddsAction
}
