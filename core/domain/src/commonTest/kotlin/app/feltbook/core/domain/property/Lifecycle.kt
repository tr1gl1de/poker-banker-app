package app.feltbook.core.domain.property

import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.CashOuts
import app.feltbook.core.domain.engine.CloseConfirmation
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.Debts
import app.feltbook.core.domain.engine.DomainError
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.engine.Handovers
import app.feltbook.core.domain.engine.ImportPlan
import app.feltbook.core.domain.engine.Imports
import app.feltbook.core.domain.engine.Journal
import app.feltbook.core.domain.engine.JournalItem
import app.feltbook.core.domain.engine.Money
import app.feltbook.core.domain.engine.NewGameInput
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.engine.SettlementChoice
import app.feltbook.core.domain.engine.Settlements
import app.feltbook.core.domain.engine.Stats
import app.feltbook.core.domain.engine.Tip
import app.feltbook.core.domain.engine.TipTarget
import app.feltbook.core.domain.engine.isSeated
import app.feltbook.core.domain.engine.reversalBlocker
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Жизненный цикл: новая игра, завершение, закрытие, переоткрытие, передача комнаты, погашение старых долгов. */
@Suppress("TooManyFunctions")
internal class Lifecycle(private val sim: Sim, private val table: TableActions) {
    private val random get() = sim.random

    /** Нет активной игры: новая игра или работа с историей (погашение, прощение, отмена, переоткрытие). */
    fun idle() {
        val closed = sim.device.games.values.filter { it.game.status == GameStatus.CLOSED }
        val pick = random.nextInt(12)
        when {
            closed.isEmpty() || pick < 6 -> startGame()
            pick < 8 -> repayOldDebt(games(), games().last().game.currentBankerName)
            pick == 8 -> table.forgive(closed.random(random))
            pick < 11 -> reverseInClosed(closed.random(random))
            else -> reopen(closed.random(random))
        }
    }

    private fun games(): List<GameSnapshot> = sim.device.games.values.toList()

    /** Шаг в активной игре RUNNING или SETTLING. */
    @Suppress("CyclomaticComplexMethod")
    fun active(s: GameSnapshot) {
        if (sim.step % JOURNAL_EVERY == 0) journal(s)
        val running = s.game.status == GameStatus.RUNNING
        val seated = GameStates.compute(s).players.count { it.isSeated }
        when (val pick = random.nextInt(100)) {
            in 0..13 -> if (running) table.addPlayer(debtor(s), ::offerRepayment) else table.cashOut()

            in 14..25 -> if (running) table.buyIn() else table.cashOut()

            in 26..34 -> table.hand()

            in 35..41 -> table.pay()

            in 42..45 -> table.payOut()

            in 46..61 -> table.cashOut()

            in 62..70 -> table.reverse()

            in 71..73 -> table.forgive()

            in 74..76 -> table.payRake()

            in 77..80 -> probe(s)

            in 81..82 -> rename(s)

            in 83..85 -> handOver(s)

            else -> if (running) {
                val exited = GameStates.compute(s).players.any { it.status != PlayerStatus.PLAYING }
                if (pick < 88 || (seated == 0 && exited)) finish(s) else table.cashOut()
            } else {
                if (pick < 89) resume(s) else close(s)
            }
        }
    }

    // ---- Игры ----

    private fun startGame() {
        val rules = randomRules()
        val banker = BANKERS.random(random)
        val plays = random.nextBoolean()
        sim.note("start banker=$banker plays=$plays $rules")
        val input = NewGameInput(if (random.nextBoolean()) "" else "Пятница", "2 ноя", banker, plays, rules)
        val problems = rules.problems()
        val expected = if (problems.isNotEmpty()) DomainError.InvalidRules(problems) else null
        val change = sim.attempt(expected) { Commands.startGame(input, null, sim.clock.stamp()) } ?: return
        val game = change.game!!
        assertEquals(GameStatus.RUNNING, game.status)
        assertEquals(rules, game.rules, "правила — слепок")
        assertEquals(plays, game.bankerPlayerId != null)
        sim.commit(change)
        sim.undo.clear()
        sim.count("start")
    }

