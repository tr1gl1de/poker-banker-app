package app.feltbook.core.domain.engine

import app.feltbook.core.domain.T0
import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.playerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Пример 8.1 командами: Андрей / Борис / Влад, банкир Олег не играет. */
fun example81(table: TestTable = TestTable(), vladPays: Boolean = true): TestTable = table.apply {
    add("Андрей", 1_000)
    add("Борис", 1_000, BuyInPayment.PAID)
    add("Влад", 2_000)
    buy("Андрей", 1_000)
    later()
    val boris = CashOuts.quote(snapshot, id("Борис"), 3_753, null, clock.now)
    assertEquals(53L, CashOuts.roundingTip(boris.chips, 100))
    cashOut("Борис", 3_753, Tip(53, TipTarget.Banker))
    cashOut("Влад", 1_247, settlement = if (vladPays) SettlementChoice.Full else SettlementChoice.None)
    cashOut("Андрей", 0)
}

class CashOutExample81Test {
    @Test
    fun `сквозной пример 8_1 командами — записи журнала совпадают с таблицей`() {
        val table = example81()
        val e = table.snapshot.entries
        assertEquals((1L..19L).toList(), e.map { it.seq })
        val a = table.id("Андрей")
        val b = table.id("Борис")
        val v = table.id("Влад")
        val expected = listOf(
            Triple(EntryType.BUY_IN, a, 1_000L),
            Triple(EntryType.RAKE, a, 100L),
            Triple(EntryType.BANKER_FEE, a, 100L),
            Triple(EntryType.BUY_IN, b, 1_000L),
            Triple(EntryType.RAKE, b, 100L),
            Triple(EntryType.BANKER_FEE, b, 100L),
            Triple(EntryType.PAYMENT, b, 1_200L),
            Triple(EntryType.BUY_IN, v, 2_000L),
            Triple(EntryType.RAKE, v, 200L),
            Triple(EntryType.BANKER_FEE, v, 100L),
            Triple(EntryType.BUY_IN, a, 1_000L),
            Triple(EntryType.RAKE, a, 100L),
            Triple(EntryType.CASH_OUT, b, 3_753L),
            Triple(EntryType.TIP, b, 53L),
            Triple(EntryType.PAYOUT, b, 3_700L),
            Triple(EntryType.CASH_OUT, v, 1_247L),
            Triple(EntryType.PAYMENT, v, 1_053L),
            Triple(EntryType.CASH_OUT, a, 0L),
            Triple(EntryType.PAYMENT, a, 2_300L),
        )
        assertEquals(expected, e.map { Triple(it.type, it.playerId!!, it.amount) })
        assertNull(e[13].counterpartyPlayerId, "чаевые банкиру")
        assertEquals(10, e.map { it.operationId }.distinct().size, "op1–op10")
        assertTrue(e[12].operationId == e[13].operationId && e[13].operationId != e[14].operationId)

        val t = table.state.table
        assertEquals(500L, t.rakeAccrued)
        assertEquals(353L, t.bankerIncome)
        assertEquals(4_553L, e.filter { it.type == EntryType.PAYMENT }.sumOf { it.amount }, "пришло")
        assertEquals(3_700L, e.filter { it.type == EntryType.PAYOUT }.sumOf { it.amount }, "выплачено")
        assertEquals(853L, t.cashOnHand)
        assertEquals(853L, t.expectedCash)
        assertEquals(0L, t.chipsInPlay)
        assertCashIdentity(t)
        assertEquals(-1_053L, table.state.player(v)!!.balanceAtExit)
    }

    @Test
    fun `котировка выхода Влада — платит 1 053`() {
        val table = TestTable().apply {
            add("Влад", 2_000)
            later()
        }
        val quote = CashOuts.quote(table.snapshot, table.id("Влад"), 1_247, null, table.clock.now)
        assertEquals(1_247L, quote.withdrawal)
        assertEquals(2_300L, quote.sessionCharged)
        assertEquals(0L, quote.sessionPaid)
        assertEquals(0L, quote.carriedOver)
        assertEquals(-2_300L, quote.outstandingBefore)
        assertEquals(-1_053L, quote.balance)
        assertEquals(-753L, quote.resultThisGame)
        assertEquals(61L, quote.minutesPlayed)
        assertFalse(quote.isEarlyExit)
        assertFalse(quote.tipAsChips)
        assertTrue(quote.canConfirm)
        assertTrue(quote.warnings.isEmpty())
    }
}

