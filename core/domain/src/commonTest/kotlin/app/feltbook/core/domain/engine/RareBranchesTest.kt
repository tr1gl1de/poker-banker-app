package app.feltbook.core.domain.engine

import app.feltbook.core.domain.T0
import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.entry
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.Handover
import app.feltbook.core.domain.model.HandoverId
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Редкие ветки движка: void-игроки, пустая причина отмены, CREATED при приёме, нестандартные пакеты и журналы. */
class RareBranchesTest {
    /** Андрей добавлен по ошибке и отменён (void), Борис за столом. */
    private fun withVoid(): TestTable = TestTable().apply {
        add("Андрей", 1_000)
        reverse(lastOp("Андрей", EntryType.BUY_IN))
        add("Борис", 1_000)
        later()
    }

    @Test
    fun `void-игрок — выход, оплата, выплата, прощение и чаевые ему недоступны`() {
        val table = withVoid()
        val andrey = table.id("Андрей")
        assertTrue(table.state.player(andrey)!!.isVoid)
        assertViolation(DomainError.PlayerNotFound(andrey)) { table.cashOut("Андрей", 0) }
        assertViolation(DomainError.PlayerNotFound(andrey)) { table.pay("Андрей", 1) }
        assertViolation(DomainError.PlayerNotFound(andrey)) { table.payOut("Андрей", 1) }
        assertViolation(DomainError.PlayerNotFound(andrey)) {
            Commands.forgiveDebt(table.snapshot, andrey, null, table.clock.stamp())
        }
        assertViolation(DomainError.TipRecipientInvalid(andrey)) {
            table.cashOut("Борис", 1_000, Tip(10, TipTarget.ToPlayer(andrey)))
        }
    }

    @Test
    fun `причина отмены из одних пробелов — без причины`() {
        val table = TestTable()
        table.add("Андрей", 1_000)
        table.reverse(table.lastOp("Андрей", EntryType.BUY_IN), "   ")
        assertTrue(table.snapshot.entries.filter { it.type == EntryType.REVERSAL }.all { it.note == null })
        val item = Journal.items(table.snapshot).filterIsInstance<JournalItem.Operation>().single()
        assertNull(item.reversal!!.reason)
    }

    @Test
    fun `приём передачи игры в статусе CREATED — становится RUNNING`() {
        val table = example84Start()
        val prepared = Handovers.prepare(table.snapshot, "Влад", keepIncome = false, stamp = table.clock.stamp())
        val created = prepared.outgoing.copy(game = prepared.outgoing.game.copy(status = GameStatus.CREATED))
        val change = Handovers.accept(created, prepared.handover, null, null, null)
        assertEquals(GameStatus.RUNNING, change.game!!.status)
    }

    @Test
    fun `импорт — локальная копия другой игры запрещена, расходящиеся акты — Conflict`() {
        val first = example84Start()
        val other = TestTable()
        assertFailsWith<IllegalArgumentException> { Imports.plan(first.snapshot, other.snapshot, null) }
        val prepared = Handovers.prepare(first.snapshot, "Влад", keepIncome = false, stamp = first.clock.stamp())
        val local = prepared.outgoing
        val foreignAct = prepared.handover.copy(id = HandoverId(Uuid.fromLongs(9, 9)), toBanker = "Пётр")
        val incoming = local.copy(handovers = listOf(foreignAct))
        assertEquals(
            ImportPlan.Conflict(ImportConflictReason.DIVERGED_LEDGER),
            Imports.plan(incoming, local, null),
        )
        val older = local.copy(entries = local.entries.dropLast(1), handovers = listOf(foreignAct.copy(ledgerSeq = 9)))
        assertEquals(ImportPlan.Conflict(ImportConflictReason.DIVERGED_LEDGER), Imports.plan(older, local, null))
    }

    @Test
    fun `валидатор — акт с отрицательным seq, выход и закуп вне диапазона`() {
        val table = example84Start()
        val snapshot = table.snapshot
        val act = Handover(HandoverId(Uuid.fromLongs(8, 8)), snapshot.game.id, "Андрей", "Влад", 0, 0, -1, T0)
        assertEquals(
            listOf(LedgerProblemCode.HANDOVER_SEQ_OUT_OF_RANGE),
            LedgerValidator.validate(snapshot.copy(handovers = listOf(act))).map { it.code },
        )
        val dima = table.id("Дима")
        fun codesWith(type: EntryType, amount: Long) = LedgerValidator.validate(
            snapshot.copy(entries = snapshot.entries + entry(11, type, dima, amount, game = snapshot.game.id)),
        ).map { it.code }
        assertEquals(listOf(LedgerProblemCode.AMOUNT_OUT_OF_RANGE), codesWith(EntryType.CASH_OUT, -1))
        assertEquals(listOf(LedgerProblemCode.AMOUNT_OUT_OF_RANGE), codesWith(EntryType.CASH_OUT, Money.MAX_AMOUNT + 1))
        assertEquals(listOf(LedgerProblemCode.AMOUNT_OUT_OF_RANGE), codesWith(EntryType.BUY_IN, Money.MAX_AMOUNT + 1))
        assertEquals(emptyList(), codesWith(EntryType.CASH_OUT, Money.MAX_AMOUNT))
        assertEquals(emptyList(), codesWith(EntryType.BUY_IN, Money.MAX_AMOUNT))
    }

    @Test
    fun `версия пакета не числом — Malformed`() {
        assertIs<DecodeResult.Malformed>(FeltbookCodec.decode("""{"format":"feltbook","schemaVersion":"один"}"""))
        assertIs<DecodeResult.Malformed>(FeltbookCodec.decode("""{"format":"feltbook","schemaVersion":{}}"""))
    }

    @Test
    fun `оплата сразу после чужого выхода или позже своего — не «Расчёт при выходе»`() {
        val table = TestTable()
        table.add("Андрей", 1_000)
        table.add("Борис", 1_000)
        table.later()
        table.cashOut("Борис", 1_000, settlement = SettlementChoice.None)
        table.pay("Андрей", 500)
        table.cashOut("Андрей", 1_000, settlement = SettlementChoice.None)
        table.later(1)
        table.payOut("Андрей", 100)
        val kinds = Journal.items(table.snapshot).filterIsInstance<JournalItem.Operation>().take(4).map { it.kind }
        assertEquals(
            listOf(OperationKind.PAYOUT, OperationKind.CASH_OUT, OperationKind.PAYMENT, OperationKind.CASH_OUT),
            kinds,
        )
    }
}
