package app.feltbook.feature.game.table

import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.SettlementChoice
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.format.UiText

/** Ввод открытой шторки (храним только ввод; цифры каждый раз считает движок по свежему снимку). */
sealed interface SheetInput {
    data class Card(val playerId: PlayerId) : SheetInput

    data class BuyIn(
        val playerId: PlayerId,
        val entry: AmountEntry = AmountEntry(),
        val payment: BuyInPayment = BuyInPayment.DEBT,
        val paymentTouched: Boolean = false,
        val debtCollapsed: Boolean = false,
        /** «Погасить сейчас» старый долг вернувшегося игрока (m3-ui-spec §5.4). */
        val debtPay: AmountEntry? = null,
    ) : SheetInput

    data class NewPlayer(
        val name: String = "",
        val entry: AmountEntry = AmountEntry(),
        val payment: BuyInPayment = BuyInPayment.DEBT,
        /** Банкир сам выбрал статус — не подменять «Оплачено сейчас» для должника. */
        val paymentTouched: Boolean = false,
        val amountFocused: Boolean = false,
        val debtCollapsed: Boolean = false,
        val debtPay: AmountEntry? = null,
    ) : SheetInput

    data class Pay(val playerId: PlayerId, val payout: Boolean, val entry: AmountEntry) : SheetInput

    data class CashOut(
        val playerId: PlayerId,
        val chips: AmountEntry = AmountEntry(),
        val focus: CashOutFocus = CashOutFocus.CHIPS,
        val tip: TipChoice = TipChoice.NONE,
        val customTip: AmountEntry = AmountEntry(),
        /** null — чаевые банкиру. */
        val tipTo: PlayerId? = null,
        val tipsExpanded: Boolean = false,
        val picking: Boolean = false,
        val partial: AmountEntry? = null,
        val earlyConfirmed: Boolean = false,
    ) : SheetInput

    data class Breakdown(val kind: BreakdownKind) : SheetInput
}

sealed interface DialogInput {
    data class Reverse(
        val playerId: PlayerId,
        val operationId: OperationId,
        val title: UiText,
        val reason: String = "",
    ) : DialogInput

    data class Rename(val playerId: PlayerId, val value: String, val error: UiText? = null) : DialogInput

    /** «Исключительный случай»: выход ждёт подтверждения с выбранным расчётом. */
    data class EarlyExit(val playerId: PlayerId, val choice: SettlementChoice) : DialogInput

    data class Forgive(val playerId: PlayerId) : DialogInput
}

/** Всё изменяемое состояние экрана, кроме снимка игры. */
data class TableInputs(
    val sheet: SheetInput? = null,
    val dialog: DialogInput? = null,
    val busy: Boolean = false,
    val selected: PlayerId? = null,
)

/** Общие поля шторок со старым долгом: «Новый игрок» и закуп/возврат. */
data class DebtFields(
    val debtPay: AmountEntry?,
    val collapsed: Boolean,
    val payment: BuyInPayment,
    val touched: Boolean,
)

fun SheetInput.debtFields(): DebtFields? = when (this) {
    is SheetInput.NewPlayer -> DebtFields(debtPay, debtCollapsed, payment, paymentTouched)
    is SheetInput.BuyIn -> DebtFields(debtPay, debtCollapsed, payment, paymentTouched)
    else -> null
}

fun SheetInput.withDebtFields(fields: DebtFields): SheetInput = when (this) {
    is SheetInput.NewPlayer ->
        copy(
            debtPay = fields.debtPay,
            debtCollapsed = fields.collapsed,
            payment = fields.payment,
            paymentTouched = fields.touched,
        )

    is SheetInput.BuyIn ->
        copy(
            debtPay = fields.debtPay,
            debtCollapsed = fields.collapsed,
            payment = fields.payment,
            paymentTouched = fields.touched,
        )

    else -> this
}
