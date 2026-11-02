package app.feltbook.core.domain.engine

import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.emptyGame
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.apply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettlementExample81Test {
    @Test
    fun `итог 8_1 — фишки сошлись, рейк 500, банкиру 353, закрытие с отдачей рейка`() {
        val table = example81()
        table.apply(Commands.finish(table.snapshot))
        val s = Settlements.compute(table.snapshot)
        assertTrue(s.chipsReconciled)
        assertEquals(5_000L, s.chipsIssued)
        assertEquals(5_000L, s.chipsReturned)
        assertEquals(1, s.rebuyCount)
        assertEquals(3, s.playerCount)
        assertEquals(500L, s.rakeAccrued)
        assertEquals(500L, s.rakeDue)
        assertEquals(300L, s.bankerFees)
        assertEquals(53L, s.tipsToBanker)
        assertEquals(0L, s.forgiven)
        assertEquals(353L, s.bankerIncome)
        assertEquals(853L, s.expectedCash)
        assertEquals(853L, s.cashOnHand)
        assertTrue(s.unpaidDebts.isEmpty())
        assertTrue(s.unpaidPayouts.isEmpty())
        assertEquals(0L, s.shortage)
        assertTrue(s.requiredConfirmations.isEmpty())
        assertTrue(s.blockers.isEmpty())
        val andrey = s.rows.single { it.name == "Андрей" }
        assertEquals(
            SettlementRow(andrey.playerId, "Андрей", false, PlayerStatus.CASHED_OUT, 2_300, 0, 0, 0, -2_300, 0, false),
            andrey,
        )
        val boris = s.rows.single { it.name == "Борис" }
        assertEquals(1_200L, boris.paidBeforeExit)
        assertEquals(3_700L, boris.withdrawal)
        assertEquals(53L, boris.tipsGiven)
        assertEquals(3_700L, boris.balanceAtExit)
        assertEquals(-1_053L, s.rows.single { it.name == "Влад" }.balanceAtExit)

        val close = Commands.close(
            table.snapshot,
            payRake = true,
            acknowledged = emptySet(),
            stamp = table.clock.stamp(),
        )
        val rakePaid = close.entries.single()
        assertEquals(EntryType.RAKE_PAID, rakePaid.type)
        assertEquals(20L, rakePaid.seq)
        assertEquals(500L, rakePaid.amount)
        assertNull(rakePaid.playerId)
        table.apply(close)
        assertEquals(GameStatus.CLOSED, table.snapshot.game.status)
        assertEquals(table.clock.now, table.snapshot.game.closedAt)
        val t = table.state.table
        assertEquals(353L, t.cashOnHand)
        assertEquals(0L, t.rakeDue)
        assertEquals(353L, t.expectedCash)
    }

    @Test
    fun `закрытие без отдачи рейка — рейк остаётся к отдаче`() {
        val table = example81()
        table.apply(Commands.finish(table.snapshot))
        val close = Commands.close(
            table.snapshot,
            payRake = false,
            acknowledged = emptySet(),
            stamp = table.clock.stamp(),
        )
        assertTrue(close.entries.isEmpty())
        assertEquals(500L, Settlements.compute(table.snapshot.apply(close)).rakeDue)
    }
}

class SettlementExample82Test {
    private fun vladLeft(): TestTable = example81(vladPays = false).also { it.apply(Commands.finish(it.snapshot)) }

