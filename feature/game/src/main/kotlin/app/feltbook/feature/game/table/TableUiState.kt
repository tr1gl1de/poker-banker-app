package app.feltbook.feature.game.table

import androidx.compose.runtime.Immutable
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.BuyInQuote
import app.feltbook.core.domain.engine.CashOutQuote
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.component.Suit
import app.feltbook.core.ui.format.UiText
import kotlin.time.Duration
import kotlin.time.Instant

/** Стол (m3-ui-spec §5.3) со всеми шторками: шторки — состояние экрана, а не маршруты (§8.1). */
@Immutable
data class TableUiState(
    val loading: Boolean = true,
    val gameId: String = "",
    val title: String = "",
    val status: GameStatus = GameStatus.RUNNING,
    val bankerName: String = "",
    val now: Instant = Instant.fromEpochMilliseconds(0),
    val elapsed: Duration = Duration.ZERO,
    val board: BoardUi = BoardUi(),
    val playersAtTable: Int = 0,
    val playersLeft: Int = 0,
    val rows: List<PlayerRowUi> = emptyList(),
    val sheet: SheetUi? = null,
    val dialog: DialogUi? = null,
    /** Идёт запись — главные кнопки недоступны (ТЗ §5.1: блокировка до завершения операции). */
    val busy: Boolean = false,
    /** Планшет: выбранный игрок и его карточка в панели деталей (m3-ui-spec §9). */
    val selectedPlayerId: String? = null,
    val detail: PlayerCardUi? = null,
) {
    val canAddPlayer: Boolean get() = status == GameStatus.RUNNING
    val settling: Boolean get() = status == GameStatus.SETTLING
}

/** Цифры табло — `TableSummary`. */
@Immutable
data class BoardUi(
    val chipsInPlay: Long = 0,
    val chipsIssued: Long = 0,
    val chipsReturned: Long = 0,
    val rebuyCount: Int = 0,
    val cashOnHand: Long = 0,
    val playersOwe: Long = 0,
    val bankerOwes: Long = 0,
    val rakeDue: Long = 0,
    val bankerIncome: Long = 0,
)

/** Строка игрока (таблица состояний m3-ui-spec §5.3). */
@Immutable
data class PlayerRowUi(
    val id: String,
    val name: String,
    val suit: Suit,
    val status: PlayerStatus,
    val isBanker: Boolean,
    val rebuyCount: Int,
    /** «закуп N» — текущей сессии, если вернулся. */
    val chipsBought: Long,
    val bought: Boolean,
    val playedFor: Duration?,
    /** Ранний выход сейчас: сколько минут сыграно (null — не ранний). */
    val earlyMinutes: Long?,
    val minPlayMinutes: Int,
    val outstanding: Long,
    val balanceAtExit: Long?,
    val canBuyIn: Boolean,
    val canCashOut: Boolean,
)

enum class BreakdownKind { CHIPS, CASH, DEBTS, RAKE, BANKER }

enum class Tone { INK, OWE, PAY, BRASS, MUTED }

@Immutable
data class BreakdownLine(val label: UiText, val amount: Long, val tone: Tone = Tone.INK, val currency: Boolean = true)

sealed interface SheetUi

@Immutable
data class BreakdownUi(
    val kind: BreakdownKind,
    val total: Long,
    val lines: List<BreakdownLine>,
    /** «Банкир должен» (долги) или «По банкирам» (доход). */
    val secondaryTitle: UiText? = null,
    val secondary: List<BreakdownLine> = emptyList(),
) : SheetUi

@Immutable
data class JournalSubUi(val text: UiText, val amount: Long)

@Immutable
data class JournalRowUi(
    val operationId: String,
    val at: Instant,
    val title: UiText,
    val amount: Long?,
    val subs: List<JournalSubUi>,
    val reversed: Boolean,
    val reversible: Boolean,
)

/** Карточка игрока (шторка на телефоне, панель деталей на планшете). */
@Immutable
data class PlayerCardUi(
    val playerId: String,
    val name: String,
    val suit: Suit,
    val status: PlayerStatus,
    val isBanker: Boolean,
    val rebuyCount: Int,
    val playedFor: Duration?,
    val firstBuyInAt: Instant?,
    val lastCashOutAt: Instant?,
    val earlyExit: Boolean,
    val chipsBought: Long,
    val charged: Long,
    val paid: Long,
    val debt: Long,
    val owedByBanker: Long,
    val canBuyIn: Boolean,
    val canPay: Boolean,
    val canPayout: Boolean,
    val canCashOut: Boolean,
    val canEarlyExit: Boolean,
    val canForgive: Boolean,
    val canRename: Boolean,
    val journal: List<JournalRowUi>,
) : SheetUi

