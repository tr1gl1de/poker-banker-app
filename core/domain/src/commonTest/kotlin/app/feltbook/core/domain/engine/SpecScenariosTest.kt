package app.feltbook.core.domain.engine

import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.tableFrom
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Сквозные сценарии ТЗ (роль qa-engineer): каждый — от первой команды до итога, с тождеством кассы на каждом шаге. */
class SpecScenariosTest {
    private fun TestTable.checked(): TestTable = apply {
        assertCashIdentity(state.table)
        assertEquals(emptyList(), LedgerValidator.validate(snapshot))
    }

    private fun roundTrip(snapshot: GameSnapshot) {
        val at = snapshot.game.createdAt
        val text = FeltbookCodec.encode(FeltbookPackage.BackupPackage(at, listOf(snapshot), emptyList()), at)
        val pkg = assertIs<DecodeResult.Ok>(FeltbookCodec.decode(text)).pkg
        val games = assertIs<FeltbookPackage.BackupPackage>(pkg).games
        assertEquals(listOf(snapshot), games)
        assertEquals(ImportPlan.Identical, Imports.plan(games.single(), snapshot, null))
    }

    @Test
    fun `§3_6 целиком — сверка 5 000, рейк 500, банкиру 353, касса 853, закрытие и пакет`() {
        val table = example81().checked()
        table.apply(Commands.finish(table.snapshot))
        val s = Settlements.compute(table.snapshot)
        assertEquals(5_000L, s.chipsIssued)
        assertEquals(5_000L, s.chipsReturned)
        assertTrue(s.chipsReconciled)
        assertEquals(500L, s.rakeDue)
        assertEquals(300L, s.bankerFees)
        assertEquals(53L, s.tipsToBanker)
        assertEquals(353L, s.bankerIncome)
        assertEquals(853L, s.cashOnHand)
        assertEquals(853L, s.expectedCash)
        assertEquals(1, s.rebuyCount)
        val rows = s.rows.associateBy { it.name }
        assertEquals(-2_300L, rows.getValue("Андрей").balanceAtExit)
        assertEquals(3_700L, rows.getValue("Борис").balanceAtExit)
        assertEquals(-1_053L, rows.getValue("Влад").balanceAtExit)
        assertTrue(s.rows.all { it.outstanding == 0L })
        assertEquals(emptySet(), s.requiredConfirmations)
        table.apply(Commands.close(table.snapshot, payRake = true, emptySet(), table.clock.stamp()))
        table.checked()
        assertEquals(353L, table.state.table.cashOnHand, "рейк отдан — на руках доход банкира")
        assertEquals(-53L, table.state.players.sumOf { it.result!! }, "Σ результатов = −чаевые банкиру")
        roundTrip(table.snapshot)
    }

    @Test
    fun `игрок вышел и вернулся — второй выход только по новым покупкам, итог собирает оба выхода`() {
        val table = TestTable()
        val boris = table.add("Борис", 1_000, BuyInPayment.PAID)
        table.add("Андрей", 2_000, BuyInPayment.PAID)
        table.later()
        table.cashOut("Борис", 1_500).also { table.checked() }
        assertEquals(PlayerStatus.CASHED_OUT, table.state.player(boris)!!.status)
        assertEquals(1_500L, table.state.table.chipsInPlay)
        val back = Pricing.quoteBuyIn(table.snapshot, boris, 1_000)
        assertTrue(back.isReturn)
        assertEquals(100L, back.rake)
        assertEquals(0L, back.bankerFee, "разовый сбор при возврате не повторяется")
        table.buy("Борис", 1_000).also { table.checked() }
        val session = table.state.player(boris)!!.session!!
        assertEquals(1_000L, session.chipsBought)
        assertEquals(1_100L, session.charged)
        assertEquals(0L, session.carriedOver, "прошлый выход рассчитан")
        table.later()
        val quote = CashOuts.quote(table.snapshot, boris, 300, null, table.clock.now)
        assertEquals(-800L, quote.balance, "300 − 1 100 новых покупок")
        table.cashOut("Борис", 300).also { table.checked() }
        table.cashOut("Андрей", 2_200).also { table.checked() }
        val state = table.state.player(boris)!!
        assertEquals(1, state.rebuyCount)
        assertEquals(1_800L, state.withdrawal)
        assertEquals(-200L, state.result)
        assertEquals(0L, table.state.table.chipsInPlay)
        assertEquals(2, table.snapshot.entries.count { it.playerId == boris && it.type == EntryType.CASH_OUT })
    }

