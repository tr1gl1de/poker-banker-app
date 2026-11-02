package app.feltbook.core.domain.engine

import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.RakeMode
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
    private val names = listOf("Андрей", "Борис", "Влад", "Дима", "Егор")

    @Test
    fun `случайные игры — журнал корректен, касса сходится, отмена равна вычёркиванию, пакет без потерь`() {
        val random = Random(20261102)
        val entries = (1..SEQUENCES).sumOf { run(random) }
        assertTrue(entries > SEQUENCES * STEPS / 2, "генератор действительно пишет записи: $entries")
    }

    private fun run(random: Random): Int {
        val rules = GameRules(
            bankerFeeMode = BankerFeeMode.entries.random(random),
            bankerFeeValue = random.nextLong(0, 20),
            rakeMode = RakeMode.entries.random(random),
            rakeValue = random.nextLong(0, 20),
            splitPayment = random.nextBoolean(),
        )
        val table = TestTable(rules = rules, banker = "Андрей", bankerPlays = random.nextBoolean())
        repeat(STEPS) {
            val before = table.snapshot
            table.later(random.nextInt(0, 30))
            try {
                step(table, random)
            } catch (_: DomainRuleViolation) {
                // Недопустимое случайное действие: журнал не меняется.
            }
            val after = table.snapshot
            assertEquals(before.entries, after.entries.take(before.entries.size), "журнал только дописывается")
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

            else -> table.apply(Commands.payRake(table.snapshot, table.clock.stamp()))
        }
    }

    private fun cashOut(table: TestTable, player: PlayerState, state: GameState, random: Random) {
        val chips = random.nextLong(0, maxOf(1, state.table.chipsInPlay + 1))
        val recipients = state.players.filter { it.player.id != player.player.id && !it.isVoid }
        val tip = when {
            chips == 0L || random.nextInt(3) == 0 -> null
            random.nextBoolean() || recipients.isEmpty() -> Tip(random.nextLong(1, chips + 1), TipTarget.Banker)
            else -> Tip(random.nextLong(1, chips + 1), TipTarget.ToPlayer(recipients.random(random).player.id))
        }
        val settlement = when (random.nextInt(3)) {
            0 -> SettlementChoice.Full
            1 -> SettlementChoice.None
            else -> SettlementChoice.Partial(1)
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