/** Закуп / докуп / возврат (макет BuyIn). */
@Immutable
data class BuyInUi(
    val playerId: String,
    val name: String,
    val suit: Suit,
    val isFirst: Boolean,
    val isReturn: Boolean,
    val chipsBought: Long,
    val debt: Long,
    val owedByBanker: Long,
    val entry: AmountEntry,
    val minBuyIn: Long,
    val rakePercent: Long?,
    val split: Boolean,
    val payment: BuyInPayment,
    val quote: BuyInQuote,
    val lastAmount: Long?,
    val busy: Boolean,
) : SheetUi {
    val belowMin: Boolean get() = entry.value in 1 until minBuyIn
    val canConfirm: Boolean get() = quote.canConfirm && !busy
}

/** Ошибка имени нового игрока. */
@Immutable
sealed interface NameProblem {
    data object TooLong : NameProblem

    data class Taken(val playerId: String, val name: String, val seated: Boolean) : NameProblem
}

/** Предупреждение о старом долге (макет DebtWarning). */
@Immutable
data class DebtWarningUi(
    val name: String,
    val total: Long,
    val since: Instant,
    val oldestGameTitle: String,
    val days: Long,
    val games: Int,
)

@Immutable
data class NewPlayerUi(
    val name: String,
    val suggestions: List<String>,
    val nameProblem: NameProblem?,
    val debt: DebtWarningUi?,
    val debtCollapsed: Boolean,
    val amountFocused: Boolean,
    val entry: AmountEntry,
    val minBuyIn: Long,
    val rakePercent: Long?,
    val split: Boolean,
    val payment: BuyInPayment,
    val quote: BuyInQuote,
    /** «Погасить сейчас»: ввод суммы погашения старого долга (null — не открыт). */
    val debtPay: AmountEntry?,
    val busy: Boolean,
) : SheetUi {
    val belowMin: Boolean get() = entry.value in 1 until minBuyIn
    val canConfirm: Boolean get() = quote.canConfirm && !busy
}

/** «Оплата долга» / «Выплатить» (m3-ui-spec §5.7). */
@Immutable
data class PayUi(
    val playerId: String,
    val name: String,
    val suit: Suit,
    val payout: Boolean,
    /** Долг игрока или долг банкира перед игроком. */
    val current: Long,
    val entry: AmountEntry,
    val error: UiText?,
    val busy: Boolean,
) : SheetUi {
    val remaining: Long get() = (current - entry.value).coerceAtLeast(0)
    val canConfirm: Boolean get() = !busy && entry.value in 1..current
}

enum class CashOutFocus { CHIPS, TIP, NONE }

enum class TipChoice { NONE, ROUND_100, ROUND_500, CUSTOM }

@Immutable
data class TipRecipientUi(val playerId: String, val name: String, val suit: Suit, val seated: Boolean)

/** Выход (макеты CashOut, DebtCashOut). */
@Immutable
data class CashOutUi(
    val playerId: String,
    val name: String,
    val suit: Suit,
    val playedFor: Duration?,
    val chipsBought: Long,
    val debtBefore: Long,
    val chips: AmountEntry,
    val focus: CashOutFocus,
    val tipChoice: TipChoice,
    val customTip: AmountEntry,
    val round100: Long,
    val round500: Long,
    /** null — банкиру. */
    val tipRecipient: TipRecipientUi?,
    val tipsExpanded: Boolean,
    val quote: CashOutQuote,
    val minPlayMinutes: Int,
    val earlyConfirmed: Boolean,
    val partial: AmountEntry?,
    /** Выбор получателя чаевых (null — не открыт). */
    val recipients: List<TipRecipientUi>?,
    val blocker: UiText?,
    val busy: Boolean,
) : SheetUi {
    val balance: Long get() = quote.balance
    val showEarlyBanner: Boolean get() = quote.isEarlyExit && !earlyConfirmed
    val canConfirm: Boolean get() = quote.canConfirm && !busy
    val partialMax: Long get() = kotlin.math.abs(balance) - 1
    val canConfirmPartial: Boolean get() = canConfirm && partial != null && partial.value in 1..partialMax
}

sealed interface DialogUi {
    data class Reverse(val operationId: String, val title: UiText, val reason: String) : DialogUi

    data class Rename(val playerId: String, val oldName: String, val value: String, val error: UiText?) : DialogUi

    data class EarlyExit(val name: String, val minutes: Long, val minPlayMinutes: Int) : DialogUi

    data class Forgive(val playerId: String, val amount: Long, val gameTitle: String) : DialogUi
}