    @Test
    fun `Влад не заплатил — недостача 1 053, касса минус 200, закрыть с долгом`() {
        val table = vladLeft()
        val vlad = table.id("Влад")
        val state = table.state.player(vlad)!!
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, state.status)
        assertEquals(-1_053L, state.outstanding)
        val s = Settlements.compute(table.snapshot)
        assertEquals(-200L, s.cashOnHand)
        assertEquals(853L, s.expectedCash)
        assertEquals(listOf(PlayerAmount(vlad, "Влад", 1_053)), s.unpaidDebts)
        assertEquals(1_053L, s.shortage)
        assertEquals(setOf(CloseConfirmation.OPEN_DEBTS), s.requiredConfirmations)
        assertEquals(s.cashOnHand, s.expectedCash - s.chipDiff + s.unpaidPayouts.sumOf { it.amount } - s.shortage)
        assertViolation(DomainError.NotConfirmed(setOf(CloseConfirmation.OPEN_DEBTS))) {
            Commands.close(table.snapshot, payRake = true, acknowledged = emptySet(), stamp = table.clock.stamp())
        }
        table.apply(Commands.close(table.snapshot, true, setOf(CloseConfirmation.OPEN_DEBTS), table.clock.stamp()))
        assertEquals(-700L, table.state.table.cashOnHand)
        assertEquals(353L, table.state.table.expectedCash)
        assertTrue(table.state.hasOpenDebt)
    }

    @Test
    fun `погашение в старую закрытую игру — касса 353 = ожидаемой, долга нет`() {
        val table = vladLeft()
        table.apply(Commands.close(table.snapshot, true, setOf(CloseConfirmation.OPEN_DEBTS), table.clock.stamp()))
        val vlad = table.id("Влад")
        val pay = Commands.pay(table.snapshot, vlad, 1_053, table.clock.stamp(), bankerName = "Пётр")
        assertEquals(20L, pay.entries.single().seq)
        assertEquals("Пётр", pay.entries.single().bankerName)
        assertNull(pay.game)
        table.apply(pay)
        val state = table.state
        assertEquals(353L, state.table.cashOnHand)
        assertEquals(353L, state.table.expectedCash)
        assertEquals(PlayerStatus.CASHED_OUT, state.player(vlad)!!.status)
        assertFalse(state.hasOpenDebt)
        assertFalse(state.game.editedAfterClose)
        assertEquals(GameStatus.CLOSED, state.game.status)
    }

    @Test
    fun `прощение долга уменьшает доход банкира — ожидаемое = касса = минус 700`() {
        val table = vladLeft()
        table.apply(Commands.close(table.snapshot, true, setOf(CloseConfirmation.OPEN_DEBTS), table.clock.stamp()))
        val vlad = table.id("Влад")
        val forgive = Commands.forgiveDebt(table.snapshot, vlad, null, table.clock.stamp())
        assertEquals(EntryType.DEBT_FORGIVEN, forgive.entries.single().type)
        assertEquals(1_053L, forgive.entries.single().amount)
        table.apply(forgive)
        val t = table.state.table
        assertEquals(-700L, t.bankerIncome)
        assertEquals(-700L, t.expectedCash)
        assertEquals(-700L, t.cashOnHand)
        assertEquals(0L, t.playersOwe)
        assertEquals(PlayerStatus.CASHED_OUT, table.state.player(vlad)!!.status)
        assertEquals(1_053L, Settlements.compute(table.snapshot).forgiven)
    }

    @Test
    fun `частичное прощение и ошибки прощения`() {
        val table = vladLeft()
        val vlad = table.id("Влад")
        assertViolation(DomainError.ForgiveExceedsDebt(1_053)) {
            Commands.forgiveDebt(table.snapshot, vlad, 1_054, table.clock.stamp())
        }
        assertViolation(DomainError.AmountNotPositive) {
            Commands.forgiveDebt(table.snapshot, vlad, 0, table.clock.stamp())
        }
        assertViolation(DomainError.NoDebt(table.id("Борис"))) {
            Commands.forgiveDebt(table.snapshot, table.id("Борис"), null, table.clock.stamp())
        }
        assertViolation(DomainError.PlayerNotFound(app.feltbook.core.domain.playerId(99))) {
            Commands.forgiveDebt(table.snapshot, app.feltbook.core.domain.playerId(99), null, table.clock.stamp())
        }
        table.apply(Commands.forgiveDebt(table.snapshot, vlad, 53, table.clock.stamp()))
        assertEquals(1_000L, table.state.player(vlad)!!.debt)
    }
}

class CloseRulesTest {
    @Test
    fun `фишки не сошлись — нужно подтверждение CHIPS_NOT_RECONCILED`() {
        val table = TestTable().apply {
            add("Андрей", 1_000, BuyInPayment.PAID)
            later()
            cashOut("Андрей", 900)
            apply(Commands.finish(snapshot))
        }
        val s = Settlements.compute(table.snapshot)
        assertEquals(-100L, s.chipDiff)
        assertFalse(s.chipsReconciled)
        assertEquals(setOf(CloseConfirmation.CHIPS_NOT_RECONCILED), s.requiredConfirmations)
        assertViolation(DomainError.NotConfirmed(setOf(CloseConfirmation.CHIPS_NOT_RECONCILED))) {
            Commands.close(table.snapshot, false, emptySet(), table.clock.stamp())
        }
        Commands.close(table.snapshot, false, setOf(CloseConfirmation.CHIPS_NOT_RECONCILED), table.clock.stamp())
    }