    private fun randomRules(): GameRules {
        val mode = BankerFeeMode.entries.random(random)
        val rakeMode = RakeMode.entries.random(random)
        return GameRules(
            bankerFeeMode = mode,
            bankerFeeValue = if (random.nextInt(8) == 0) 0 else random.nextLong(1, if (mode.isPercent) 30 else 300),
            rakeMode = rakeMode,
            rakeValue = when (random.nextInt(30)) {
                0 -> 0
                1 -> if (rakeMode == RakeMode.PERCENT) 101 else -1
                else -> random.nextLong(1, if (rakeMode == RakeMode.PERCENT) 25 else 300)
            },
            minBuyIn = listOf(500L, 500L, 100L, 1L, 1_000L).random(random),
            splitPayment = random.nextInt(3) == 0,
            minPlayMinutes = listOf(60, 60, 30, 0).random(random),
        )
    }

    private fun finish(s: GameSnapshot) {
        sim.note("finish")
        sim.commit(sim.attempt(null) { Commands.finish(s) }!!)
        sim.count("finish")
    }

    private fun resume(s: GameSnapshot) {
        sim.note("resume")
        sim.commit(sim.attempt(null) { Commands.resume(s) }!!)
        sim.count("resume")
    }

    private fun close(s: GameSnapshot) {
        val settlement = Settlements.compute(s)
        val required = settlement.requiredConfirmations
        val ack = if (required.isNotEmpty() && random.nextInt(4) == 0) required - required.random(random) else required
        val payRake = random.nextBoolean()
        sim.note("close payRake=$payRake required=$required ack=$ack")
        val missing = required - ack
        val expected = settlement.blockers.firstOrNull()
            ?: if (missing.isNotEmpty()) DomainError.NotConfirmed(missing) else null
        val stamp = sim.clock.stamp()
        val change = sim.attempt(expected) { Commands.close(s, payRake, ack, stamp) } ?: return
        val after = sim.commit(change)
        assertEquals(GameStatus.CLOSED, after.game.status)
        assertEquals(stamp.at, after.game.closedAt)
        val t = GameStates.compute(after).table
        if (payRake) assertEquals(0L, t.rakeDue.coerceAtLeast(0), "рейк отдан при закрытии")
        sim.undo.clear()
        sim.count("close")
        if (CloseConfirmation.CHIPS_NOT_RECONCILED in required) sim.count("closeChipDiff")
        if (CloseConfirmation.OPEN_DEBTS in required) sim.count("closeWithDebt")
    }

    private fun reopen(s: GameSnapshot) {
        val active = sim.device.active()
        sim.note("reopen ${s.game.title} active=${active?.game?.id}")
        val expected = active?.let { DomainError.AnotherGameActive(it.game.id) }
        val change = sim.attempt(expected) { Commands.reopen(s, active?.game) } ?: return
        val after = sim.commit(change)
        assertEquals(GameStatus.RUNNING, after.game.status)
        assertTrue(after.game.editedAfterClose, "изменено после закрытия")
        sim.count("reopen")
    }

    private fun reverseInClosed(s: GameSnapshot) {
        if (random.nextBoolean()) {
            table.reverseMoneyOperation(s)
        } else {
            table.reverse(s, s.entries.randomOrNull(random)?.operationId ?: return)
        }
    }

    // ---- Долги прошлых игр ----

    /** При посадке за новый стол: «Влад должен N ₽ с …» → «Погасить сейчас» (запись в старую игру). */
    private fun offerRepayment(active: GameSnapshot, name: String) {
        val games = sim.device.games.values.toList()
        val debts = Debts.forName(name, games, emptyList(), excludeGameId = active.game.id)
        debts.forEach { debt ->
            val game = sim.device.games.getValue(debt.gameId)
            assertTrue(game.game.status != GameStatus.HANDED_OVER, "долги переданной игры ушли новому банкиру")
            assertFalse(game.game.status.isActive)
            assertEquals(PlayerNames.normalize(name), debt.canonicalName)
            assertEquals(GameStates.compute(game).player(debt.playerId)!!.debt, debt.amount)
        }
        if (debts.isNotEmpty() && random.nextInt(5) != 0) {
            repayOldDebt(debts.map { sim.device.games.getValue(it.gameId) }, active.game.currentBankerName)
            sim.count("repayFromNewGame")
        }
    }

    /** Должник прошлой игры садится за новый стол (каждый второй раз, если такие есть). */
    private fun debtor(active: GameSnapshot): String? = if (random.nextBoolean()) {
        Debts.open(sim.device.games.values.toList(), emptyList())
            .filter { it.gameId != active.game.id }
            .randomOrNull(random)?.playerName
    } else {
        null
    }

