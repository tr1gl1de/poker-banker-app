package app.feltbook.feature.history.debts

import androidx.compose.runtime.Immutable
import app.feltbook.core.domain.engine.OpenDebt
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.component.Suit
import app.feltbook.core.ui.format.UiText
import app.feltbook.feature.history.common.key
import kotlin.time.Instant

/** Карточка долга (m3-ui-spec §5.9) — `OpenDebt`. [key] = `gameId/playerId`. */
@Immutable
data class DebtCardUi(
    val key: String,
    val gameId: String,
    val playerId: String,
    val playerName: String,
    val suit: Suit,
    val gameTitle: String,
    val gameCreatedAt: Instant,
    val since: Instant,
    val amount: Long,
    val owedAtExit: Long,
) {
    /** «отдал 600 из 1 200» — часть уже погашена. */
    val paidPart: Long get() = (owedAtExit - amount).coerceAtLeast(0L)
}

/** Шторка «Погасить · Влад» (m3-ui-spec §5.7): сумма по умолчанию — весь долг. */
@Immutable
data class DebtPaySheetUi(val card: DebtCardUi, val entry: AmountEntry) {
    val amount: Long get() = entry.value

    val exceedsDebt: Boolean get() = amount > card.amount

    val remaining: Long get() = (card.amount - amount).coerceAtLeast(0L)

    val canConfirm: Boolean get() = amount > 0 && !exceedsDebt
}

@Immutable
data class DebtsUiState(
    val loading: Boolean = true,
    val total: Long = 0,
    /** Различные канонические имена — «2 игрока». */
    val people: Int = 0,
    /** Различные игры — «2 игры». */
    val games: Int = 0,
    val cards: List<DebtCardUi> = emptyList(),
    val now: Instant = Instant.fromEpochMilliseconds(0),
    val paySheet: DebtPaySheetUi? = null,
    val forgiveDialog: DebtCardUi? = null,
    val busy: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && cards.isEmpty()
}

sealed interface DebtsAction {
    data object Back : DebtsAction

    /** Нажатие на карточку — карточка игры долга. */
    data class OpenGame(val gameId: String) : DebtsAction

    data class Pay(val key: String) : DebtsAction

    data class PayEntry(val entry: AmountEntry) : DebtsAction

    data object PayAll : DebtsAction

    /** «половина» — с округлением вверх. */
    data object PayHalf : DebtsAction

    data object ConfirmPay : DebtsAction

    data object DismissSheet : DebtsAction

    data class Forgive(val key: String) : DebtsAction

    data object ConfirmForgive : DebtsAction

    data object DismissDialog : DebtsAction

    /** «Отменить» в snackbar погашения или прощения — `Commands.reverse` этой операции. */
    data class Undo(val token: DebtsUndo) : DebtsAction
}

/** Какую операцию отменить: запись в старой игре [gameId]. */
@Immutable
data class DebtsUndo(val gameId: String, val operationId: String)

sealed interface DebtsEvent {
    data object Back : DebtsEvent

    data class OpenGame(val gameId: String) : DebtsEvent

    data class Undoable(val message: UiText, val token: DebtsUndo) : DebtsEvent

    data class Message(val text: UiText) : DebtsEvent
}

fun debtKey(debt: OpenDebt): String = "${debt.gameId.key()}/${debt.playerId.key()}"

/** Карточки по `Debts.open` (старые сверху) и сводка «Вам должны». */
fun buildDebtsUiState(
    debts: List<OpenDebt>,
    now: Instant,
    paySheet: DebtPaySheetUi?,
    forgiveKey: String?,
    busy: Boolean,
): DebtsUiState {
    val cards = debts.map { debt ->
        DebtCardUi(
            key = debtKey(debt),
            gameId = debt.gameId.key(),
            playerId = debt.playerId.key(),
            playerName = debt.playerName,
            suit = Suit.forName(debt.canonicalName),
            gameTitle = debt.gameTitle,
            gameCreatedAt = debt.gameCreatedAt,
            since = debt.since,
            amount = debt.amount,
            owedAtExit = debt.owedAtExit,
        )
    }
    val byKey = cards.associateBy { it.key }
    return DebtsUiState(
        loading = false,
        total = debts.sumOf { it.amount },
        people = debts.map { it.canonicalName }.distinct().size,
        games = debts.map { it.gameId }.distinct().size,
        cards = cards,
        now = now,
        // Долг погашен или исчез, пока шторка открыта, — шторка закрывается; иначе — свежая сумма долга.
        paySheet = paySheet?.let { sheet -> byKey[sheet.card.key]?.let { sheet.copy(card = it) } },
        forgiveDialog = forgiveKey?.let { byKey[it] },
        busy = busy,
    )
}
