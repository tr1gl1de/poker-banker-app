package app.feltbook.core.domain.property

import app.feltbook.core.domain.T0
import app.feltbook.core.domain.TestClock
import app.feltbook.core.domain.TestIds
import app.feltbook.core.domain.engine.DomainError
import app.feltbook.core.domain.engine.DomainRuleViolation
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.engine.isSeated
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.apply
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

/**
 * Режим генератора.
 * [PHYSICAL] — модель фишек на руках: фишки ходят между игроками «раздачами», на выходе сдают ровно то, что на руках;
 *   отмена — «Отменить» последнего действия (snackbar) или денежной операции. Проверяет сверку фишек (§7.3) и игру с
 *   нулевой суммой (§7.4).
 * [CHAOS] — любые фишки на выходе (в т. ч. больше, чем в игре) и отмена любой операции журнала.
 */
internal enum class Mode { PHYSICAL, CHAOS }

/** Телефон банкира: все его игры, не больше одной активной. */
internal class Device(val label: String) {
    val games = LinkedHashMap<GameId, GameSnapshot>()

    fun active(): GameSnapshot? = games.values.firstOrNull { it.game.status.isActive }
}

/** Точка «Отменить»: операции одного действия и фишки на руках до него. */
internal class UndoPoint(val gameId: GameId, val operations: List<OperationId>, val stacks: Map<PlayerId, Long>)

/**
 * Одна случайная последовательность: два телефона (передача комнаты туда и обратно), история игр, погашение старых
 * долгов из новой игры. После каждого шага — инварианты §7 по затронутым играм; при падении — seed и трасса шагов.
 */
internal class Sim(val seed: Long, val mode: Mode, private val steps: Int) {
    val random = Random(seed)
    val clock = TestClock(T0, TestIds((seed % SEED_BLOCKS + SEED_BLOCKS) * IDS_PER_SIM))
    val devices = listOf(Device("A"), Device("B"))
    var current = 0
    val device: Device get() = devices[current]
    val trace = ArrayList<String>()
    val stacks = HashMap<PlayerId, Long>()
    val undo = ArrayList<UndoPoint>()
    val bankerOps = HashMap<GameId, MutableSet<OperationId>>()
    val counts = HashMap<String, Int>()
    var step = 0
        private set
    private val table = TableActions(this)
    private val lifecycle = Lifecycle(this, table)

    fun run(): Sim {
        try {
            repeat(steps) { i ->
                step = i
                clock.advance(random.nextInt(0, MAX_STEP_MINUTES + 1).minutes)
                val active = device.active()
                if (active == null) lifecycle.idle() else lifecycle.active(active)
            }
            lifecycle.finalCheck()
        } catch (failure: Throwable) {
            val report = "Property failed: seed=$seed mode=$mode step=$step\n" +
                "reproduce: Sim(${seed}L, Mode.$mode, $steps).run()\ntrace:\n" + trace.joinToString("\n")
            println(report)
            fail(report, failure)
        }
        return this
    }

    fun note(text: String) {
        trace += "#$step [${device.label}] $text"
    }

    fun count(kind: String) {
        counts[kind] = (counts[kind] ?: 0) + 1
    }

    fun activeSnapshot(): GameSnapshot = device.active() ?: error("no active game")

    /**
     * Ожидаемый исход команды: `expected == null` — должна пройти и вернуть изменение; иначе — бросить ровно эту
     * ошибку (журнал не меняется, команды чистые).
     */
    fun attempt(expected: DomainError?, command: () -> GameChange): GameChange? {
        if (expected == null) {
            return try {
                command()
            } catch (violation: DomainRuleViolation) {
                fail("команда должна пройти, а бросила ${violation.error}", violation)
            }
        }
        val violation = assertFailsWith<DomainRuleViolation>("ожидалась ошибка $expected") { command() }
        assertEquals(expected, violation.error)
        note("  ✗ ${violation.error::class.simpleName}")
        count("rejected")
        return null
    }

    /** Записать изменение активной игры: модель фишек обновляется до проверки, «Отменить» запоминает прежнюю. */
    fun commitActive(change: GameChange, model: () -> Unit = {}): GameSnapshot {
        val saved = HashMap(stacks)
        model()
        val after = commit(change)
        if (change.entries.isNotEmpty()) {
            undo += UndoPoint(change.gameId, change.entries.map { it.operationId }.distinct(), saved)
        }
        return after
    }

    /** `GameRepository.commit` в памяти + все инварианты по этой игре. */
    fun commit(change: GameChange, on: Device = device): GameSnapshot {
        val before = on.games[change.gameId]
        val after = before?.apply(change)
            ?: GameSnapshot(change.game!!, change.players, change.entries, change.handovers)
        if (change.entries.isNotEmpty()) {
            // Двойная отправка того же изменения: seq уже занят — второй раз не запишется.
            assertFailsWith<IllegalArgumentException>("повторное изменение не применяется") { after.apply(change) }
        }
        before?.let { Invariants.appendOnly(it, after) }
        on.games[change.gameId] = after
        check(after, on)
        return after
    }

    fun check(snapshot: GameSnapshot, on: Device, codec: Boolean = true) {
        Invariants.valid(snapshot)
        Invariants.money(snapshot)
        Invariants.reversedExcluded(snapshot)
        Invariants.bankerFees(snapshot, bankerOps[snapshot.game.id].orEmpty())
        Invariants.gameRow(snapshot)
        if (codec) Invariants.codec(snapshot, on.active()?.game?.id, clock.now)
        assertTrue(on.games.values.count { it.game.status.isActive } <= 1, "одна активная игра на телефоне")
        if (mode == Mode.PHYSICAL && snapshot.game.id == on.active()?.game?.id) physical(snapshot)
    }

    /** Модель фишек на руках: сверка фишек (§7.3), нет отрицательных фишек, нулевая сумма (§7.4). */
    private fun physical(snapshot: GameSnapshot) {
        val state = GameStates.compute(snapshot)
        val seated = state.players.filter { it.isSeated }
        val seatedIds = seated.map { it.player.id }.toSet()
        seated.forEach { assertTrue((stacks[it.player.id] ?: 0L) >= 0, "фишки на руках ≥ 0") }
        assertEquals(seated.sumOf { stacks[it.player.id] ?: 0L }, state.table.chipsInPlay, "фишки на руках = в игре")
        assertTrue(state.table.chipsInPlay >= 0, "фишек в игре не меньше нуля")
        state.players.filter { it.player.id !in seatedIds }.forEach {
            assertEquals(0L, stacks[it.player.id] ?: 0L, "у не сидящего ${it.player.name} нет фишек")
        }
        if (seated.isEmpty()) {
            assertEquals(0L, state.table.chipsInPlay, "§7.3 все вышли — фишки сошлись")
            val live = state.players.filterNot { it.isVoid }
            assertEquals(-live.sumOf { it.tipsToBanker }, live.sumOf { it.result ?: 0L }, "§7.4 нулевая сумма")
        }
    }

    fun bankerOpsOf(gameId: GameId): MutableSet<OperationId> = bankerOps.getOrPut(gameId) { HashSet() }

    companion object {
        const val SEED_BLOCKS = 1_000_000L
        const val IDS_PER_SIM = 1_000_000L
        const val MAX_STEP_MINUTES = 25
    }
}