    private fun repayOldDebt(games: List<GameSnapshot>, bankerName: String) {
        val debts = Debts.open(games, emptyList())
        val debt = debts.randomOrNull(random) ?: return
        val game = sim.device.games.getValue(debt.gameId)
        val player = GameStates.compute(game).player(debt.playerId)!!
        val amount = if (random.nextBoolean()) debt.amount else random.nextLong(1, debt.amount + 1)
        if (table.settle(game, player, amount, payout = false, bankerName = bankerName)) {
            val after = sim.device.games.getValue(debt.gameId)
            assertEquals(game.game.status, after.game.status, "погашение не меняет статус старой игры")
            sim.count("repayOld")
        }
    }

    // ---- Передача комнаты ----

    /** Сценарий 1 ТЗ §6: посреди игры на другой телефон; повторный приём — пустое изменение. */
    private fun handOver(s: GameSnapshot) {
        val target = sim.devices[1 - sim.current]
        val to = BANKERS.random(random)
        val keep = random.nextBoolean()
        val before = GameStates.compute(s)
        val act = Handovers.act(s)
        val prepared = Handovers.prepare(s, to, keep, sim.clock.stamp())
        val handover = prepared.handover
        sim.note("handover → ${target.label} to=$to keep=$keep act=$act")
        assertEquals(act, prepared.act)
        assertEquals(if (keep) act.incomeTakeable else 0L, handover.incomeTaken)
        assertTrue(handover.incomeTaken <= maxOf(0L, act.fromBankerIncome), "§7.7 забрано ≤ дохода банкира")
        assertTrue(act.incomeTakeable <= maxOf(0L, act.cashOnHand - act.rakeDue), "рейк заведения не в карман")
        assertEquals(act.cashToHandOver(keep), handover.cashHandedOver)
        assertEquals(before.table.cashOnHand, act.cashOnHand)
        assertEquals(prepared.outgoing.entries.lastOrNull()?.seq ?: 0L, handover.ledgerSeq)
        Invariants.valid(prepared.outgoing)
        val local = target.games[s.game.id]
        val targetActive = target.active()
        val receiver = receiverFor(s, to)
        val expected = targetActive?.takeIf { it.game.id != s.game.id }
            ?.let { DomainError.AnotherGameActive(it.game.id) }
        val change = sim.attempt(expected) {
            Handovers.accept(prepared.outgoing, handover, local, targetActive?.game, receiver)
        } ?: return
        if (local != null) assertIs<ImportPlan.Extend>(Imports.plan(prepared.outgoing, local, null))
        sim.commit(prepared.senderChange)
        val received = sim.commit(change, target)
        assertEquals(prepared.outgoing.entries, received.entries)
        assertEquals(prepared.outgoing.handovers, received.handovers)
        assertEquals(PlayerNames.normalize(to), received.game.currentBankerName)
        assertEquals(receiver, received.game.bankerPlayerId)
        assertEquals(s.game.status, received.game.status)
        val t = GameStates.compute(received).table
        assertEquals(before.table.cashOnHand - handover.incomeTaken, t.cashOnHand, "касса нового банкира")
        assertEquals(before.table.chipsInPlay, t.chipsInPlay)
        assertEquals(
            GameChange(s.game.id, baseSeq = received.lastSeq),
            Handovers.accept(prepared.outgoing, handover, received, received.game, receiver),
            "повторный приём того же пакета — пустое изменение",
        )
        sim.current = 1 - sim.current
        sim.undo.clear()
        sim.count(if (local != null) "handoverBack" else "handover")
    }

    private fun receiverFor(s: GameSnapshot, to: String): PlayerId? {
        val named = s.players.firstOrNull { it.name == PlayerNames.normalize(to) }
        return when {
            named != null && random.nextInt(3) != 0 -> named.id
            random.nextInt(5) == 0 -> s.players.randomOrNull(random)?.id
            else -> null
        }
    }

