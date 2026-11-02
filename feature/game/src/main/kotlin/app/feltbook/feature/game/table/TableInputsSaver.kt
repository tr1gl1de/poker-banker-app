package app.feltbook.feature.game.table

import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.feature.game.common.key
import app.feltbook.feature.game.common.toPlayerId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ввод открытой шторки переживает смерть процесса (телефоны на 2 ГБ выгружают приложение, пока банкир
 * считает фишки): [TableInputs] ↔ строка в `SavedStateHandle`. Диалоги и `busy` не сохраняются.
 */
internal object TableInputsSaver {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(inputs: TableInputs): String =
        json.encodeToString(SavedInputs.serializer(), SavedInputs(inputs.sheet?.toSaved(), inputs.selected?.key()))

    fun decode(text: String?): TableInputs? = text?.let {
        runCatching { json.decodeFromString(SavedInputs.serializer(), it) }.getOrNull()?.let { saved ->
            TableInputs(sheet = saved.sheet?.toInput(), selected = saved.selected?.toPlayerId())
        }
    }
}

@Serializable
internal data class SavedInputs(val sheet: SavedSheet? = null, val selected: String? = null)

@Serializable
internal data class SavedSheet(
    val kind: String,
    val playerId: String? = null,
    val name: String = "",
    val entry: Long = 0,
    val chipsPaid: Boolean = false,
    val feesPaid: Boolean = false,
    val touched: Boolean = false,
    val amountFocused: Boolean = false,
    val debtCollapsed: Boolean = false,
    val debtPay: Long? = null,
    val payout: Boolean = false,
    val focus: String = CashOutFocus.CHIPS.name,
    val tip: String = TipChoice.NONE.name,
    val customTip: Long = 0,
    val tipTo: String? = null,
    val tipsExpanded: Boolean = false,
    val partial: Long? = null,
    val earlyConfirmed: Boolean = false,
    val breakdown: String? = null,
)

private fun SheetInput.toSaved(): SavedSheet = when (this) {
    is SheetInput.Card -> SavedSheet(kind = "card", playerId = playerId.key())

    is SheetInput.BuyIn -> SavedSheet(
        kind = "buyIn",
        playerId = playerId.key(),
        entry = entry.value,
        chipsPaid = payment.chipsPaid,
        feesPaid = payment.feesPaid,
        touched = paymentTouched,
        debtCollapsed = debtCollapsed,
        debtPay = debtPay?.value,
    )

    is SheetInput.NewPlayer -> SavedSheet(
        kind = "newPlayer",
        name = name,
        entry = entry.value,
        chipsPaid = payment.chipsPaid,
        feesPaid = payment.feesPaid,
        touched = paymentTouched,
        amountFocused = amountFocused,
        debtCollapsed = debtCollapsed,
        debtPay = debtPay?.value,
    )

    is SheetInput.Pay -> SavedSheet(kind = "pay", playerId = playerId.key(), payout = payout, entry = entry.value)

    is SheetInput.CashOut -> SavedSheet(
        kind = "cashOut",
        playerId = playerId.key(),
        entry = chips.value,
        focus = focus.name,
        tip = tip.name,
        customTip = customTip.value,
        tipTo = tipTo?.key(),
        tipsExpanded = tipsExpanded,
        partial = partial?.value,
        earlyConfirmed = earlyConfirmed,
    )

    is SheetInput.Breakdown -> SavedSheet(kind = "breakdown", breakdown = kind.name)
}

@Suppress("CyclomaticComplexMethod") // Плоское сопоставление видов шторок.
private fun SavedSheet.toInput(): SheetInput? {
    val id = playerId?.toPlayerId()
    val payment = BuyInPayment(chipsPaid = chipsPaid, feesPaid = feesPaid)
    return when (kind) {
        "card" -> id?.let { SheetInput.Card(it) }

        "buyIn" -> id?.let {
            SheetInput.BuyIn(it, AmountEntry(entry), payment, touched, debtCollapsed, debtPay?.let(::AmountEntry))
        }

        "newPlayer" -> SheetInput.NewPlayer(
            name = name,
            entry = AmountEntry(entry),
            payment = payment,
            paymentTouched = touched,
            amountFocused = amountFocused,
            debtCollapsed = debtCollapsed,
            debtPay = debtPay?.let(::AmountEntry),
        )

        "pay" -> id?.let { SheetInput.Pay(it, payout, AmountEntry(entry)) }

        "cashOut" -> id?.let {
            SheetInput.CashOut(
                playerId = it,
                chips = AmountEntry(entry),
                focus = CashOutFocus.entries.firstOrNull { f -> f.name == focus } ?: CashOutFocus.CHIPS,
                tip = TipChoice.entries.firstOrNull { t -> t.name == tip } ?: TipChoice.NONE,
                customTip = AmountEntry(customTip),
                tipTo = tipTo?.toPlayerId(),
                tipsExpanded = tipsExpanded,
                partial = partial?.let(::AmountEntry),
                earlyConfirmed = earlyConfirmed,
            )
        }

        "breakdown" -> BreakdownKind.entries.firstOrNull { it.name == breakdown }?.let { SheetInput.Breakdown(it) }

        else -> null
    }
}
