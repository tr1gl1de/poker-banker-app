package app.feltbook.core.domain.engine

import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.entry
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.operationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Инвариант §7.5: отмена = журнал, из которого операция вычеркнута (без `isVoid`). */
fun assertReversalIsStrikeOut(before: GameSnapshot, after: GameSnapshot, operation: OperationId) {
    val struck = before.copy(
        entries = before.entries.filter { it.operationId != operation }.mapIndexed { i, e ->
            e.copy(
                seq =
                    i + 1L,
            )
        },
    )
    val expected = GameStates.compute(struck)
    val actual = GameStates.compute(after)
    assertEquals(expected.table, actual.table)
    fun PlayerState.comparable() =
        copy(isVoid = false, session = session?.copy(startedAt = kotlin.time.Instant.DISTANT_PAST))
    assertEquals(expected.players.map { it.comparable() }, actual.players.map { it.comparable() })
    assertCashIdentity(actual.table)
}

class ReversalTest {
    private fun table() = TestTable().apply {
        add("Андрей", 1_000)
        add("Борис", 1_000)
        buy("Андрей", 1_000)
        later()
    }

    private fun reverseAndCheck(table: TestTable, operation: OperationId, reason: String? = null): GameChange {
        val before = table.snapshot
        val change = table.reverse(operation, reason)
        val target = before.entries.filter { it.operationId == operation }
        assertEquals(target.size, change.entries.size)
        assertTrue(change.entries.all { it.type == EntryType.REVERSAL })
        assertEquals(1, change.entries.map { it.operationId }.distinct().size)
        assertEquals(target.map { it.id }, change.entries.map { it.reversesEntryId })
        assertEquals(target.map { it.amount }, change.entries.map { it.amount })
        assertEquals(target.map { it.playerId }, change.entries.map { it.playerId })
        assertEquals(target.map { it.counterpartyPlayerId }, change.entries.map { it.counterpartyPlayerId })
        assertReversalIsStrikeOut(before, table.snapshot, operation)
        return change
    }

    @Test
    fun `отмена первого закупа — игрок void`() {
        val table = TestTable().apply { add("Андрей", 1_000) }
        val change = reverseAndCheck(table, table.lastOp("Андрей", EntryType.BUY_IN), "  не тот игрок ")
        assertTrue(change.entries.all { it.note == "не тот игрок" })
        assertTrue(table.state.player(table.id("Андрей"))!!.isVoid)
    }

    @Test
    fun `отмена докупа`() {
        val table = table()
        reverseAndCheck(table, table.lastOp("Андрей", EntryType.BUY_IN), "   ")
        assertNull(table.snapshot.entries.last().note)
        assertEquals(0, table.state.table.rebuyCount)
    }

    @Test
    fun `отмена оплаты и выплаты`() {
        val table = table()
        table.pay("Андрей", 500)
        reverseAndCheck(table, table.lastOp("Андрей", EntryType.PAYMENT))
        assertEquals(2_300L, table.state.player(table.id("Андрей"))!!.debt)
        table.cashOut("Борис", 3_000, settlement = SettlementChoice.None)
        table.payOut("Борис", 1_000)
        reverseAndCheck(table, table.lastOp("Борис", EntryType.PAYOUT))
    }

    @Test
    fun `отмена выхода и расчёта — по отдельности`() {
        val table = table()
        table.cashOut("Борис", 3_000, Tip(100, TipTarget.ToPlayer(table.id("Андрей"))))
        reverseAndCheck(table, table.lastOp("Борис", EntryType.PAYOUT))
        assertEquals(1_700L, table.state.player(table.id("Борис"))!!.owedByBanker)
        reverseAndCheck(table, table.lastOp("Борис", EntryType.CASH_OUT))
        assertEquals(PlayerStatus.PLAYING, table.state.player(table.id("Борис"))!!.status)
    }