    // ---- Недопустимый ввод ----

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun probe(s: GameSnapshot) {
        val state = GameStates.compute(s)
        val player = state.players.filter { it.isSeated }.randomOrNull(random)
        val min = s.game.rules.minBuyIn
        val stamp = sim.clock.stamp()
        when (random.nextInt(9)) {
            0 -> if (min > 1 && s.game.status == GameStatus.RUNNING) {
                sim.note("probe add below min ${min - 1}")
                sim.attempt(DomainError.BelowMinBuyIn(min)) {
                    Commands.addPlayer(s, "Новичок", min - 1, BuyInPayment.DEBT, stamp)
                }
            }

            1 -> if (s.game.status == GameStatus.RUNNING) {
                sim.note("probe add above MAX")
                sim.attempt(DomainError.AmountTooLarge) {
                    Commands.addPlayer(s, "Новичок", Money.MAX_AMOUNT + 1, BuyInPayment.DEBT, stamp)
                }
                sim.attempt(DomainError.AmountNotPositive) {
                    Commands.addPlayer(s, "Новичок", 0, BuyInPayment.DEBT, stamp)
                }
            }

            2 -> player?.let {
                sim.note("probe tip > F")
                sim.attempt(DomainError.TipExceedsChips(10)) {
                    Commands.cashOut(s, it.player.id, 10, Tip(11, TipTarget.Banker), SettlementChoice.Full, true, stamp)
                }
                sim.attempt(DomainError.TipToSelf) {
                    val tip = Tip(1, TipTarget.ToPlayer(it.player.id))
                    Commands.cashOut(s, it.player.id, 10, tip, SettlementChoice.Full, true, stamp)
                }
            }

            3 -> player?.let {
                sim.note("probe F out of range")
                sim.attempt(DomainError.AmountNotPositive) {
                    Commands.cashOut(s, it.player.id, -1, null, SettlementChoice.Full, true, stamp)
                }
                sim.attempt(DomainError.AmountTooLarge) {
                    Commands.cashOut(s, it.player.id, Money.MAX_AMOUNT + 1, null, SettlementChoice.Full, true, stamp)
                }
            }

            4 -> player?.let {
                val chips = sim.stacks[it.player.id] ?: 0L
                val balance = CashOuts.quote(s, it.player.id, chips, null, stamp.at).balance
                val full = kotlin.math.abs(balance)
                sim.note("probe partial 0 balance=$balance")
                val expected = if (full == 0L) DomainError.NothingToSettle else DomainError.PartialOutOfRange(full - 1)
                sim.attempt(expected) {
                    Commands.cashOut(s, it.player.id, chips, null, SettlementChoice.Partial(0), true, stamp)
                }
            }

            5 -> {
                sim.note("probe pay 0 / unknown")
                state.players.firstOrNull { !it.isVoid }?.let {
                    sim.attempt(DomainError.AmountNotPositive) { Commands.pay(s, it.player.id, 0, stamp) }
                }
                val ghost = PlayerId(Uuid.fromLongs(-1, sim.seed))
                sim.attempt(DomainError.PlayerNotFound(ghost)) { Commands.pay(s, ghost, 1, stamp) }
                sim.attempt(DomainError.PlayerNotFound(ghost)) { Commands.forgiveDebt(s, ghost, null, stamp) }
            }

            6 -> {
                sim.note("probe second active game")
                val input = NewGameInput("", "2 ноя", "Пётр", false, GameRules.DEFAULT)
                sim.attempt(DomainError.AnotherGameActive(s.game.id)) { Commands.startGame(input, s.game, stamp) }
                sim.device.games.values.firstOrNull { it.game.status == GameStatus.CLOSED }?.let { closed ->
                    sim.attempt(DomainError.AnotherGameActive(s.game.id)) { Commands.reopen(closed, s.game) }
                }
            }

            7 -> if (s.game.status == GameStatus.SETTLING) {
                sim.note("probe buy in SETTLING")
                state.players.firstOrNull { !it.isVoid }?.let {
                    val allowed = setOf(GameStatus.RUNNING)
                    sim.attempt(DomainError.WrongGameStatus(GameStatus.SETTLING, allowed)) {
                        Commands.buyIn(s, it.player.id, min, BuyInPayment.DEBT, stamp)
                    }
                }
            } else {
                sim.note("probe close RUNNING")
                sim.attempt(DomainError.WrongGameStatus(GameStatus.RUNNING, setOf(GameStatus.SETTLING))) {
                    Commands.close(s, true, CloseConfirmation.entries.toSet(), stamp)
                }
            }

            else -> {
                val buyer = state.players.firstOrNull { !it.isVoid && it.buyInCount > 0 }
                if (buyer != null && s.game.status == GameStatus.RUNNING) {
                    sim.note("probe rebuy above MAX")
                    sim.attempt(DomainError.AmountTooLarge) {
                        Commands.buyIn(s, buyer.player.id, Money.MAX_AMOUNT + 1, BuyInPayment.DEBT, stamp)
                    }
                }
            }
        }
        assertEquals(s, sim.device.games.getValue(s.game.id), "недопустимый ввод не меняет игру")
        sim.count("probe")
    }

