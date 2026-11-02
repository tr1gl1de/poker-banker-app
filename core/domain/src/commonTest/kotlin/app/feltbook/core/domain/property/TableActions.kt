package app.feltbook.core.domain.property

import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.BuyInQuote
import app.feltbook.core.domain.engine.CashOutQuote
import app.feltbook.core.domain.engine.CashOuts
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.DomainError
import app.feltbook.core.domain.engine.DomainWarning
import app.feltbook.core.domain.engine.GameState
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.engine.Ledger
import app.feltbook.core.domain.engine.Money
import app.feltbook.core.domain.engine.PlayerState
import app.feltbook.core.domain.engine.Pricing
import app.feltbook.core.domain.engine.SettlementChoice
import app.feltbook.core.domain.engine.Suspicion
import app.feltbook.core.domain.engine.Tip
import app.feltbook.core.domain.engine.TipTarget
import app.feltbook.core.domain.engine.assertReversalIsStrikeOut
import app.feltbook.core.domain.engine.isSeated
import app.feltbook.core.domain.engine.reversalBlocker
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.RakeMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Действия за столом активной игры: покупки, оплаты, выходы с чаевыми и расчётом, отмены, прощение, рейк. */
@Suppress("TooManyFunctions")
internal class TableActions(private val sim: Sim) {
    private val random get() = sim.random

    // ---- Покупки ----

    fun addPlayer(returning: String?, onDebt: (GameSnapshot, String) -> Unit) {
        val raw = returning ?: NAMES.random(random)
        val name = if (random.nextInt(8) == 0) "  $raw " else raw
        onDebt(sim.activeSnapshot(), name)
        val s = sim.activeSnapshot()
        val chips = amount(s.game.rules)
        val payment = payment(s.game.rules)
        sim.note("add '$name' $chips $payment")
        val quote = Pricing.quoteNewPlayer(s, name, chips)
        val expected = quote.blockers.firstOrNull() ?: splitBlocker(s.game.rules, payment)
        val change = sim.attempt(expected) {
            Commands.addPlayer(s, name, chips, payment, sim.clock.stamp())
        } ?: return
        purchase(s, change, quote, payment)
        sim.count("add")
    }

    fun buyIn() {
        val s = sim.activeSnapshot()
        val state = GameStates.compute(s)
        val target = state.players.filterNot { it.isVoid }.randomOrNull(random) ?: return
        val chips = amount(s.game.rules)
        val payment = payment(s.game.rules)
        sim.note("buy ${target.player.name} $chips $payment (${target.status})")
        val quote = Pricing.quoteBuyIn(s, target.player.id, chips)
        assertEquals(target.status != PlayerStatus.PLAYING, quote.isReturn, "возврат вышедшего — докуп")
        val expected = quote.blockers.firstOrNull() ?: splitBlocker(s.game.rules, payment)
        val change = sim.attempt(expected) {
            Commands.buyIn(s, target.player.id, chips, payment, sim.clock.stamp())
        } ?: return
        purchase(s, change, quote, payment)
        sim.count(if (quote.isReturn) "return" else "rebuy")
    }

