package app.feltbook.core.domain.engine

import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.model.apply
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** Пакет резервной копии с одной игрой читается обратно без потерь. */
private fun assertRoundTrip(snapshot: GameSnapshot) {
    assertEquals(emptyList(), LedgerValidator.validate(snapshot))
    val at = snapshot.game.createdAt
    val text = FeltbookCodec.encode(FeltbookPackage.BackupPackage(at, listOf(snapshot), emptyList()), at)
    val decoded = assertIs<DecodeResult.Ok>(FeltbookCodec.decode(text))
    assertEquals(listOf(snapshot), assertIs<FeltbookPackage.BackupPackage>(decoded.pkg).games)
}

/** Граничные случаи роли qa-engineer: минимум, ноль фишек, лишние фишки, огромные суммы, двойная отправка, границы. */
class EdgeCasesTest {
    @Test
    fun `докуп меньше минимума — блокер, ровно минимум — можно`() {
        val table = TestTable()
        val andrey = table.add("Андрей", 500)
        assertViolation(DomainError.BelowMinBuyIn(500)) { table.buy("Андрей", 499) }
        val quote = Pricing.quoteBuyIn(table.snapshot, andrey, 499)
        assertFalse(quote.canConfirm)
        table.buy("Андрей", 500)
        assertEquals(1_000L, table.state.player(andrey)!!.chipsBought)
    }

    @Test
    fun `минимум 1 фишка — закуп на 1 без рейка-процента, сбор фикс`() {
        val table = TestTable(rules = GameRules(minBuyIn = 1))
        val id = table.add("Андрей", 1)
        val entries = table.snapshot.entries
        assertEquals(listOf(EntryType.BUY_IN, EntryType.BANKER_FEE), entries.map { it.type }, "рейк 10 % с 1 = 0")
        assertEquals(101L, table.state.player(id)!!.debt)
        assertViolation(DomainError.AmountNotPositive) { table.buy("Андрей", 0) }
    }

