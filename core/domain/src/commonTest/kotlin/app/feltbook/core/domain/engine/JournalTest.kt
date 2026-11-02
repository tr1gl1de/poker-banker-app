package app.feltbook.core.domain.engine

import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.Handover
import app.feltbook.core.domain.model.HandoverId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class JournalTest {
    @Test
    fun `журнал примера 8_1 — виды операций, новые сверху`() {
        val table = example81()
        val items = Journal.items(table.snapshot)
        val kinds = items.map { (it as JournalItem.Operation).kind }
        assertEquals(
            listOf(
                OperationKind.FIRST_BUY_IN,
                OperationKind.FIRST_BUY_IN,
                OperationKind.FIRST_BUY_IN,
                OperationKind.REBUY,
                OperationKind.CASH_OUT,
                OperationKind.SETTLEMENT,
                OperationKind.CASH_OUT,
                OperationKind.SETTLEMENT,
                OperationKind.CASH_OUT,
                OperationKind.SETTLEMENT,
            ),
            kinds.reversed(),
        )
        val first = items.last() as JournalItem.Operation
        assertEquals(3, first.entries.size)
        assertEquals(table.id("Андрей"), first.playerId)
        assertEquals("Олег", first.bankerName)
        assertTrue(first.reversible)
        assertNull(first.reversal)
        assertEquals(first.entries.first().createdAt, first.at)
    }

    @Test
    fun `оплата, выплата, прощение, рейк — отдельные виды`() {
        val table = TestTable().apply {
            add("Андрей", 1_000)
            add("Борис", 1_000, BuyInPayment.PAID)
            pay("Андрей", 200)
            later()
            cashOut("Борис", 2_000, settlement = SettlementChoice.None)
            later(1)
            payOut("Борис", 800)
            cashOut("Андрей", 0, settlement = SettlementChoice.None)
            apply(Commands.forgiveDebt(snapshot, id("Андрей"), 100, clock.stamp()))
            apply(Commands.payRake(snapshot, clock.stamp()))
        }
        val kinds = Journal.items(table.snapshot).map { (it as JournalItem.Operation).kind }.reversed()
        assertEquals(
            listOf(
                OperationKind.FIRST_BUY_IN,
                OperationKind.FIRST_BUY_IN,
                OperationKind.PAYMENT,
                OperationKind.CASH_OUT,
                OperationKind.PAYOUT,
                OperationKind.CASH_OUT,
                OperationKind.DEBT_FORGIVEN,
                OperationKind.RAKE_PAID,
            ),
            kinds,
        )
    }

    @Test
    fun `отменённая операция остаётся строкой с пометкой, отмена отдельной строкой не выводится`() {
        val table = TestTable().apply {
            add("Андрей", 1_000)
            buy("Андрей", 1_000)
        }
        val op = table.lastOp("Андрей", EntryType.BUY_IN)
        table.clock.advance(kotlin.time.Duration.parse("5m"))
        table.reverse(op, "ошибся суммой")
        val items = Journal.items(table.snapshot)
        assertEquals(2, items.size)
        val reversed = items.first() as JournalItem.Operation
        assertEquals(op, reversed.operationId)
        assertEquals(ReversalInfo(table.clock.now, "Олег", "ошибся суммой"), reversed.reversal)
        assertFalse(reversed.reversible)
        assertEquals(OperationKind.REBUY, reversed.kind)
        // Докуп после отмены первого закупа — «Закуп».
        table.reverse(table.snapshot.entries.first().operationId)
        table.add("Андрей", 700)
        val latest = Journal.items(table.snapshot).first() as JournalItem.Operation
        assertEquals(OperationKind.FIRST_BUY_IN, latest.kind)
    }

    @Test
    fun `журнал игрока — его операции и полученные чаевые, акт передачи после своей записи`() {
        val table = TestTable().apply {
            add("Андрей", 1_000)
            add("Борис", 1_000)
            later()
        }
        val andrey = table.id("Андрей")
        val handover =
            Handover(HandoverId(Uuid.fromLongs(7, 7)), table.snapshot.game.id, "Олег", "Влад", 0, 0, 3, table.clock.now)
        table.apply(GameChange(table.snapshot.game.id, handovers = listOf(handover)))
        table.cashOut("Борис", 1_000, Tip(100, TipTarget.ToPlayer(andrey)), SettlementChoice.None)
        val items = Journal.items(table.snapshot)
        assertEquals(listOf(false, false, true, false), items.map { it is JournalItem.HandoverItem })
        assertEquals(JournalItem.HandoverItem(handover, handover.at), items[2])
        val forAndrey = Journal.forPlayer(table.snapshot, andrey)
        assertEquals(listOf(OperationKind.CASH_OUT, OperationKind.FIRST_BUY_IN), forAndrey.map { it.kind })
        val late = handover.copy(id = HandoverId(Uuid.fromLongs(7, 8)), ledgerSeq = 100)
        table.apply(GameChange(table.snapshot.game.id, handovers = listOf(late)))
        assertTrue(Journal.items(table.snapshot).first() is JournalItem.HandoverItem)
    }
}