    /** Покупка: записи совпадают с котировкой и с независимым расчётом формул ТЗ §3.2. */
    @Suppress("CyclomaticComplexMethod")
    private fun purchase(before: GameSnapshot, change: GameChange, quote: BuyInQuote, payment: BuyInPayment) {
        val entries = change.entries
        val buy = entries.first()
        val playerId = buy.playerId!!
        val rules = before.game.rules
        val chips = quote.chips
        assertEquals(EntryType.BUY_IN, buy.type)
        assertEquals(chips, buy.amount)
        assertEquals(1, entries.map { it.operationId }.distinct().size, "покупка — одна операция")
        val isBanker = (change.game ?: before.game).bankerPlayerId == playerId
        val first = (GameStates.compute(before).player(playerId)?.takeUnless { it.isVoid }?.buyInCount ?: 0) == 0
        val rake = if (rules.rakeMode == RakeMode.PERCENT) roundPercent(chips, rules.rakeValue) else rules.rakeValue
        val fee = when {
            isBanker -> 0L
            rules.bankerFeeMode.isOnce && !first -> 0L
            rules.bankerFeeMode.isPercent -> roundPercent(chips, rules.bankerFeeValue)
            else -> rules.bankerFeeValue
        }
        assertEquals(rake, entries.sumOf { if (it.type == EntryType.RAKE) it.amount else 0L }, "рейк §3.2")
        assertEquals(fee, entries.sumOf { if (it.type == EntryType.BANKER_FEE) it.amount else 0L }, "сбор §3.2")
        assertEquals(chips + rake + fee, quote.total, "к оплате = D + рейк + сбор")
        val paid = (if (payment.chipsPaid) chips else 0L) + (if (payment.feesPaid) rake + fee else 0L)
        assertEquals(paid, entries.sumOf { if (it.type == EntryType.PAYMENT) it.amount else 0L }, "оплачено сразу")
        assertTrue(entries.none { it.amount == 0L }, "нулевые записи не пишутся")
        val typical = Pricing.typicalPurchase(before)
        val suspicious = chips >= Suspicion.ABSOLUTE_LIMIT ||
            (typical != null && chips > typical * Suspicion.AVERAGE_MULTIPLIER)
        assertEquals(suspicious, quote.warnings.any { it is DomainWarning.SuspiciousBuyIn }, "подозрительная сумма")
        sim.commitActive(change) {
            if (isBanker) sim.bankerOpsOf(before.game.id) += buy.operationId
            stacks(playerId, chips)
        }
    }

    // ---- Деньги ----

    fun pay() {
        val s = sim.activeSnapshot()
        val players = GameStates.compute(s).players.filterNot { it.isVoid }
        val target = (players.filter { it.debt > 0 }.takeIf { random.nextInt(10) != 0 } ?: players)
            .randomOrNull(random) ?: return
        val amount = if (target.debt > 0 && random.nextBoolean()) target.debt else random.nextLong(1, target.debt + 2)
        settle(s, target, amount, payout = false)
    }

    fun payOut() {
        val s = sim.activeSnapshot()
        val target = GameStates.compute(s).players.filter { it.owedByBanker > 0 }.randomOrNull(random) ?: return
        val amount = if (random.nextBoolean()) target.owedByBanker else random.nextLong(1, target.owedByBanker + 2)
        settle(s, target, amount, payout = true)
    }

    /** Оплата/выплата в игре `s` (активной или закрытой — погашение старого долга). */
    fun settle(
        s: GameSnapshot,
        target: PlayerState,
        amount: Long,
        payout: Boolean,
        bankerName: String = s.game.currentBankerName,
    ): Boolean {
        val limit = if (payout) target.owedByBanker else target.debt
        sim.note("${if (payout) "payOut" else "pay"} ${target.player.name} $amount of $limit by $bankerName")
        val expected = when {
            amount > limit && payout -> DomainError.PayoutExceedsOwed(limit)
            amount > limit -> DomainError.PaymentExceedsDebt(limit)
            else -> null
        }
        val stamp = sim.clock.stamp()
        val change = sim.attempt(expected) {
            if (payout) {
                Commands.payOut(s, target.player.id, amount, stamp, bankerName)
            } else {
                Commands.pay(s, target.player.id, amount, stamp, bankerName)
            }
        } ?: return false
        assertEquals(bankerName, change.entries.single().bankerName)
        val device = sim.device.also { assertTrue(s.game.id in it.games, "игра на этом телефоне") }
        val after = if (device == sim.device && s.game.status.isActive) {
            sim.commitActive(change)
        } else {
            sim.commit(change, device)
        }
        assertEquals(s.game, after.game, "оплата не меняет строку игры (без «изменено после закрытия»)")
        val now = GameStates.compute(after).player(target.player.id)!!
        assertEquals(target.outstanding + if (payout) -amount else amount, now.outstanding)
        sim.count(if (payout) "payOut" else "pay")
        return true
    }

