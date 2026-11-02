package app.feltbook.core.domain.engine

import app.feltbook.core.domain.Example81
import app.feltbook.core.domain.Example83
import app.feltbook.core.domain.T0
import app.feltbook.core.domain.emptyGame
import app.feltbook.core.domain.emptySnapshot
import app.feltbook.core.domain.entry
import app.feltbook.core.domain.entryId
import app.feltbook.core.domain.gameId
import app.feltbook.core.domain.model.EntryType.BANKER_FEE
import app.feltbook.core.domain.model.EntryType.BUY_IN
import app.feltbook.core.domain.model.EntryType.CASH_OUT
import app.feltbook.core.domain.model.EntryType.DEBT_FORGIVEN
import app.feltbook.core.domain.model.EntryType.INCOME_TAKEN
import app.feltbook.core.domain.model.EntryType.PAYMENT
import app.feltbook.core.domain.model.EntryType.PAYOUT
import app.feltbook.core.domain.model.EntryType.RAKE
import app.feltbook.core.domain.model.EntryType.RAKE_PAID
import app.feltbook.core.domain.model.EntryType.REVERSAL
import app.feltbook.core.domain.model.EntryType.TIP
import app.feltbook.core.domain.model.EntryType.TIP_CHIPS
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.Player
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.playerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Тождество кассы §3.4 контракта. */
fun assertCashIdentity(table: TableSummary) {
    assertEquals(
        table.cashOnHand,
        table.expectedCash + table.chipsInPlay + table.bankerOwes - table.playersOwe,
        "тождество кассы: $table",
    )
    assertEquals(table.chipsIssued - table.chipsReturned, table.chipsInPlay)
    assertEquals(table.bankerIncome, table.incomeByBanker.sumOf { it.net })
}

class GameStatesTest {
    private val a = Example81.andrey
    private val b = Example81.boris
    private val v = Example81.vlad

    @Test
    fun `табло после op4 примера 8_1 — все за столом`() {
        val state = GameStates.compute(Example81.snapshot(Example81.afterOp4))
        val t = state.table
        assertEquals(5_000L, t.chipsIssued)
        assertEquals(0L, t.chipsReturned)
        assertEquals(5_000L, t.chipsInPlay)
        assertEquals(1, t.rebuyCount)
        assertEquals(3, t.playersAtTable)
        assertEquals(0, t.playersLeft)
        assertEquals(4_600L, t.playersOwe)
        assertEquals(0L, t.bankerOwes)
        assertEquals(1_200L, t.cashOnHand)
        assertEquals(500L, t.rakeAccrued)
        assertEquals(500L, t.rakeDue)
        assertEquals(300L, t.bankerIncome)
        assertEquals(800L, t.expectedCash)
        assertCashIdentity(t)
        val andrey = state.player(a)!!
        assertEquals(PlayerStatus.PLAYING, andrey.status)
        assertEquals(2, andrey.buyInCount)
        assertEquals(1, andrey.rebuyCount)
        assertEquals(2_300L, andrey.charged)
        assertEquals(2_300L, andrey.debt)
        assertNull(andrey.result)
        assertNull(andrey.balanceAtExit)
        assertEquals(SessionFigures(T0, 2_000, 2_300, 0, 0), andrey.session)
        assertEquals(1_200L, state.player(b)!!.paid)
        assertFalse(state.hasOpenDebt)
    }

