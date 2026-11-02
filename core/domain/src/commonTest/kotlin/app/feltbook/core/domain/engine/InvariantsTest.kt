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
import app.feltbook.core.domain.tableFrom
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Быстрая проверка инвариантов §7 на случайных последовательностях команд (фиксированный seed).
 * Полный набор property-тестов — задача M1.9 (qa-engineer).
 */
class InvariantsTest {
    private var handovers = 0
    private var closes = 0
    private val names = listOf("Андрей", "Борис", "Влад", "Дима", "Егор")

    @Test
    fun `случайные игры — журнал корректен, касса сходится, отмена равна вычёркиванию, пакет без потерь`() {
        val random = Random(20261102)
        val entries = (1..SEQUENCES).sumOf { run(random) }
        assertTrue(entries > SEQUENCES * STEPS / 2, "генератор действительно пишет записи: $entries")
        assertTrue(handovers > SEQUENCES / 4 && closes > SEQUENCES / 10, "передачи $handovers, закрытия $closes")
    }

    private fun run(random: Random): Int {
        val rules = GameRules(
            bankerFeeMode = BankerFeeMode.entries.random(random),
            bankerFeeValue = random.nextLong(0, 20),
            rakeMode = RakeMode.entries.random(random),
            rakeValue = random.nextLong(0, 20),
            splitPayment = random.nextBoolean(),
        )
        var table = TestTable(rules = rules, banker = "Андрей", bankerPlays = random.nextBoolean())
        repeat(STEPS) {
            val before = table.snapshot
            table.later(random.nextInt(0, 30))
            try {
                if (random.nextInt(25) == 0) table = handOver(table, random) else step(table, random)
            } catch (_: DomainRuleViolation) {
                // Недопустимое случайное действие: журнал не меняется.
            }
            val after = table.snapshot
            if (after.game.id == before.game.id && after.handovers.size == before.handovers.size) {
                assertEquals(before.entries, after.entries.take(before.entries.size), "журнал только дописывается")
            }
            assertTrue(LedgerValidator.validate(after).isEmpty(), "валидатор: ${LedgerValidator.validate(after)}")
            assertCashIdentity(table.state.table)
        }
        val text = FeltbookCodec.encode(
            FeltbookPackage.BackupPackage(table.clock.now, listOf(table.snapshot), emptyList()),
            table.clock.now,
        )
        val decoded = FeltbookCodec.decode(text) as DecodeResult.Ok
        assertEquals(listOf(table.snapshot), (decoded.pkg as FeltbookPackage.BackupPackage).games)
        zeroSum(table)
        return table.snapshot.entries.size
    }

    @Suppress("CyclomaticComplexMethod")
    private fun step(table: TestTable, random: Random) {
        val state = table.state
        val seated = state.players.filter { it.status == PlayerStatus.PLAYING && !it.isVoid }
        val left = state.players.filter { it.status != PlayerStatus.PLAYING && !it.isVoid }
        val payment = BuyInPayment(random.nextBoolean(), random.nextBoolean())
        when (random.nextInt(10)) {
            0, 1 -> table.add(names.random(random), random.nextLong(500, 3_000), payment)

            2 -> (seated + left).randomOrNull(random)?.let {
                table.buy(it.player.name, random.nextLong(500, 2_000), payment)
            }

            3 -> state.players.filter { it.debt > 0 }.randomOrNull(random)?.let {
                table.pay(
                    it.player.name,
                    random.nextLong(
                        1,
                        it.debt + 1,
                    ),
                )
            }

            4, 5 -> seated.randomOrNull(random)?.let { cashOut(table, it, state, random) }

            6 -> table.snapshot.entries.randomOrNull(random)?.let { reverse(table, it.operationId) }

            7 -> state.players.filter {
                it.owedByBanker > 0
            }.randomOrNull(random)?.let { table.payOut(it.player.name, it.owedByBanker) }

            8 -> state.players.filter { it.status == PlayerStatus.LEFT_WITH_DEBT }.randomOrNull(random)?.let {
                table.apply(Commands.forgiveDebt(table.snapshot, it.player.id, null, table.clock.stamp()))
            }

            else -> if (random.nextBoolean()) {
                lifecycle(
                    table,
                    random,
                )
            } else {
                table.apply(Commands.payRake(table.snapshot, table.clock.stamp()))
            }
        }
    }