    fun forgive(s: GameSnapshot = sim.activeSnapshot()) {
        val players = GameStates.compute(s).players.filterNot { it.isVoid }
        val debtor = players.filter { it.status == PlayerStatus.LEFT_WITH_DEBT }.randomOrNull(random)
        val target = debtor ?: players.randomOrNull(random) ?: return
        val amount = if (target.debt > 1 && random.nextBoolean()) random.nextLong(1, target.debt) else null
        sim.note("forgive ${target.player.name} ${amount ?: "all"} of ${target.debt}")
        val expected = if (target.status != PlayerStatus.LEFT_WITH_DEBT) DomainError.NoDebt(target.player.id) else null
        val change = sim.attempt(expected) {
            Commands.forgiveDebt(s, target.player.id, amount, sim.clock.stamp())
        } ?: return
        val before = GameStates.compute(s).table.bankerIncome
        val device = sim.device.also { assertTrue(s.game.id in it.games, "игра на этом телефоне") }
        val after = if (s.game.status.isActive) sim.commitActive(change) else sim.commit(change, device)
        val forgiven = amount ?: target.debt
        assertEquals(before - forgiven, GameStates.compute(after).table.bankerIncome, "прощение уменьшает доход")
        sim.count("forgive")
    }

    fun payRake() {
        val s = sim.activeSnapshot()
        val due = GameStates.compute(s).table.rakeDue
        sim.note("payRake $due")
        val change = sim.attempt(if (due <= 0) DomainError.NoRakeDue else null) {
            Commands.payRake(s, sim.clock.stamp())
        } ?: return
        val after = GameStates.compute(sim.commitActive(change)).table
        assertEquals(0L, after.rakeDue)
        sim.count("payRake")
    }

    // ---- Выход ----

    fun cashOut() {
        val s = sim.activeSnapshot()
        val state = GameStates.compute(s)
        val candidates = state.players.filter {
            it.status == PlayerStatus.PLAYING && !it.isVoid && (it.isSeated || sim.mode == Mode.CHAOS)
        }
        val player = candidates.randomOrNull(random) ?: return
        val chips = exitChips(player, state.table.chipsInPlay)
        cashOut(s, player, chips, tip(state, player, chips))
    }

    private fun exitChips(player: PlayerState, inPlay: Long): Long = when {
        sim.mode == Mode.PHYSICAL -> sim.stacks[player.player.id] ?: 0L
        random.nextInt(5) == 0 -> random.nextLong(0, inPlay.coerceAtLeast(0) + 2_000)
        else -> random.nextLong(0, inPlay.coerceAtLeast(0) + 1)
    }