    @Test
    fun `сквозной пример 8_1 — рейк 500, банкиру 353, на руках 853`() {
        val state = GameStates.compute(Example81.snapshot(Example81.full))
        val t = state.table
        assertEquals(5_000L, t.chipsIssued)
        assertEquals(5_000L, t.chipsReturned)
        assertEquals(0L, t.chipsInPlay)
        assertEquals(500L, t.rakeAccrued)
        assertEquals(353L, t.bankerIncome)
        assertEquals(listOf(BankerIncome("Олег", fees = 300, tips = 53, forgiven = 0, taken = 0)), t.incomeByBanker)
        assertEquals(853L, t.cashOnHand)
        assertEquals(853L, t.expectedCash)
        assertEquals(0L, t.playersOwe)
        assertEquals(0, t.playersAtTable)
        assertEquals(3, t.playersLeft)
        assertCashIdentity(t)

        val andrey = state.player(a)!!
        assertEquals(2_300L, andrey.charged)
        assertEquals(2_300L, andrey.paid)
        assertEquals(0L, andrey.withdrawal)
        assertEquals(0L, andrey.outstanding)
        assertEquals(-2_300L, andrey.balanceAtExit)
        assertEquals(-2_000L, andrey.result)
        assertEquals(PlayerStatus.CASHED_OUT, andrey.status)
        assertNull(andrey.session)

        val boris = state.player(b)!!
        assertEquals(3_700L, boris.withdrawal)
        assertEquals(53L, boris.tipsGiven)
        assertEquals(53L, boris.tipsToBanker)
        assertEquals(3_700L, boris.paidOut)
        assertEquals(0L, boris.outstanding)
        assertEquals(3_700L, boris.balanceAtExit)
        assertEquals(2_700L, boris.result)

        val vlad = state.player(v)!!
        assertEquals(1_247L, vlad.withdrawal)
        assertEquals(1_053L, vlad.paid)
        assertEquals(-1_053L, vlad.balanceAtExit)
        assertEquals(-753L, vlad.result)
        assertEquals(PlayerStatus.CASHED_OUT, vlad.status)

        assertEquals(
            -53L,
            state.players.sumOf {
                it.result!!
            },
            "игра с нулевой суммой: Σ результатов = −чаевые банкиру",
        )
        assertEquals(T0, vlad.lastCashOutAt)
    }