    @Test
    fun `банкир не выплатил вышедшему — UNPAID_PAYOUTS`() {
        val table = TestTable().apply {
            add("Андрей", 1_000, BuyInPayment.PAID)
            add("Борис", 1_000, BuyInPayment.PAID)
            later()
            cashOut("Андрей", 1_500, settlement = SettlementChoice.None)
            cashOut("Борис", 500)
            apply(Commands.finish(snapshot))
        }
        val s = Settlements.compute(table.snapshot)
        assertEquals(listOf(PlayerAmount(table.id("Андрей"), "Андрей", 1_500)), s.unpaidPayouts)
        assertEquals(setOf(CloseConfirmation.UNPAID_PAYOUTS), s.requiredConfirmations)
    }

    @Test
    fun `игроки ещё за столом и не тот статус — блокеры закрытия`() {
        val table = TestTable(banker = "Олег", bankerPlays = true).apply { add("Андрей", 1_000) }
        val running = Settlements.compute(table.snapshot)
        assertEquals(
            listOf(
                DomainError.WrongGameStatus(GameStatus.RUNNING, setOf(GameStatus.SETTLING)),
                DomainError.PlayersStillPlaying(listOf(table.id("Андрей"))),
            ),
            running.blockers,
        )
        assertViolation(DomainError.WrongGameStatus(GameStatus.RUNNING, setOf(GameStatus.SETTLING))) {
            Commands.close(table.snapshot, false, emptySet(), table.clock.stamp())
        }
        table.apply(Commands.finish(table.snapshot))
        assertViolation(DomainError.PlayersStillPlaying(listOf(table.id("Андрей")))) {
            Commands.close(table.snapshot, false, CloseConfirmation.entries.toSet(), table.clock.stamp())
        }
        assertEquals(2, running.playerCount, "банкир-игрок без закупа — в таблице, но не мешает закрыть")
    }

    @Test
    fun `рейк отдан заведению посреди игры`() {
        val table = TestTable().apply { add("Андрей", 1_000) }
        val change = Commands.payRake(table.snapshot, table.clock.stamp())
        assertEquals(100L, change.entries.single().amount)
        table.apply(change)
        assertEquals(0L, table.state.table.rakeDue)
        assertEquals(-100L, table.state.table.cashOnHand)
        assertCashIdentity(table.state.table)
        assertViolation(DomainError.NoRakeDue) { Commands.payRake(table.snapshot, table.clock.stamp()) }
        val closed = table.snapshot.copy(game = table.snapshot.game.copy(status = GameStatus.CLOSED))
        assertViolation(
            DomainError.WrongGameStatus(GameStatus.CLOSED, setOf(GameStatus.RUNNING, GameStatus.SETTLING)),
        ) {
            Commands.payRake(closed, table.clock.stamp())
        }
    }

    @Test
    fun `переоткрытие — RUNNING, пометка изменено после закрытия`() {
        val table = example81()
        table.apply(Commands.finish(table.snapshot))
        table.apply(Commands.close(table.snapshot, true, emptySet(), table.clock.stamp()))
        val other = emptyGame(id = app.feltbook.core.domain.gameId(2))
        assertViolation(DomainError.AnotherGameActive(other.id)) { Commands.reopen(table.snapshot, other) }
        val reopened = table.snapshot.apply(Commands.reopen(table.snapshot, null)).game
        assertEquals(GameStatus.RUNNING, reopened.status)
        assertNull(reopened.closedAt)
        assertTrue(reopened.editedAfterClose)
        assertEquals(
            GameStatus.RUNNING,
            table.snapshot.apply(Commands.reopen(table.snapshot, table.snapshot.game)).game.status,
        )
        assertViolation(DomainError.WrongGameStatus(GameStatus.RUNNING, setOf(GameStatus.CLOSED))) {
            Commands.reopen(table.snapshot.apply(Commands.reopen(table.snapshot, null)), null)
        }
    }
}