class CashOutRulesTest {
    @Test
    fun `выход на 59-й минуте без подтверждения — ошибка, с подтверждением — пометка`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000)
        table.clock.advance(59.minutes + 59.seconds)
        val quote = CashOuts.quote(table.snapshot, id, 1_000, null, table.clock.now)
        assertTrue(quote.isEarlyExit)
        assertTrue(quote.canConfirm, "ранний выход — не блокер котировки")
        assertViolation(DomainError.EarlyExitNotConfirmed(59, 60)) { table.cashOut("Андрей", 1_000) }
        val change = table.cashOut("Андрей", 1_000, early = true)
        assertTrue(change.entries.first().earlyExit)
        assertTrue(table.state.player(id)!!.earlyExit)
    }

    @Test
    fun `ровно 60 минут — выход не ранний`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000)
        table.clock.advance(60.minutes)
        assertFalse(CashOuts.quote(table.snapshot, id, 1_000, null, table.clock.now).isEarlyExit)
        val change = table.cashOut("Андрей", 1_000)
        assertFalse(change.entries.first().earlyExit)
    }

    @Test
    fun `чаевые банкиру — в доход, W = F − T`() {
        val table = TestTable().apply {
            add("Борис", 1_000, BuyInPayment.PAID)
            later()
        }
        val change = table.cashOut("Борис", 3_753, Tip(253, TipTarget.Banker))
        assertEquals(listOf(EntryType.CASH_OUT, EntryType.TIP, EntryType.PAYOUT), change.entries.map { it.type })
        assertEquals(3_500L, change.entries.last().amount)
        assertEquals(353L, table.state.table.bankerIncome)
        assertEquals(253L, CashOuts.roundingTip(3_753, 500))
    }

    @Test
    fun `чаевые игроку за столом — фишками, сверка фишек сходится`() {
        val table = TestTable().apply {
            add("Егор", 1_000, BuyInPayment.PAID)
            add("Дима", 1_000)
            later()
        }
        val dima = table.id("Дима")
        val quote = CashOuts.quote(
            table.snapshot,
            table.id("Егор"),
            1_500,
            Tip(100, TipTarget.ToPlayer(dima)),
            table.clock.now,
        )
        assertTrue(quote.tipAsChips)
        assertEquals(1_400L, quote.withdrawal)
        val change = table.cashOut("Егор", 1_500, Tip(100, TipTarget.ToPlayer(dima)))
        val tip = change.entries[1]
        assertEquals(EntryType.TIP_CHIPS, tip.type)
        assertEquals(dima, tip.counterpartyPlayerId)
        assertEquals(1_400L, change.entries[2].amount)
        table.cashOut("Дима", 600)
        val t = table.state.table
        assertEquals(2_000L, t.chipsIssued)
        assertEquals(2_000L, t.chipsReturned)
        assertEquals(100L, table.state.player(dima)!!.chipTipsReceived)
        assertCashIdentity(t)
    }

    @Test
    fun `чаевые вышедшему игроку — рублями к его выводу, банкир становится должен`() {
        val table = TestTable().apply {
            add("Борис", 1_000, BuyInPayment.PAID)
            add("Влад", 1_000, BuyInPayment.PAID)
            later()
            cashOut("Борис", 1_000)
        }
        val boris = table.id("Борис")
        val change = table.cashOut("Влад", 1_000, Tip(100, TipTarget.ToPlayer(boris)))
        assertEquals(EntryType.TIP, change.entries[1].type)
        assertEquals(boris, change.entries[1].counterpartyPlayerId)
        val state = table.state
        assertEquals(100L, state.player(boris)!!.owedByBanker)
        assertEquals(PlayerStatus.CASHED_OUT, state.player(boris)!!.status)
        assertEquals(100L, state.table.bankerOwes)
        assertEquals(200L, state.table.bankerIncome, "только сборы — чаевые игроку не доход банкира")
        assertCashIdentity(state.table)
        table.payOut("Борис", 100)
        assertEquals(0L, table.state.table.bankerOwes)
    }

    @Test
    fun `блокеры чаевых — больше F, себе, ноль, неизвестному`() {
        val table = TestTable().apply {
            add("Андрей", 1_000)
            later()
        }
        val a = table.id("Андрей")
        fun blockers(tip: Tip, chips: Long = 500) =
            CashOuts.quote(table.snapshot, a, chips, tip, table.clock.now).blockers
        assertEquals(listOf<DomainError>(DomainError.TipExceedsChips(500)), blockers(Tip(501, TipTarget.Banker)))
        assertEquals(listOf<DomainError>(DomainError.TipToSelf), blockers(Tip(100, TipTarget.ToPlayer(a))))
        assertEquals(listOf<DomainError>(DomainError.TipNotPositive), blockers(Tip(0, TipTarget.Banker)))
        assertEquals(
            listOf<DomainError>(DomainError.TipRecipientInvalid(playerId(99))),
            blockers(Tip(100, TipTarget.ToPlayer(playerId(99)))),
        )
        assertViolation(DomainError.TipExceedsChips(500)) { table.cashOut("Андрей", 500, Tip(501, TipTarget.Banker)) }
    }

    @Test
    fun `частичный расчёт — остаток в долг, «не заплатил» — долг целиком`() {
        val table = TestTable().apply {
            add("Влад", 2_000)
            add("Андрей", 1_000)
            later()
        }
        assertViolation(DomainError.PartialOutOfRange(1_052)) {
            table.cashOut("Влад", 1_247, settlement = SettlementChoice.Partial(1_053))
        }
        assertViolation(DomainError.PartialOutOfRange(1_052)) {
            table.cashOut("Влад", 1_247, settlement = SettlementChoice.Partial(0))
        }
        val change = table.cashOut("Влад", 1_247, settlement = SettlementChoice.Partial(600))
        assertEquals(EntryType.PAYMENT, change.entries.last().type)
        assertEquals(600L, change.entries.last().amount)
        val vlad = table.state.player(table.id("Влад"))!!
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, vlad.status)
        assertEquals(453L, vlad.debt)
        val none = table.cashOut("Андрей", 0, settlement = SettlementChoice.None)
        assertEquals(listOf(EntryType.CASH_OUT), none.entries.map { it.type })
        assertEquals(1_200L, table.state.player(table.id("Андрей"))!!.debt)
    }

    @Test
    fun `частичная выплата банкиром — остаток банкир должен`() {
        val table = TestTable().apply {
            add("Борис", 1_000, BuyInPayment.PAID)
            later()
        }
        val change = table.cashOut("Борис", 3_000, settlement = SettlementChoice.Partial(1_000))
        assertEquals(EntryType.PAYOUT, change.entries.last().type)
        assertEquals(2_000L, table.state.player(table.id("Борис"))!!.owedByBanker)
    }

    @Test
    fun `квиты — расчёт без записи, частичный расчёт невозможен`() {
        val table = TestTable().apply {
            add("Борис", 1_000)
            later()
        }
        assertViolation(DomainError.NothingToSettle) {
            table.cashOut("Борис", 1_200, settlement = SettlementChoice.Partial(1))
        }
        val change = table.cashOut("Борис", 1_200)
        assertEquals(listOf(EntryType.CASH_OUT), change.entries.map { it.type })
        assertEquals(PlayerStatus.CASHED_OUT, table.state.player(table.id("Борис"))!!.status)
    }

    @Test
    fun `фишек на выходе больше, чем в игре — предупреждение`() {
        val table = TestTable().apply {
            add("Борис", 1_000)
            later()
        }
        val quote = CashOuts.quote(table.snapshot, table.id("Борис"), 1_001, null, table.clock.now)
        assertEquals(listOf<DomainWarning>(DomainWarning.ChipsExceedInPlay(1_001, 1_000)), quote.warnings)
        assertTrue(quote.canConfirm)
    }

    @Test
    fun `выход вышедшего, неизвестного, в закрытой игре, с плохой суммой — блокеры`() {
        val table = TestTable().apply {
            add("Борис", 1_000)
            later()
            cashOut("Борис", 1_000, settlement = SettlementChoice.None)
        }
        val b = table.id("Борис")
        assertViolation(DomainError.PlayerNotPlaying(b, PlayerStatus.LEFT_WITH_DEBT)) { table.cashOut("Борис", 0) }
        val unknown = CashOuts.quote(table.snapshot, playerId(99), 0, null, T0)
        assertEquals(listOf<DomainError>(DomainError.PlayerNotFound(playerId(99))), unknown.blockers)
        assertNull(unknown.minutesPlayed)
        assertFalse(unknown.isEarlyExit)
        val closed = table.snapshot.copy(game = table.snapshot.game.copy(status = GameStatus.CLOSED))
        assertTrue(
            DomainError.WrongGameStatus(GameStatus.CLOSED, setOf(GameStatus.RUNNING, GameStatus.SETTLING)) in
                CashOuts.quote(closed, b, 0, null, T0).blockers,
        )
        val other = TestTable().apply { add("Х", 1_000) }
        val x = other.id("Х")
        assertEquals(
            listOf<DomainError>(DomainError.AmountNotPositive),
            CashOuts.quote(other.snapshot, x, -1, null, T0).blockers,
        )
        assertEquals(
            listOf<DomainError>(DomainError.AmountTooLarge),
            CashOuts.quote(other.snapshot, x, Money.MAX_AMOUNT + 1, null, T0).blockers,
        )
    }

    @Test
    fun `быстрое округление — шаг должен быть положительным`() {
        assertEquals(53L, CashOuts.roundingTip(3_753, 100))
        assertEquals(0L, CashOuts.roundingTip(3_700, 100))
        assertFailsWith<IllegalArgumentException> { CashOuts.roundingTip(3_753, 0) }
        assertFailsWith<IllegalArgumentException> { CashOuts.roundingTip(-1, 100) }
    }

    @Test
    fun `пример 8_3 командами — банкир-игрок, чаевые фишками, возврат, частичная оплата`() {
        val table = TestTable(banker = "Андрей", bankerPlays = true).apply {
            buy("Андрей", 1_000, BuyInPayment.PAID)
            add("Дима", 1_000)
            add("Егор", 1_000, BuyInPayment.PAID)
            later()
        }
        val dima = table.id("Дима")
        assertEquals(10L, table.snapshot.entries.size.toLong())
        table.cashOut("Егор", 1_500, Tip(100, TipTarget.ToPlayer(dima)))
        val back = table.buy("Егор", 500)
        assertEquals(listOf(EntryType.BUY_IN, EntryType.RAKE), back.entries.map { it.type })
        table.cashOut("Дима", 1_300, Tip(50, TipTarget.Banker))
        val egorQuote = CashOuts.quote(table.snapshot, table.id("Егор"), 200, null, table.clock.now)
        assertEquals(0L, egorQuote.carriedOver)
        assertEquals(550L, egorQuote.sessionCharged)
        assertEquals(-350L, egorQuote.balance)
        assertEquals(100L, egorQuote.resultThisGame)
        table.cashOut("Егор", 200, settlement = SettlementChoice.Partial(100))
        table.cashOut("Андрей", 600)
        val e = table.snapshot.entries
        assertEquals(22, e.size)
        assertEquals(
            listOf(EntryType.CASH_OUT, EntryType.TIP_CHIPS, EntryType.PAYOUT),
            e.subList(10, 13).map { it.type },
        )
        assertEquals(
            listOf(1_400L, 50L, 100L, 600L),
            e.filter {
                it.seq in setOf(13L, 18L, 20L, 22L)
            }.map { it.amount },
        )
        val t = table.state.table
        assertEquals(3_500L, t.chipsIssued)
        assertEquals(3_500L, t.chipsReturned)
        assertEquals(350L, t.rakeAccrued)
        assertEquals(250L, t.bankerIncome)
        assertEquals(600L, t.expectedCash)
        assertEquals(350L, t.cashOnHand)
        assertEquals(250L, table.state.player(table.id("Егор"))!!.debt)
        assertCashIdentity(t)
    }
}
