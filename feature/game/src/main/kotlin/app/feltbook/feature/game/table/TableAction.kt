package app.feltbook.feature.game.table

import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.format.UiText

/** Действия стола и его шторок. Ввод суммы ([EntryChange], [ChipSelect]) относится к активному полю шторки. */
sealed interface TableAction {
    // Стол
    data class RowClick(val playerId: String) : TableAction

    /** Планшет: выбор игрока для панели деталей. */
    data class SelectPlayer(val playerId: String) : TableAction

    data class SwipeBuyIn(val playerId: String) : TableAction

    data class SwipeCashOut(val playerId: String) : TableAction

    data object AddPlayer : TableAction

    data class OpenBreakdown(val kind: BreakdownKind) : TableAction

    data object Finish : TableAction

    data object Resume : TableAction

    data object ToSettlement : TableAction

    data object Handover : TableAction

    data object DismissSheet : TableAction

    // Ввод суммы
    data class EntryChange(val entry: AmountEntry) : TableAction

    data class ChipSelect(val amount: Long) : TableAction

    data class PaymentChange(val payment: BuyInPayment) : TableAction

    // Закуп / новый игрок
    data object ConfirmBuyIn : TableAction

    data class NameChange(val name: String) : TableAction

    data class SuggestionPick(val name: String) : TableAction

    data object FocusAmount : TableAction

    data object FocusName : TableAction

    data class OpenExisting(val playerId: String) : TableAction

    data class ReturnExisting(val playerId: String) : TableAction

    data object DebtPayNow : TableAction

    data object DebtSeatWithDebt : TableAction

    data object DebtPayConfirm : TableAction

    data object DebtPayCancel : TableAction

    // Карточка игрока
    data class CardBuyIn(val playerId: String) : TableAction

    data class CardPay(val playerId: String) : TableAction

    data class CardPayout(val playerId: String) : TableAction

    data class CardCashOut(val playerId: String) : TableAction

    data class CardEarlyExit(val playerId: String) : TableAction

    data class CardRename(val playerId: String) : TableAction

    data class CardForgive(val playerId: String) : TableAction

    data class JournalHold(val playerId: String, val operationId: String) : TableAction

    // Оплата / выплата
    data object PayAll : TableAction

    data object PayHalf : TableAction

    data object ConfirmPay : TableAction

    // Выход
    data object FocusChips : TableAction

    data object KeypadDone : TableAction

    data class TipSelect(val choice: TipChoice) : TableAction

    data object ExpandTips : TableAction

    data object TipToBanker : TableAction

    data object PickTipRecipient : TableAction

    data class TipRecipientPicked(val playerId: String) : TableAction

    data object CancelPicker : TableAction

    /** Hold «Расчёт произведён» / «Получил N ₽». */
    data object SettleFull : TableAction

    data object OpenPartial : TableAction

    data object ClosePartial : TableAction

    /** Hold «Получил 500 ₽» / «Выплатил 500 ₽». */
    data object SettlePartial : TableAction

    /** «Не заплатил» / «Пока ничего не выплатил». */
    data object SettleNone : TableAction

    // Диалоги
    data class ReasonChange(val reason: String) : TableAction

    data class RenameChange(val value: String) : TableAction

    data object DialogConfirm : TableAction

    data object DialogDismiss : TableAction

    // Snackbar
    data class Undo(val token: UndoToken) : TableAction
}

/** Чем отменить последнюю обратимую операцию (snackbar «Отменить», 5 с). */
sealed interface UndoToken {
    /** Операции в порядке отмены (расчёт выхода — раньше самого выхода); `gameId` — игра записи. */
    data class Reverse(val operations: List<Pair<String, String>>) : UndoToken

    data class Rename(val playerId: String, val name: String) : UndoToken
}

sealed interface TableEvent {
    data class Undoable(val message: UiText, val token: UndoToken) : TableEvent

    data class Message(val message: UiText) : TableEvent

    data object OpenSettlement : TableEvent

    data object OpenHandover : TableEvent

    data object GameGone : TableEvent

    /** Подтверждение денежного действия (вибрация, если включена). */
    data object ConfirmHaptic : TableEvent
}