    @Test
    fun `выход с нулём фишек — вывод 0, весь долг к оплате, чаевые с нуля невозможны`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000)
        table.add("Борис", 1_000, BuyInPayment.PAID)
        table.later()
        assertViolation(DomainError.TipExceedsChips(0)) { table.cashOut("Андрей", 0, Tip(1, TipTarget.Banker)) }
        val quote = CashOuts.quote(table.snapshot, id, 0, null, table.clock.now)
        assertEquals(0L, quote.withdrawal)
        assertEquals(-1_200L, quote.balance)
        table.cashOut("Андрей", 0, settlement = SettlementChoice.None)
        val state = table.state.player(id)!!
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, state.status)
        assertEquals(1_200L, state.debt)
        assertEquals(-1_000L, state.result)
        assertEquals(2_000L, table.state.table.chipsInPlay, "все фишки Андрея у Бориса")
        table.cashOut("Борис", 2_000)
        assertTrue(Settlements.compute(table.snapshot).chipsReconciled)
        assertEquals(setOf(CloseConfirmation.OPEN_DEBTS), Settlements.compute(table.snapshot).requiredConfirmations)
    }

    @Test
    fun `чаевые равны всем фишкам — вывод 0`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000, BuyInPayment.PAID)
        table.later()
        table.cashOut("Андрей", 1_000, Tip(1_000, TipTarget.Banker))
        val state = table.state
        assertEquals(0L, state.player(id)!!.withdrawal)
        assertEquals(1_100L, state.table.bankerIncome, "сбор 100 + чаевые 1 000")
        assertEquals(state.table.cashOnHand, state.table.expectedCash)
    }

    @Test
    fun `фишек на выходе больше, чем в игре — предупреждение, выход проходит, закрыть — с подтверждением`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000, BuyInPayment.PAID)
        table.later()
        val quote = CashOuts.quote(table.snapshot, id, 1_500, null, table.clock.now)
        assertEquals(listOf<DomainWarning>(DomainWarning.ChipsExceedInPlay(1_500, 1_000)), quote.warnings)
        assertTrue(quote.canConfirm, "предупреждение не блокирует")
        assertTrue(CashOuts.quote(table.snapshot, id, 1_000, null, table.clock.now).warnings.isEmpty())
        table.cashOut("Андрей", 1_500)
        assertEquals(-500L, table.state.table.chipsInPlay)
        assertCashIdentity(table.state.table)
        table.apply(Commands.finish(table.snapshot))
        val settlement = Settlements.compute(table.snapshot)
        assertEquals(500L, settlement.chipDiff)
        assertEquals(setOf(CloseConfirmation.CHIPS_NOT_RECONCILED), settlement.requiredConfirmations)
        assertViolation(DomainError.NotConfirmed(setOf(CloseConfirmation.CHIPS_NOT_RECONCILED))) {
            Commands.close(table.snapshot, true, emptySet(), table.clock.stamp())
        }
        table.apply(Commands.close(table.snapshot, true, settlement.requiredConfirmations, table.clock.stamp()))
        assertEquals(GameStatus.CLOSED, table.snapshot.game.status)
    }

    @Test
    fun `10 000 000 — закуп, докуп, выход и расчёт без переполнения, пакет без потерь`() {
        val table = TestTable()
        val big = 10_000_000L
        val andrey = table.add("Андрей", big, BuyInPayment.PAID)
        val boris = table.add("Борис", big)
        table.buy("Борис", big)
        assertEquals(11_000_100L, table.snapshot.entries.first { it.type == EntryType.PAYMENT }.amount)
        assertEquals(22_000_100L, table.state.player(boris)!!.debt)
        table.later()
        assertEquals(53L, CashOuts.roundingTip(25_000_053, 500))
        table.cashOut("Андрей", 25_000_053, Tip(53, TipTarget.Banker))
        table.cashOut("Борис", 4_999_947)
        val state = table.state
        assertEquals(25_000_000L, state.player(andrey)!!.withdrawal)
        assertEquals(15_000_000L, state.player(andrey)!!.result)
        assertEquals(-15_000_053L, state.player(boris)!!.result)
        assertEquals(3_000_253L, state.table.cashOnHand, "пришло 11 000 100 + 17 000 153, выплачено 25 000 000")
        assertEquals(3_000_000L, state.table.rakeDue)
        assertEquals(0L, state.table.chipsInPlay)
        assertEquals(state.table.expectedCash, state.table.cashOnHand)
        assertCashIdentity(state.table)
        assertRoundTrip(table.snapshot)
    }

    @Test
    fun `граница MAX_AMOUNT — закуп, выход и оплата на MAX можно, MAX + 1 — блокер`() {
        val max = Money.MAX_AMOUNT
        val table = TestTable(rules = GameRules(rakeValue = 0, bankerFeeValue = 0))
        val id = table.add("Андрей", max)
        assertEquals(max, table.state.player(id)!!.debt)
        assertViolation(DomainError.AmountTooLarge) { table.buy("Андрей", max + 1) }
        assertViolation(DomainError.AmountTooLarge) { table.pay("Андрей", max + 1) }
        assertViolation(DomainError.AmountTooLarge) {
            Commands.addPlayer(table.snapshot, "Борис", max + 1, BuyInPayment.DEBT, table.clock.stamp())
        }
        table.pay("Андрей", max)
        table.later()
        assertViolation(DomainError.AmountTooLarge) { table.cashOut("Андрей", max + 1) }
        assertFalse(CashOuts.quote(table.snapshot, id, max + 1, null, table.clock.now).canConfirm)
        table.cashOut("Андрей", max)
        assertEquals(0L, table.state.player(id)!!.outstanding)
        assertRoundTrip(table.snapshot)
    }

    @Test
    fun `рейк и сбор с MAX_AMOUNT считаются без переполнения — 100 процентов`() {
        val rules = GameRules(rakeValue = 100, bankerFeeMode = BankerFeeMode.EACH_PERCENT, bankerFeeValue = 100)
        val table = TestTable(rules = rules)
        val quote = Pricing.quoteNewPlayer(table.snapshot, "Андрей", Money.MAX_AMOUNT)
        assertEquals(Money.MAX_AMOUNT, quote.rake)
        assertEquals(Money.MAX_AMOUNT, quote.bankerFee)
        assertEquals(3 * Money.MAX_AMOUNT, quote.total)
    }

    @Ignore("BUG: закуп ~90 млн+ «Оплачено сейчас» пишет PAYMENT > MAX_AMOUNT — валидатор и экспорт отвергают")
    @Test
    fun `закуп MAX_AMOUNT с оплатой сразу — журнал проходит валидатор и экспортируется`() {
        val table = TestTable()
        table.add("Андрей", Money.MAX_AMOUNT, BuyInPayment.PAID)
        // PAYMENT = 100 000 000 + рейк 10 000 000 + сбор 100 = 110 000 100 > MAX_AMOUNT.
        assertRoundTrip(table.snapshot)
    }

    @Ignore("BUG: расчёт при выходе, рейк к отдаче и прощение долга больше MAX_AMOUNT — валидатор отвергает журнал")
    @Test
    fun `расчёт при выходе и отдача рейка больше MAX_AMOUNT — журнал проходит валидатор`() {
        val table = TestTable(rules = GameRules(rakeMode = RakeMode.PERCENT, rakeValue = 100))
        table.add("Андрей", 60_000_000)
        table.buy("Андрей", 60_000_000)
        table.later()
        // Долг 240 000 100: расчёт «Получил» одной записью PAYMENT 240 000 100 > MAX_AMOUNT.
        table.cashOut("Андрей", 0)
        assertRoundTrip(table.snapshot)
        // Рейк к отдаче 120 000 000 — RAKE_PAID одной записью > MAX_AMOUNT.
        table.apply(Commands.payRake(table.snapshot, table.clock.stamp()))
        assertRoundTrip(table.snapshot)
    }

    @Test
    fun `двойная отправка — то же изменение второй раз не применяется, повторный приём и импорт — пусто`() {
        val table = TestTable()
        table.add("Андрей", 1_000)
        val before = table.snapshot
        val change = Commands.buyIn(before, table.id("Андрей"), 1_000, BuyInPayment.DEBT, table.clock.stamp())
        val twice = Commands.buyIn(before, table.id("Андрей"), 1_000, BuyInPayment.DEBT, table.clock.stamp())
        table.apply(change)
        // Команда по устаревшему снимку (двойной тап до обновления экрана) занимает тот же seq — не запишется.
        assertEquals(change.entries.map { it.seq }, twice.entries.map { it.seq })
        assertFailsWith<IllegalArgumentException> { table.snapshot.apply(change) }
        assertFailsWith<IllegalArgumentException> { table.snapshot.apply(twice) }
        assertEquals(1, table.state.player(table.id("Андрей"))!!.rebuyCount)
        // Идемпотентность по operationId в API нет: команда по новому снимку — новая операция (защита — UI, ТЗ §5.1).
        val again = Commands.buyIn(table.snapshot, table.id("Андрей"), 1_000, BuyInPayment.DEBT, table.clock.stamp())
        assertTrue(again.entries.none { e -> change.entries.any { it.operationId == e.operationId } })
        assertEquals(ImportPlan.Identical, Imports.plan(table.snapshot, table.snapshot, table.snapshot.game.id))
    }

    @Test
    fun `ранний выход ровно на границе — 59 минут 59,999 секунды ранний, 60 минут нет`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000)
        val start = table.clock.now
        table.clock.now = start + 60.minutes - 1.milliseconds
        val quote = CashOuts.quote(table.snapshot, id, 1_000, null, table.clock.now)
        assertTrue(quote.isEarlyExit)
        assertEquals(59L, quote.minutesPlayed)
        table.clock.now = start + 60.minutes
        assertFalse(CashOuts.quote(table.snapshot, id, 1_000, null, table.clock.now).isEarlyExit)
        assertEquals(60L, CashOuts.quote(table.snapshot, id, 1_000, null, table.clock.now).minutesPlayed)
    }

    @Test
    fun `минимальное время 0 — выход никогда не ранний`() {
        val table = TestTable(rules = GameRules(minPlayMinutes = 0))
        val id = table.add("Андрей", 1_000)
        assertFalse(CashOuts.quote(table.snapshot, id, 1_000, null, table.clock.now).isEarlyExit)
        table.cashOut("Андрей", 1_000)
        assertFalse(table.state.player(id)!!.earlyExit)
    }

    @Test
    fun `таймер — от собственного первого закупа, вернувшийся считает от первого закупа в игре`() {
        val table = TestTable()
        table.add("Андрей", 1_000)
        table.clock.advance(45.minutes)
        val boris = table.add("Борис", 1_000)
        table.clock.advance(20.minutes)
        assertFalse(table.state.player(table.id("Андрей"))!!.isEarlyExit(table.snapshot.game.rules, table.clock.now))
        val quote = CashOuts.quote(table.snapshot, boris, 1_000, null, table.clock.now)
        assertTrue(quote.isEarlyExit, "Борис в игре 20 мин")
        assertEquals(20L, quote.minutesPlayed)
        table.cashOut("Андрей", 1_000)
        table.buy("Андрей", 500)
        val back = CashOuts.quote(table.snapshot, table.id("Андрей"), 500, null, table.clock.now)
        assertFalse(back.isEarlyExit, "возврат — тот же таймер от первого закупа")
    }

    @Test
    fun `частичный расчёт на границе — от 1 до баланса минус 1`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000)
        table.later()
        assertViolation(DomainError.PartialOutOfRange(1_199)) {
            table.cashOut("Андрей", 0, settlement = SettlementChoice.Partial(1_200))
        }
        assertViolation(DomainError.PartialOutOfRange(1_199)) {
            table.cashOut("Андрей", 0, settlement = SettlementChoice.Partial(0))
        }
        table.cashOut("Андрей", 0, settlement = SettlementChoice.Partial(1_199))
        assertEquals(1L, table.state.player(id)!!.debt)
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, table.state.player(id)!!.status)
    }
}
