package app.feltbook.feature.history.detail

import androidx.compose.runtime.Immutable
import app.feltbook.core.domain.engine.Journal
import app.feltbook.core.domain.engine.JournalItem
import app.feltbook.core.domain.engine.OperationKind
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.UiText
import app.feltbook.core.ui.format.pluralQuantity
import app.feltbook.core.ui.format.plus
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.key
import kotlin.time.Instant

@Immutable
data class JournalSubUi(val text: UiText, val amount: Long)

/**
 * Строка полного журнала игры (m3-ui-spec §5.11 «Карточка игры»): «Борис · Докуп 1 000 · в долг» и подстроки.
 * [handover] — акт передачи «Игра принята от Андрея» (латунью, не отменяется).
 */
@Immutable
data class JournalRowUi(
    val id: String,
    val at: Instant,
    val title: UiText,
    val amount: Long?,
    val subs: List<JournalSubUi>,
    val reversed: Boolean,
    val reversible: Boolean,
    val handover: Boolean = false,
)

/** Полный журнал игры, новые сверху (`Journal.items`); имена и суммы — как в карточке игрока §5.5. */
object JournalRows {
    fun of(snapshot: GameSnapshot): List<JournalRowUi> {
        val items = Journal.items(snapshot)
        val severalBankers = items.filterIsInstance<JournalItem.Operation>().map { it.bankerName }.distinct().size > 1
        return items.map { item ->
            when (item) {
                is JournalItem.Operation -> operationRow(snapshot, item, severalBankers)

                is JournalItem.HandoverItem -> JournalRowUi(
                    id = item.handover.id.value.toString(),
                    at = item.at,
                    title = uiText(R.string.detail_journal_handover, item.handover.fromBanker),
                    amount = null,
                    subs = emptyList(),
                    reversed = false,
                    reversible = false,
                    handover = true,
                )
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod") // Таблица видов операций m3-ui-spec §5.5.
    private fun operationRow(
        snapshot: GameSnapshot,
        op: JournalItem.Operation,
        severalBankers: Boolean,
    ): JournalRowUi {
        val entries = op.entries
        val owner = op.playerId
        fun sum(type: EntryType) = entries.filter { it.type == type && it.playerId == owner }.sumOf { it.amount }
        fun nameOf(id: PlayerId?) = id?.let { snapshot.player(it)?.name }.orEmpty()
        val subs = mutableListOf<JournalSubUi>()
        var amount: Long? = null
        val rules = snapshot.game.rules
        val title: UiText = when (op.kind) {
            OperationKind.FIRST_BUY_IN, OperationKind.REBUY -> {
                val chips = sum(EntryType.BUY_IN)
                val rake = sum(EntryType.RAKE)
                val fee = sum(EntryType.BANKER_FEE)
                val paid = sum(EntryType.PAYMENT)
                amount = chips
                if (rake > 0) {
                    val text = if (rules.rakeMode == RakeMode.PERCENT) {
                        uiText(R.string.detail_journal_sub_rake_percent, MoneyFormat.percent(rules.rakeValue.toInt()))
                    } else {
                        uiText(R.string.detail_journal_sub_rake)
                    }
                    subs += JournalSubUi(text, rake)
                }
                if (fee > 0) subs += JournalSubUi(uiText(R.string.detail_journal_sub_fee), fee)
                if (paid > 0) subs += JournalSubUi(uiText(R.string.detail_journal_sub_payment), paid)
                val head = if (op.kind == OperationKind.FIRST_BUY_IN) {
                    R.string.detail_journal_buy_in_first
                } else {
                    R.string.detail_journal_buy_in_rebuy
                }
                val status = when (paid) {
                    0L -> R.string.detail_journal_status_debt
                    chips + rake + fee -> R.string.detail_journal_status_paid
                    else -> R.string.detail_journal_status_partial
                }
                uiText(head, MoneyFormat.number(chips)) + uiText(status)
            }

            OperationKind.PAYMENT -> {
                amount = sum(EntryType.PAYMENT)
                uiText(R.string.detail_journal_payment)
            }

            OperationKind.PAYOUT -> {
                amount = sum(EntryType.PAYOUT)
                uiText(R.string.detail_journal_payout)
            }

            OperationKind.SETTLEMENT -> {
                val payment = sum(EntryType.PAYMENT)
                if (payment > 0) {
                    amount = payment
                    uiText(R.string.detail_journal_settlement_received)
                } else {
                    amount = sum(EntryType.PAYOUT)
                    uiText(R.string.detail_journal_settlement_paid_out)
                }
            }

            OperationKind.CASH_OUT -> {
                val exit = entries.first { it.type == EntryType.CASH_OUT }
                amount = exit.amount
                entries.filter { it.type == EntryType.TIP || it.type == EntryType.TIP_CHIPS }.forEach { tip ->
                    val to = tip.counterpartyPlayerId
                    val text = if (to == null) {
                        uiText(R.string.detail_journal_sub_tip_banker)
                    } else {
                        uiText(R.string.detail_journal_sub_tip_player, nameOf(to))
                    }
                    subs += JournalSubUi(text, tip.amount)
                }
                val chips = UiText.Plural(
                    UiR.plurals.chips_count,
                    pluralQuantity(exit.amount),
                    listOf(MoneyFormat.number(exit.amount)),
                )
                val head = uiText(R.string.detail_journal_cash_out, chips)
                if (exit.earlyExit) head + uiText(R.string.detail_journal_cash_out_early) else head
            }

            OperationKind.DEBT_FORGIVEN -> {
                amount = sum(EntryType.DEBT_FORGIVEN)
                uiText(R.string.detail_journal_forgiven)
            }

            OperationKind.RAKE_PAID -> {
                amount = entries.sumOf { it.amount }
                uiText(R.string.detail_journal_rake_paid)
            }

            OperationKind.INCOME_TAKEN -> {
                amount = entries.sumOf { it.amount }
                uiText(R.string.detail_journal_income_taken, op.bankerName)
            }
        }
        val withName = if (owner != null && nameOf(owner).isNotEmpty()) {
            uiText(R.string.detail_journal_row_player, nameOf(owner), title)
        } else {
            title
        }
        val full = if (severalBankers && op.kind != OperationKind.INCOME_TAKEN) {
            withName + uiText(R.string.detail_journal_banker_suffix, op.bankerName)
        } else {
            withName
        }
        return JournalRowUi(
            id = op.operationId.key(),
            at = op.at,
            title = full,
            amount = amount,
            subs = subs,
            reversed = op.reversal != null,
            reversible = op.reversible,
        )
    }
}