    private fun rename(s: GameSnapshot) {
        val player = s.players.randomOrNull(random) ?: return
        val name = if (random.nextInt(10) == 0) "   " else TableActions.NAMES.random(random)
        val normalized = PlayerNames.normalize(name)
        val other = s.players.firstOrNull { it.id != player.id && PlayerNames.normalize(it.name) == normalized }
        val expected = when {
            normalized.isEmpty() -> DomainError.NameBlank
            other != null -> DomainError.NameTaken(other.id)
            else -> null
        }
        sim.note("rename ${player.name} → '$name'")
        val change = sim.attempt(expected) { Commands.renamePlayer(s, player.id, name) } ?: return
        assertTrue(change.entries.isEmpty(), "переименование не пишет журнал")
        val after = sim.commit(change)
        assertEquals(normalized, after.player(player.id)!!.name)
        sim.count("rename")
    }

    // ---- Журнал, история, резервная копия ----

    private fun journal(s: GameSnapshot) {
        val items = Journal.items(s)
        val operations = items.filterIsInstance<JournalItem.Operation>()
        val expected = s.entries.groupBy { it.operationId }
            .filterValues { op -> op.none { it.type == EntryType.REVERSAL } }
        assertEquals(expected.keys, operations.map { it.operationId }.toSet(), "каждая операция — одна строка")
        assertEquals(operations.size, expected.size)
        assertEquals(s.handovers.size, items.count { it is JournalItem.HandoverItem })
        operations.forEach { op ->
            assertEquals(reversalBlocker(s, op.operationId) == null, op.reversible)
        }
        assertEquals(items, items.sortedByDescending { it.at }, "новые сверху")
    }

    /** В конце: все игры обоих телефонов, резервная копия каждого телефона, история, долги, статистика. */
    fun finalCheck() {
        sim.note("final")
        sim.devices.forEach { device ->
            val games = device.games.values.toList()
            games.forEach { sim.check(it, device, codec = false) }
            games.forEach { journal(it) }
            backup(device, games)
            val debts = Debts.open(games, emptyList())
            val expected = games.filter { it.game.status != GameStatus.HANDED_OVER }.sumOf { game ->
                GameStates.compute(game).players
                    .filter { !it.isVoid && it.status == PlayerStatus.LEFT_WITH_DEBT }
                    .sumOf { it.debt }
            }
            assertEquals(expected, debts.sumOf { it.amount }, "открытые долги")
            val period = Stats.period(games, emptyList(), null, null)
            val finished = games.filter { it.game.status in FINISHED }
            assertEquals(finished.size, period.gameCount)
            assertEquals(finished.sumOf { GameStates.compute(it).table.rakeAccrued }, period.rake)
            assertEquals(finished.sumOf { GameStates.compute(it).table.bankerIncome }, period.bankerIncome)
            games.forEach { Stats.historyItem(it) }
        }
    }

    private fun backup(device: Device, games: List<GameSnapshot>) {
        val at = sim.clock.now
        val text = FeltbookCodec.encode(FeltbookPackage.BackupPackage(at, games, emptyList()), at)
        val decoded = assertIs<FeltbookPackage.BackupPackage>(assertIs<DecodeResult.Ok>(FeltbookCodec.decode(text)).pkg)
        assertEquals(games, decoded.games, "резервная копия телефона ${device.label} без потерь")
        val activeId = device.active()?.game?.id
        decoded.games.forEach { incoming ->
            assertEquals(ImportPlan.Identical, Imports.plan(incoming, device.games[incoming.game.id], activeId))
        }
    }

    private companion object {
        val BANKERS = listOf("Олег", "Пётр", "Влад", "Андрей")
        const val JOURNAL_EVERY = 7
        val FINISHED = setOf(GameStatus.CLOSED, GameStatus.HANDED_OVER)
    }
}
