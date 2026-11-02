package app.feltbook.core.domain.property

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property-тесты инвариантов §7 (docs/plan/m1-domain.md, M1.9): случайные последовательности по [STEPS] шагов по всем
 * командам — закуп, докуп, оплата и частичная оплата, выход с чаевыми банкиру / игроку за столом / вышедшему, расчёт
 * Full/Partial/None, возврат вышедшего, отмена любой операции, прощение, рейк, завершение, закрытие, переоткрытие,
 * передача комнаты посреди игры (туда и обратно), погашение старого долга из новой игры, недопустимый ввод.
 *
 * Seed фиксирован: последовательность `k` класса — `Random(BASE + k)`. При падении в отчёте seed, шаг и трасса;
 * воспроизвести — `Sim(seed, mode, STEPS).run()`.
 */
internal fun runSequences(base: Long, count: Int, mode: Mode): Map<String, Int> {
    val counts = HashMap<String, Int>()
    var entries = 0L
    for (k in 0 until count) {
        val sim = Sim(base + k, mode, STEPS).run()
        sim.counts.forEach { (kind, n) -> counts[kind] = (counts[kind] ?: 0) + n }
        entries += sim.devices.sumOf { d -> d.games.values.sumOf { it.entries.size.toLong() } }
    }
    assertTrue(entries > count * STEPS / 2, "генератор действительно пишет журнал: $entries записей")
    return counts
}

/** Генератор покрывает каждую команду, а не только «удобные». */
internal fun assertCovered(counts: Map<String, Int>, kinds: List<String>, min: Int) {
    val missing = kinds.filter { (counts[it] ?: 0) < min }
    assertTrue(missing.isEmpty(), "редко встречаются $missing (минимум $min): $counts")
}

internal const val STEPS = 50
internal const val SEQUENCES_PER_CLASS = 500

private val COMMON = listOf(
    "start", "add", "rebuy", "return", "pay", "payOut", "cashOut", "cashOutTip", "settleNone", "settlePartial",
    "forgive", "payRake", "finish", "resume", "close", "closeWithDebt", "reopen", "handover", "handoverBack",
    "repayOld", "repayFromNewGame", "rename", "probe", "rejected",
)

/** Модель фишек на руках: сверка фишек и нулевая сумма при честной сдаче фишек; «Отменить» последнего действия. */
class PhysicalPropertyTest {
    @Test
    fun `500 игр по 50 шагов с фишками на руках — сверка фишек, касса, сбор, пакет без потерь`() {
        val counts = runSequences(base = 20_261_102_000L, count = SEQUENCES_PER_CLASS, mode = Mode.PHYSICAL)
        assertCovered(counts, COMMON + "undo" + "reverse", min = 20)
    }
}

/** Любые фишки на выходе и отмена любой операции журнала. */
class ChaosPropertyTest {
    @Test
    fun `500 игр по 50 шагов с любыми фишками и отменами — журнал корректен, отмена равна вычёркиванию`() {
        val counts = runSequences(base = 20_261_103_000L, count = SEQUENCES_PER_CLASS, mode = Mode.CHAOS)
        assertCovered(counts, COMMON + "reverse" + "closeChipDiff", min = 20)
    }
}