    @Test
    fun `отмена выхода Димы в примере 8_3 — снова за столом, деньги не задваиваются`() {
        val table = TestTable(banker = "Андрей", bankerPlays = true).apply {
            buy("Андрей", 1_000, BuyInPayment.PAID)
            add("Дима", 1_000)
            add("Егор", 1_000, BuyInPayment.PAID)
            later()
            cashOut("Егор", 1_500, Tip(100, TipTarget.ToPlayer(id("Дима"))))
            buy("Егор", 500)
            cashOut("Дима", 1_300, Tip(50, TipTarget.Banker))
            cashOut("Егор", 200, settlement = SettlementChoice.Partial(100))
            cashOut("Андрей", 600)
        }
        reverseAndCheck(table, table.lastOp("Дима", EntryType.CASH_OUT))
        val dima = table.state.player(table.id("Дима"))!!
        assertEquals(PlayerStatus.PLAYING, dima.status)
        assertEquals(-1_250L, dima.outstanding)
        assertEquals(1_300L, table.state.table.chipsInPlay)
        assertEquals(200L, table.state.table.bankerIncome)
        assertEquals(350L, table.state.table.cashOnHand)
        // Повторный выход с правильным F считает только разницу.
        table.cashOut("Дима", 1_300, Tip(50, TipTarget.Banker))
        assertEquals(0L, table.state.player(table.id("Дима"))!!.outstanding)
    }

    @Test
    fun `отмена прощения и отдачи рейка`() {
        val table = table()
        table.cashOut("Борис", 0, settlement = SettlementChoice.None)
        table.apply(Commands.forgiveDebt(table.snapshot, table.id("Борис"), null, table.clock.stamp()))
        reverseAndCheck(table, table.lastOp("Борис", EntryType.DEBT_FORGIVEN))
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, table.state.player(table.id("Борис"))!!.status)
        table.apply(Commands.payRake(table.snapshot, table.clock.stamp()))
        reverseAndCheck(table, table.snapshot.entries.last { it.type == EntryType.RAKE_PAID }.operationId)
        assertEquals(300L, table.state.table.rakeDue)
    }

    @Test
    fun `отмена отмены, повторная отмена, неизвестная операция — ошибки`() {
        val table = table()
        val op = table.lastOp("Андрей", EntryType.BUY_IN)
        val reversal = table.reverse(op)
        assertViolation(DomainError.OperationNotReversible(NotReversibleReason.ALREADY_REVERSED)) { table.reverse(op) }
        assertViolation(DomainError.OperationNotReversible(NotReversibleReason.IS_REVERSAL)) {
            table.reverse(reversal.entries.first().operationId)
        }
        assertViolation(DomainError.OperationNotFound(operationId(999))) { table.reverse(operationId(999)) }
    }

    @Test
    fun `INCOME_TAKEN не отменяется, переданная игра — только чтение`() {
        val table = table()
        val taken =
            entry(table.snapshot.nextSeq, EntryType.INCOME_TAKEN, null, 100, op = 500, game = table.snapshot.game.id)
        table.apply(GameChange(table.snapshot.game.id, entries = listOf(taken)))
        assertViolation(DomainError.OperationNotReversible(NotReversibleReason.INCOME_TAKEN)) {
            table.reverse(operationId(500))
        }
        val handed = table.snapshot.copy(game = table.snapshot.game.copy(status = GameStatus.HANDED_OVER))
        assertViolation(DomainError.OperationNotReversible(NotReversibleReason.GAME_READ_ONLY)) {
            Commands.reverse(handed, table.lastOp("Андрей", EntryType.BUY_IN), null, table.clock.stamp())
        }
    }

    @Test
    fun `в закрытой игре отменяются только погашения, выплаты и прощения`() {
        val table = example81(vladPays = false)
        table.apply(Commands.finish(table.snapshot))
        table.apply(Commands.close(table.snapshot, true, setOf(CloseConfirmation.OPEN_DEBTS), table.clock.stamp()))
        assertViolation(DomainError.OperationNotReversible(NotReversibleReason.NOT_ALLOWED_AFTER_CLOSE)) {
            table.reverse(table.lastOp("Андрей", EntryType.BUY_IN))
        }
        assertViolation(DomainError.OperationNotReversible(NotReversibleReason.NOT_ALLOWED_AFTER_CLOSE)) {
            table.reverse(table.snapshot.entries.last { it.type == EntryType.RAKE_PAID }.operationId)
        }
        table.pay("Влад", 1_000)
        reverseAndCheck(table, table.lastOp("Влад", EntryType.PAYMENT))
        reverseAndCheck(table, table.lastOp("Андрей", EntryType.PAYMENT), "расчёт при выходе ошибочный")
        table.apply(Commands.forgiveDebt(table.snapshot, table.id("Влад"), null, table.clock.stamp()))
        reverseAndCheck(table, table.lastOp("Влад", EntryType.DEBT_FORGIVEN))
        assertEquals(GameStatus.CLOSED, table.snapshot.game.status)
    }
}