    @Test
    fun `ушёл с долгом → вернулся в ту же игру → новый выход гасит и старый долг`() {
        val table = TestTable()
        val vlad = table.add("Влад", 2_000)
        table.add("Борис", 1_000, BuyInPayment.PAID)
        table.later()
        table.cashOut("Влад", 1_247, settlement = SettlementChoice.None).also { table.checked() }
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, table.state.player(vlad)!!.status)
        assertEquals(1_053L, table.state.player(vlad)!!.debt)
        table.buy("Влад", 1_000, BuyInPayment.PAID).also { table.checked() }
        val session = table.state.player(vlad)!!.session!!
        assertEquals(-1_053L, session.carriedOver, "«Долг с прошлого выхода»")
        table.later()
        val quote = CashOuts.quote(table.snapshot, vlad, 2_000, null, table.clock.now)
        assertEquals(-1_053L, quote.carriedOver)
        assertEquals(947L, quote.balance, "2 000 − 1 053 старого долга")
        table.cashOut("Влад", 2_000).also { table.checked() }
        table.cashOut("Борис", 753).also { table.checked() }
        val state = table.state.player(vlad)!!
        assertEquals(PlayerStatus.CASHED_OUT, state.status)
        assertEquals(0L, state.outstanding)
        assertFalse(table.state.hasOpenDebt)
        assertEquals(table.state.table.expectedCash, table.state.table.cashOnHand)
    }

    @Test
    fun `ушёл с долгом → сел за новый стол → «Погасить сейчас» пишется в старую игру`() {
        val old = example81(vladPays = false)
        old.apply(Commands.finish(old.snapshot))
        old.apply(Commands.close(old.snapshot, true, setOf(CloseConfirmation.OPEN_DEBTS), old.clock.stamp())).also {
            old.checked()
        }
        val vladOld = old.id("Влад")
        val next = TestTable(banker = "Пётр", clock = old.clock)
        val warning = Debts.forName(" Влад ", listOf(old.snapshot, next.snapshot), emptyList(), next.snapshot.game.id)
        assertEquals(1_053L, warning.single().amount)
        assertEquals(old.snapshot.game.id, warning.single().gameId)
        next.add("Влад", 1_000, BuyInPayment.PAID).also { next.checked() }
        old.apply(Commands.pay(old.snapshot, vladOld, 1_053, old.clock.stamp(), bankerName = "Пётр")).also {
            old.checked()
        }
        assertEquals(GameStatus.CLOSED, old.snapshot.game.status)
        assertFalse(old.snapshot.game.editedAfterClose)
        assertFalse(old.state.hasOpenDebt)
        assertEquals("Пётр", old.snapshot.entries.last().bankerName)
        assertEquals(old.state.table.expectedCash, old.state.table.cashOnHand)
        assertTrue(Debts.open(listOf(old.snapshot, next.snapshot), emptyList()).isEmpty())
        assertEquals(1_200L, next.state.table.cashOnHand, "новая игра не затронута погашением")
    }

    @Test
    fun `ранний выход — только с подтверждением «Исключительный случай», пометка в итоге и журнале`() {
        val table = TestTable()
        val boris = table.add("Борис", 1_000, BuyInPayment.PAID)
        table.add("Андрей", 1_000, BuyInPayment.PAID)
        table.clock.advance(47.minutes)
        val quote = CashOuts.quote(table.snapshot, boris, 1_000, null, table.clock.now)
        assertTrue(quote.isEarlyExit)
        assertEquals(47L, quote.minutesPlayed)
        assertViolation(DomainError.EarlyExitNotConfirmed(47, 60)) { table.cashOut("Борис", 1_000) }
        table.cashOut("Борис", 1_000, early = true).also { table.checked() }
        table.later()
        table.cashOut("Андрей", 1_000).also { table.checked() }
        val rows = Settlements.compute(table.snapshot).rows.associateBy { it.name }
        assertTrue(rows.getValue("Борис").earlyExit)
        assertFalse(rows.getValue("Андрей").earlyExit)
        val exit = Journal.forPlayer(table.snapshot, boris).first { it.kind == OperationKind.CASH_OUT }
        assertTrue(exit.entries.first().earlyExit)
    }

    @Test
    fun `банкир-игрок — рейк в кассу, сбора нет, расчёт относительно своей кассы`() {
        val table = TestTable(banker = "Андрей", bankerPlays = true)
        val andrey = table.id("Андрей")
        table.buy("Андрей", 1_000, BuyInPayment.PAID)
        assertTrue(table.snapshot.entries.none { it.type == EntryType.BANKER_FEE })
        table.add("Дима", 1_000)
        table.later()
        table.cashOut("Дима", 400).also { table.checked() }
        table.cashOut("Андрей", 1_600).also { table.checked() }
        val state = table.state
        assertTrue(state.player(andrey)!!.isBanker)
        assertEquals(1_600L, state.player(andrey)!!.paidOut, "банкир выплатил себе из кассы")
        assertEquals(600L, state.player(andrey)!!.result)
        assertEquals(200L, state.table.rakeDue)
        assertEquals(100L, state.table.bankerIncome, "сбор только с Димы")
        assertEquals(300L, state.table.cashOnHand)
        assertEquals(state.table.expectedCash, state.table.cashOnHand)
    }

    @Test
    fun `чаевые игроку — сидящему фишками, вышедшему рублями, банкир выплачивает вышедшему`() {
        val table = TestTable()
        val a = table.add("Андрей", 1_000, BuyInPayment.PAID)
        val b = table.add("Борис", 1_000, BuyInPayment.PAID)
        val v = table.add("Влад", 1_000, BuyInPayment.PAID)
        table.later()
        table.cashOut("Андрей", 500).also { table.checked() }
        table.cashOut("Борис", 1_200, Tip(200, TipTarget.ToPlayer(v))).also { table.checked() }
        assertEquals(EntryType.TIP_CHIPS, table.snapshot.entries.first { it.counterpartyPlayerId == v }.type)
        table.cashOut("Влад", 1_500, Tip(100, TipTarget.ToPlayer(a))).also { table.checked() }
        val tip = table.snapshot.entries.last { it.counterpartyPlayerId == a }
        assertEquals(EntryType.TIP, tip.type)
        val state = table.state
        assertEquals(100L, state.player(a)!!.owedByBanker, "банкир должен Андрею его чаевые")
        assertEquals(600L, state.player(a)!!.withdrawal)
        assertEquals(1_000L, state.player(b)!!.withdrawal)
        assertEquals(1_400L, state.player(v)!!.withdrawal)
        assertEquals(0L, state.table.chipsInPlay, "фишки сошлись: фишечные чаевые не задвоены")
        table.payOut("Андрей", 100).also { table.checked() }
        assertEquals(0L, table.state.table.bankerOwes)
        assertEquals(0L, table.state.players.sumOf { it.result!! })
    }

    @Test
    fun `отмена записи — «Отменено» в журнале, суммы как без неё, касса сходится`() {
        val table = TestTable()
        table.add("Андрей", 1_000)
        table.add("Борис", 1_000)
        val wrong = table.buy("Андрей", 5_000).entries.first().operationId
        val before = table.snapshot
        table.reverse(wrong, " ошибся суммой ").also { table.checked() }
        assertReversalIsStrikeOut(before, table.snapshot, wrong)
        val item = Journal.items(table.snapshot).filterIsInstance<JournalItem.Operation>()
            .first { it.operationId == wrong }
        assertEquals("ошибся суммой", item.reversal!!.reason)
        assertFalse(item.reversible)
        assertEquals(0, table.state.table.rebuyCount)
        assertEquals(2_400L, table.state.table.playersOwe)
        assertEquals(EntryType.REVERSAL, table.snapshot.entries.last().type)
        assertEquals(before.entries, table.snapshot.entries.take(before.entries.size), "журнал только дописан")
    }

    @Test
    fun `передача комнаты посреди игры — касса и доход старого банкира, игра продолжается у нового`() {
        val table = example84Start()
        val prepared = Handovers.prepare(table.snapshot, "Влад", keepIncome = true, stamp = table.clock.stamp())
        val change = Handovers.accept(prepared.outgoing, prepared.handover, null, null, null)
        val vlad = tableFrom(receive(prepared.outgoing, change)).checked()
        assertNull(vlad.snapshot.game.bankerPlayerId)
        vlad.later()
        vlad.cashOut("Егор", 1_500, Tip(100, TipTarget.Banker)).also { vlad.checked() }
        vlad.cashOut("Дима", 1_000, settlement = SettlementChoice.None).also { vlad.checked() }
        vlad.cashOut("Андрей", 500).also { vlad.checked() }
        vlad.apply(Commands.finish(vlad.snapshot))
        val s = Settlements.compute(vlad.snapshot)
        assertEquals(
            listOf(BankerIncome("Андрей", 200, 0, 0, 200), BankerIncome("Влад", 0, 100, 0, 0)),
            s.incomeByBanker,
        )
        assertEquals(200L, s.incomeTaken)
        assertEquals(s.expectedCash - s.shortage, s.cashOnHand, "недостача — долг Димы")
        assertEquals(200L, s.shortage)
        vlad.apply(
            Commands.close(vlad.snapshot, true, s.requiredConfirmations, vlad.clock.stamp()),
        ).also { vlad.checked() }
        roundTrip(vlad.snapshot)
    }
}