    @Test
    fun `Влад не заплатил — ушёл с долгом 1 053, касса минус 200`() {
        val entries = Example81.full.filter { it.seq != 17L }.mapIndexed { i, e -> e.copy(seq = i + 1L) }
        val state = GameStates.compute(Example81.snapshot(entries))
        val vlad = state.player(v)!!
        assertEquals(-1_053L, vlad.outstanding)
        assertEquals(1_053L, vlad.debt)
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, vlad.status)
        assertTrue(state.hasOpenDebt)
        assertEquals(-200L, state.table.cashOnHand)
        assertEquals(853L, state.table.expectedCash)
        assertEquals(1_053L, state.table.playersOwe)
        assertCashIdentity(state.table)
    }

    @Test
    fun `банкир-игрок, чаевые фишками, возврат и частичная оплата — пример 8_3`() {
        val state = GameStates.compute(Example83.snapshot(Example83.entries))
        val t = state.table
        assertEquals(3_500L, t.chipsIssued)
        assertEquals(3_500L, t.chipsReturned, "Σ CASH_OUT 3 600 − TIP_CHIPS 100")
        assertEquals(350L, t.rakeAccrued)
        assertEquals(250L, t.bankerIncome)
        assertEquals(600L, t.expectedCash)
        assertEquals(350L, t.cashOnHand)
        assertEquals(250L, t.playersOwe)
        assertEquals(1, t.rebuyCount)
        assertCashIdentity(t)
        val andrey = state.player(Example83.andrey)!!
        assertTrue(andrey.isBanker)
        assertEquals(0L, andrey.bankerFee)
        assertEquals(-400L, andrey.result)
        val dima = state.player(Example83.dima)!!
        assertEquals(100L, dima.chipTipsReceived)
        assertEquals(0L, dima.tipsReceived)
        assertEquals(1_250L, dima.withdrawal)
        assertEquals(250L, dima.result)
        assertEquals(50L, dima.balanceAtExit)
        val egor = state.player(Example83.egor)!!
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, egor.status)
        assertEquals(250L, egor.debt)
        assertEquals(-350L, egor.balanceAtExit)
        assertEquals(100L, egor.result)
        assertEquals(2, egor.buyInCount)
        assertEquals(-50L, state.players.sumOf { it.result!! })
    }

    @Test
    fun `сессия вернувшегося игрока — только новые покупки, перенос с прошлого выхода`() {
        // Е до своего второго выхода: seq 1–18 примера 8.3.
        val state = GameStates.compute(Example83.snapshot(Example83.entries.take(18)))
        val egor = state.player(Example83.egor)!!
        assertEquals(PlayerStatus.PLAYING, egor.status)
        assertEquals(SessionFigures(T0, chipsBought = 500, charged = 550, paid = 0, carriedOver = 0), egor.session)
        assertEquals(-550L, egor.outstanding)
        assertEquals(1_400L, egor.balanceAtExit, "баланс прошлого выхода сохраняется")
        assertNull(egor.result)
    }

    @Test
    fun `долг прошлого выхода переносится в новую сессию`() {
        val p = playerId(1)
        val snapshot = single(
            entry(1, BUY_IN, p, 1_000, op = 1),
            entry(2, RAKE, p, 100, op = 1),
            entry(3, BANKER_FEE, p, 100, op = 1),
            entry(4, CASH_OUT, p, 500, op = 2),
            entry(5, BUY_IN, p, 500, op = 3),
            entry(6, RAKE, p, 50, op = 3),
            entry(7, PAYMENT, p, 550, op = 3),
            entry(8, PAYOUT, p, 10, op = 4),
        )
        val state = GameStates.compute(snapshot).player(p)!!
        assertEquals(PlayerStatus.PLAYING, state.status)
        assertEquals(-710L, state.outstanding)
        assertEquals(-700L, state.balanceAtExit)
        val session = state.session!!
        assertEquals(550L, session.charged)
        assertEquals(550L, session.paid)
        assertEquals(-710L, session.carriedOver, "долг 700 + выплата 10 после возврата")
    }

    @Test
    fun `чаевые вышедшему игроку добавляются к его выводу`() {
        val p = playerId(1)
        val q = playerId(2)
        val snapshot = GameSnapshot(
            emptyGame(),
            listOf(Player(p, gameId(1), "П", T0), Player(q, gameId(1), "К", T0)),
            listOf(
                entry(1, BUY_IN, p, 1_000, op = 1),
                entry(2, BUY_IN, q, 1_000, op = 2),
                entry(3, CASH_OUT, q, 1_000, op = 3),
                entry(4, CASH_OUT, p, 1_000, op = 4),
                entry(5, TIP, p, 100, op = 4, counterparty = q),
            ),
            emptyList(),
        )
        val state = GameStates.compute(snapshot)
        val receiver = state.player(q)!!
        assertEquals(100L, receiver.tipsReceived)
        assertEquals(1_100L, receiver.withdrawal)
        assertEquals(PlayerStatus.CASHED_OUT, receiver.status)
        assertEquals(0L, state.player(p)!!.tipsToBanker)
        assertEquals(900L, state.player(p)!!.withdrawal)
        assertEquals(0L, state.table.bankerIncome, "чаевые игроку — не доход банкира")
        assertCashIdentity(state.table)
    }

    @Test
    fun `void-игрок — все записи отменены`() {
        val p = playerId(1)
        val snapshot = single(
            entry(1, BUY_IN, p, 1_000, op = 1),
            entry(2, RAKE, p, 100, op = 1),
            entry(3, REVERSAL, p, 1_000, op = 2, reverses = entryId(1)),
            entry(4, REVERSAL, p, 100, op = 2, reverses = entryId(2)),
        )
        val state = GameStates.compute(snapshot)
        assertTrue(state.player(p)!!.isVoid)
        assertEquals(0, state.table.playersAtTable)
        assertEquals(0, state.table.playersLeft)
        assertFalse(GameStates.compute(single()).player(p)!!.isVoid, "игрок без записей — не void")
    }

    @Test
    fun `таймер и ранний выход — 59 минут ранний, 60 нет`() {
        val p = playerId(1)
        val state = GameStates.compute(single(entry(1, BUY_IN, p, 1_000))).player(p)!!
        assertEquals(47.minutes, state.playedFor(T0 + 47.minutes))
        assertTrue(state.isEarlyExit(GameRules.DEFAULT, T0 + 59.minutes + 59.seconds))
        assertFalse(state.isEarlyExit(GameRules.DEFAULT, T0 + 60.minutes))
        assertFalse(state.isEarlyExit(GameRules(minPlayMinutes = 0), T0))
        val noBuyIn = GameStates.compute(single()).player(p)!!
        assertNull(noBuyIn.playedFor(T0))
        assertFalse(noBuyIn.isEarlyExit(GameRules.DEFAULT, T0))
        assertNull(noBuyIn.session)
        assertEquals(PlayerStatus.PLAYING, noBuyIn.status)
    }

    @Test
    fun `доход разных банкиров раздельно, прощение и забранный доход`() {
        val p = playerId(1)
        val snapshot = single(
            entry(1, BUY_IN, p, 1_000, op = 1),
            entry(2, RAKE, p, 100, op = 1),
            entry(3, BANKER_FEE, p, 100, op = 1),
            entry(4, PAYMENT, p, 1_200, op = 1),
            entry(5, INCOME_TAKEN, null, 100, op = 2),
            entry(6, BUY_IN, p, 1_000, op = 3, banker = "Влад"),
            entry(7, RAKE, p, 100, op = 3, banker = "Влад"),
            entry(8, BANKER_FEE, p, 100, op = 3, banker = "Влад"),
            entry(9, CASH_OUT, p, 500, op = 4, banker = "Влад"),
            entry(10, TIP, p, 50, op = 4, banker = "Влад"),
            entry(11, DEBT_FORGIVEN, p, 300, op = 5, banker = "Влад"),
            entry(12, RAKE_PAID, null, 200, op = 6, banker = "Влад"),
        ).let { it.copy(game = it.game.copy(currentBankerName = "Влад")) }
        val t = GameStates.compute(snapshot).table
        assertEquals(
            listOf(
                BankerIncome("Олег", fees = 100, tips = 0, forgiven = 0, taken = 100),
                BankerIncome("Влад", fees = 100, tips = 50, forgiven = 300, taken = 0),
            ),
            t.incomeByBanker,
        )
        assertEquals(-150L, t.incomeByBanker[1].net)
        assertEquals(-50L, t.bankerIncome)
        assertEquals(100L, t.incomeTaken)
        assertEquals(200L, t.rakePaid)
        assertEquals(0L, t.rakeDue)
        assertEquals(900L, t.cashOnHand)
        assertCashIdentity(t)
        val state = GameStates.compute(snapshot).player(p)!!
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, state.status)
        assertEquals(300L, state.forgiven)
    }

    @Test
    fun `пустая игра — текущий банкир с нулевым доходом`() {
        val state = GameStates.compute(emptySnapshot())
        assertEquals(listOf(BankerIncome("Олег", 0, 0, 0, 0)), state.table.incomeByBanker)
        assertEquals(emptySnapshot().game, state.game)
        assertNull(state.player(playerId(1)))
    }

    @Test
    fun `ранний выход помечается у игрока`() {
        val p = playerId(1)
        val state = GameStates.compute(
            single(entry(1, BUY_IN, p, 1_000), entry(2, CASH_OUT, p, 1_000, op = 2, earlyExit = true)),
        ).player(p)!!
        assertTrue(state.earlyExit)
        assertEquals(0L, state.owedByBanker)
        assertEquals(0L, state.debt)
    }

    private fun single(vararg entries: app.feltbook.core.domain.model.LedgerEntry): GameSnapshot =
        GameSnapshot(emptyGame(), listOf(Player(playerId(1), gameId(1), "П", T0)), entries.toList(), emptyList())
}
