package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.Handover
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.PlayerId
import kotlin.time.Instant

/** Вид операции — выводится из состава записей (в БД не хранится). */
enum class OperationKind {
    /** Первый действующий закуп игрока — «Закуп». */
    FIRST_BUY_IN,

    /** «Докуп» (в т. ч. возврат). */
    REBUY,

    /** PAYMENT отдельно от покупки — «Оплата»/«Погашение». */
    PAYMENT,

    /** PAYOUT отдельно — «Выплачено». */
    PAYOUT,

    /** PAYMENT/PAYOUT, сразу следующие (seq − 1) за выходом того же игрока с тем же временем — «Расчёт при выходе». */
    SETTLEMENT,

    /** CASH_OUT + чаевые — «Выход». */
    CASH_OUT,
    DEBT_FORGIVEN,
    RAKE_PAID,
    INCOME_TAKEN,
}

data class ReversalInfo(val at: Instant, val bankerName: String, val reason: String?)

sealed interface JournalItem {
    val at: Instant

    /**
     * Строка журнала: операция со всеми записями («Закуп 1 000 · в долг», «↳ рейк 10 % 100», «↳ сбор банкира 100»).
     * Отменённая операция остаётся строкой с пометкой «отменено» ([reversal]); операции REVERSAL отдельными строками
     *   не выводятся.
     */
    data class Operation(
        val operationId: OperationId,
        override val at: Instant,
        val bankerName: String,
        val playerId: PlayerId?,
        val kind: OperationKind,
        val entries: List<LedgerEntry>,
        val reversal: ReversalInfo?,
        val reversible: Boolean,
    ) : JournalItem

    /** «Игра принята от <fromBanker>». */
    data class HandoverItem(val handover: Handover, override val at: Instant) : JournalItem
}

object Journal {
    /** Весь журнал игры, новые сверху. Акт передачи стоит сразу после записи `ledgerSeq`. */
    fun items(snapshot: GameSnapshot): List<JournalItem> {
        val operations = operations(snapshot)
        val chronological = buildList {
            var pending = snapshot.handovers.sortedBy { it.ledgerSeq }
            operations.forEach { op ->
                val firstSeq = op.entries.first().seq
                val (before, after) = pending.partition { it.ledgerSeq < firstSeq }
                before.forEach { add(JournalItem.HandoverItem(it, it.at)) }
                pending = after
                add(op)
            }
            pending.forEach { add(JournalItem.HandoverItem(it, it.at)) }
        }
        return chronological.asReversed()
    }

    /** Журнал карточки игрока: операции, где он `playerId` или получатель чаевых. */
    fun forPlayer(snapshot: GameSnapshot, playerId: PlayerId): List<JournalItem.Operation> = operations(snapshot)
        .filter { op -> op.entries.any { it.playerId == playerId || it.counterpartyPlayerId == playerId } }
        .asReversed()

    private fun operations(snapshot: GameSnapshot): List<JournalItem.Operation> {
        val all = snapshot.entries
        val effective = Ledger.effective(all)
        val reversals = all.filter { it.type == EntryType.REVERSAL }.associateBy { it.reversesEntryId }
        val bySeq = all.associateBy { it.seq }
        return all.groupBy { it.operationId }
            .values
            .filter { op -> op.none { it.type == EntryType.REVERSAL } }
            .map { op ->
                val first = op.first()
                val reversal = op.firstNotNullOfOrNull { reversals[it.id] }
                JournalItem.Operation(
                    operationId = first.operationId,
                    at = first.createdAt,
                    bankerName = first.bankerName,
                    playerId = first.playerId,
                    kind = kind(op, effective, bySeq),
                    entries = op,
                    reversal = reversal?.let { ReversalInfo(it.createdAt, it.bankerName, it.note) },
                    reversible = reversalBlocker(snapshot, first.operationId) == null,
                )
            }
    }

    private fun kind(
        op: List<LedgerEntry>,
        effective: List<LedgerEntry>,
        bySeq: Map<Long, LedgerEntry>,
    ): OperationKind {
        val first = op.first()
        val types = op.map { it.type }.toSet()
        return when {
            EntryType.BUY_IN in types -> {
                val earlier = effective.any {
                    it.type == EntryType.BUY_IN && it.playerId == first.playerId &&
                        it.seq < first.seq
                }
                if (earlier) OperationKind.REBUY else OperationKind.FIRST_BUY_IN
            }

            EntryType.CASH_OUT in types -> OperationKind.CASH_OUT

            EntryType.DEBT_FORGIVEN in types -> OperationKind.DEBT_FORGIVEN

            EntryType.RAKE_PAID in types -> OperationKind.RAKE_PAID

            EntryType.INCOME_TAKEN in types -> OperationKind.INCOME_TAKEN

            isSettlement(first, bySeq[first.seq - 1]) -> OperationKind.SETTLEMENT

            first.type == EntryType.PAYOUT -> OperationKind.PAYOUT

            else -> OperationKind.PAYMENT
        }
    }

    private fun isSettlement(entry: LedgerEntry, previous: LedgerEntry?): Boolean = previous != null &&
        previous.type in EXIT_TYPES &&
        previous.playerId == entry.playerId &&
        previous.createdAt == entry.createdAt

    private val EXIT_TYPES = setOf(EntryType.CASH_OUT, EntryType.TIP, EntryType.TIP_CHIPS)
}