    private fun tip(state: GameState, player: PlayerState, chips: Long): Tip? {
        if (chips == 0L) return null
        val others = state.players.filter { !it.isVoid && it.player.id != player.player.id }
        return when (random.nextInt(10)) {
            0, 1 -> CashOuts.roundingTip(chips, 100).takeIf { it > 0 }?.let { Tip(it, TipTarget.Banker) }

            2 -> CashOuts.roundingTip(chips, 500).takeIf { it > 0 }?.let { Tip(it, TipTarget.Banker) }

            3 -> Tip(random.nextLong(1, chips + 1), TipTarget.Banker)

            4, 5 -> others.filter { it.isSeated }.randomOrNull(random)
                ?.let { Tip(random.nextLong(1, minOf(chips, 300) + 1), TipTarget.ToPlayer(it.player.id)) }

            6 -> others.filter { it.status != PlayerStatus.PLAYING }.randomOrNull(random)
                ?.let { Tip(random.nextLong(1, minOf(chips, 300) + 1), TipTarget.ToPlayer(it.player.id)) }

            else -> null
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun cashOut(s: GameSnapshot, player: PlayerState, chips: Long, tip: Tip?, settlement: SettlementChoice? = null) {
        val id = player.player.id
        val quote = CashOuts.quote(s, id, chips, tip, sim.clock.now)
        val full = abs(quote.balance)
        val choice = settlement ?: when (random.nextInt(8)) {
            0, 1 -> SettlementChoice.None
            2 -> if (full >= 2) SettlementChoice.Partial(random.nextLong(1, full)) else SettlementChoice.Full
            else -> SettlementChoice.Full
        }
        val confirmed = !quote.isEarlyExit || random.nextInt(5) != 0
        sim.note("cashOut ${player.player.name} F=$chips tip=$tip $choice balance=${quote.balance}")
        val expected = quote.blockers.firstOrNull()
            ?: earlyBlocker(s.game.rules, quote, confirmed)
            ?: partialBlocker(choice, full)
        val change = sim.attempt(expected) {
            Commands.cashOut(s, id, chips, tip, choice, confirmed, sim.clock.stamp())
        } ?: return
        val inPlay = GameStates.compute(s).table.chipsInPlay
        assertEquals(chips > inPlay, quote.warnings.any { it is DomainWarning.ChipsExceedInPlay }, "F > в игре")
        val settled = when (choice) {
            SettlementChoice.Full -> full
            SettlementChoice.None -> 0L
            is SettlementChoice.Partial -> choice.amount
        }
        checkExitEntries(change, quote, chips, settled)
        val after = sim.commitActive(change) {
            sim.stacks.remove(id)
            if (quote.tipAsChips) {
                stacks((tip!!.to as TipTarget.ToPlayer).playerId, tip.amount)
            }
        }
        val now = GameStates.compute(after).player(id)!!
        val signed = if (quote.balance > 0) -settled else settled
        assertEquals(quote.balance + signed, now.outstanding, "остаток после расчёта")
        assertEquals(if (now.outstanding < 0) PlayerStatus.LEFT_WITH_DEBT else PlayerStatus.CASHED_OUT, now.status)
        assertEquals(quote.resultThisGame, now.result, "результат за игру")
        assertEquals(quote.balance, now.balanceAtExit, "баланс при выходе")
        if (quote.isEarlyExit) assertTrue(now.earlyExit, "пометка раннего выхода")
        sim.count(if (tip == null) "cashOut" else "cashOutTip")
        if (choice != SettlementChoice.Full) sim.count("settle${choice::class.simpleName}")
    }

    private fun checkExitEntries(change: GameChange, quote: CashOutQuote, chips: Long, settled: Long) {
        val exit = change.entries.first()
        assertEquals(EntryType.CASH_OUT, exit.type)
        assertEquals(chips, exit.amount)
        assertEquals(quote.isEarlyExit, exit.earlyExit)
        val tip = quote.tip
        if (tip != null) {
            val entry = change.entries[1]
            assertEquals(if (quote.tipAsChips) EntryType.TIP_CHIPS else EntryType.TIP, entry.type)
            assertEquals(tip.amount, entry.amount)
            assertEquals((tip.to as? TipTarget.ToPlayer)?.playerId, entry.counterpartyPlayerId)
        }
        val money = change.entries.filter { it.type == EntryType.PAYMENT || it.type == EntryType.PAYOUT }
        assertEquals(settled, money.sumOf { it.amount }, "сумма расчёта при выходе")
        if (money.isNotEmpty()) {
            assertEquals(if (quote.balance > 0) EntryType.PAYOUT else EntryType.PAYMENT, money.single().type)
            assertTrue(money.single().operationId != exit.operationId, "расчёт — отдельная операция")
        }
    }

    private fun earlyBlocker(rules: GameRules, quote: CashOutQuote, confirmed: Boolean): DomainError? =
        if (quote.isEarlyExit && !confirmed) {
            DomainError.EarlyExitNotConfirmed(quote.minutesPlayed ?: 0L, rules.minPlayMinutes)
        } else {
            null
        }

    private fun partialBlocker(choice: SettlementChoice, full: Long): DomainError? = when {
        choice !is SettlementChoice.Partial -> null
        full == 0L -> DomainError.NothingToSettle
        choice.amount !in 1 until full -> DomainError.PartialOutOfRange(full - 1)
        else -> null
    }

    // ---- Раздача (только модель фишек) ----

    @Suppress("ReturnCount")
    fun hand() {
        if (sim.mode != Mode.PHYSICAL) return
        val seated = GameStates.compute(sim.activeSnapshot()).players.filter { it.isSeated }
        if (seated.size < 2) return
        val (from, to) = seated.shuffled(random).take(2)
        val have = sim.stacks[from.player.id] ?: 0L
        if (have == 0L) return
        val moved = random.nextLong(1, have + 1)
        sim.note("hand ${from.player.name} → ${to.player.name} $moved")
        stacks(from.player.id, -moved)
        stacks(to.player.id, moved)
    }

    // ---- Отмена ----

    fun reverse() {
        if (sim.mode == Mode.PHYSICAL) {
            if (random.nextBoolean()) undoLast() else reverseMoneyOperation(sim.activeSnapshot())
        } else {
            val s = sim.activeSnapshot()
            reverse(s, s.entries.randomOrNull(random)?.operationId ?: return)
        }
    }

    /** «Отменить» последнего действия: все его операции в обратном порядке, фишки на руках — как до него. */
    private fun undoLast() {
        val point = sim.undo.removeLastOrNull() ?: return
        sim.note("undo ${point.operations.size} op(s)")
        point.operations.asReversed().forEach { op ->
            // Фишки возвращаются вместе с первой операцией действия (покупка или «Выход»); расчёт фишек не трогает.
            if (op == point.operations.first()) {
                sim.stacks.clear()
                sim.stacks.putAll(point.stacks)
            }
            val s = sim.activeSnapshot()
            assertEquals(point.gameId, s.game.id)
            val reversed = Ledger.reversedIds(s.entries)
            if (s.entries.filter { it.operationId == op }.all { it.id in reversed }) {
                sim.check(s, sim.device, codec = false)
                return@forEach
            }
            assertEquals(null, reversalBlocker(s, op), "«Отменить» последнего действия всегда можно")
            sim.commit(Commands.reverse(s, op, "отмена", sim.clock.stamp()))
            assertReversalIsStrikeOut(s, sim.activeSnapshot(), op)
        }
        sim.count("undo")
    }

    /** Отмена денежной операции (оплата, выплата, прощение, рейк) — фишек не касается. */
    fun reverseMoneyOperation(s: GameSnapshot) {
        val op = s.entries.groupBy { it.operationId }.filterValues { list -> list.all { it.type in MONEY } }
            .keys.randomOrNull(random) ?: return
        reverse(s, op)
    }

    fun reverse(s: GameSnapshot, op: OperationId) {
        val types = s.entries.filter { it.operationId == op }.map { it.type }.distinct()
        val expected = reversalBlocker(s, op)
        sim.note("reverse $types expected=$expected")
        val device = sim.device.also { assertTrue(s.game.id in it.games, "игра на этом телефоне") }
        val change = sim.attempt(expected) {
            Commands.reverse(s, op, if (random.nextBoolean()) "  ошибка ввода " else null, sim.clock.stamp())
        } ?: return
        assertTrue(change.entries.all { it.type == EntryType.REVERSAL })
        assertTrue(change.entries.all { it.note == null || it.note == "ошибка ввода" }, "причина без пробелов")
        val after = sim.commit(change, device)
        assertReversalIsStrikeOut(s, after, op)
        sim.count("reverse")
    }

    // ---- Помощники ----

    private fun stacks(id: PlayerId, delta: Long) {
        if (sim.mode != Mode.PHYSICAL) return
        sim.stacks[id] = (sim.stacks[id] ?: 0L) + delta
    }

    private fun amount(rules: GameRules): Long = when (random.nextInt(40)) {
        0 -> rules.minBuyIn
        1 -> Suspicion.ABSOLUTE_LIMIT
        2 -> BIG_AMOUNT
        else -> random.nextLong(rules.minBuyIn, rules.minBuyIn + 3_000)
    }

    private fun payment(rules: GameRules): BuyInPayment = when {
        rules.splitPayment || random.nextInt(20) == 0 -> BuyInPayment(random.nextBoolean(), random.nextBoolean())
        random.nextBoolean() -> BuyInPayment.PAID
        else -> BuyInPayment.DEBT
    }

    private fun splitBlocker(rules: GameRules, payment: BuyInPayment): DomainError? =
        if (!payment.isSingleStatus && !rules.splitPayment) DomainError.SplitPaymentDisabled else null

    private fun roundPercent(amount: Long, percent: Long): Long = (amount * percent + 50) / 100

    companion object {
        val NAMES = listOf("Андрей", "Борис", "Влад", "Дима", "Егор", "Женя", "Гоша", "Олег")
        const val BIG_AMOUNT = 10_000_000L
        val MONEY = setOf(EntryType.PAYMENT, EntryType.PAYOUT, EntryType.DEBT_FORGIVEN, EntryType.RAKE_PAID)

        init {
            check(BIG_AMOUNT < Money.MAX_AMOUNT)
        }
    }
}