    /** Завершить / вернуть / закрыть / погасить после закрытия / переоткрыть. */
    private fun lifecycle(table: TestTable, random: Random) {
        val snapshot = table.snapshot
        when (snapshot.game.status) {
            GameStatus.RUNNING -> table.apply(Commands.finish(snapshot))

            GameStatus.SETTLING -> if (random.nextInt(3) == 0) {
                table.apply(Commands.resume(snapshot))
            } else {
                table.apply(
                    Commands.close(
                        snapshot,
                        random.nextBoolean(),
                        CloseConfirmation.entries.toSet(),
                        table.clock.stamp(),
                    ),
                )
                closes++
            }

            GameStatus.CLOSED -> {
                val debtor = table.state.players.firstOrNull { it.debt > 0 }
                if (debtor != null && random.nextBoolean()) {
                    table.apply(
                        Commands.pay(
                            snapshot,
                            debtor.player.id,
                            random.nextLong(1, debtor.debt + 1),
                            table.clock.stamp(),
                            "Пётр",
                        ),
                    )
                } else {
                    table.apply(Commands.reopen(snapshot, null))
                }
            }

            else -> Unit
        }
    }

    /** Передача посреди игры: отправитель коммитит senderChange, игра продолжается у получателя. */
    private fun handOver(table: TestTable, random: Random): TestTable {
        val to = listOf("Влад", "Пётр", "Олег").random(random)
        val prepared = Handovers.prepare(table.snapshot, to, random.nextBoolean(), table.clock.stamp())
        val sender = table.snapshot.apply(prepared.senderChange)
        assertTrue(LedgerValidator.validate(sender).isEmpty(), "отправитель: ${LedgerValidator.validate(sender)}")
        assertEquals(prepared.outgoing.entries, sender.entries)
        val seat = table.state.players.filter { !it.isVoid }.randomOrNull(random)?.takeIf { random.nextBoolean() }
        val change = Handovers.accept(prepared.outgoing, prepared.handover, null, null, seat?.player?.id)
        val received = GameSnapshot(change.game!!, change.players, change.entries, change.handovers)
        assertTrue(prepared.handover.incomeTaken <= maxOf(0L, prepared.act.fromBankerIncome), "§7.7: забрано ≤ дохода")
        assertTrue(LedgerValidator.validate(received).isEmpty())
        handovers++
        return tableFrom(received)
    }

    private fun cashOut(table: TestTable, player: PlayerState, state: GameState, random: Random) {
        val chips = random.nextLong(0, maxOf(1, state.table.chipsInPlay + 1))
        val recipients = state.players.filter { it.player.id != player.player.id && !it.isVoid }
        val tip = when {
            chips == 0L || random.nextInt(3) == 0 -> null
            random.nextBoolean() || recipients.isEmpty() -> Tip(random.nextLong(1, chips + 1), TipTarget.Banker)
            else -> Tip(random.nextLong(1, chips + 1), TipTarget.ToPlayer(recipients.random(random).player.id))
        }
        val balance = kotlin.math.abs(
            CashOuts.quote(table.snapshot, player.player.id, chips, tip, table.clock.now).balance,
        )
        val settlement = when {
            random.nextInt(3) == 0 -> SettlementChoice.None
            balance > 1 && random.nextBoolean() -> SettlementChoice.Partial(random.nextLong(1, balance))
            else -> SettlementChoice.Full
        }
        table.apply(
            Commands.cashOut(table.snapshot, player.player.id, chips, tip, settlement, true, table.clock.stamp()),
        )
    }

    private fun reverse(table: TestTable, operation: app.feltbook.core.domain.model.OperationId) {
        val before = table.snapshot
        if (reversalBlocker(before, operation) != null) return
        table.reverse(operation)
        // Сравнение с вычёркиванием корректно, только если вычеркнутый журнал сам допустим (нет отмен этой операции).
        val touched = before.entries.any {
            it.type == EntryType.REVERSAL &&
                before.entries.any { t -> t.id == it.reversesEntryId && t.operationId == operation }
        }
        if (!touched) assertReversalIsStrikeOut(before, table.snapshot, operation)
    }

    /** §7.4: когда все вышли и фишки сошлись, Σ результатов = −чаевые банкиру. */
    private fun zeroSum(table: TestTable) {
        val state = table.state
        val everyoneLeft = state.players.none { it.isSeated }
        if (!everyoneLeft || state.table.chipsInPlay != 0L || state.game.status == GameStatus.HANDED_OVER) return
        val players = state.players.filterNot { it.isVoid }
        assertEquals(-players.sumOf { it.tipsToBanker }, players.sumOf { it.result ?: 0L })
    }

    private companion object {
        const val SEQUENCES = 300
        const val STEPS = 40
    }
}
