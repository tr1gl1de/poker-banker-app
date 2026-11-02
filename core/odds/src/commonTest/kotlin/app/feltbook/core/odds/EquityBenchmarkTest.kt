package app.feltbook.core.odds

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.test.runTest

/**
 * Бенчмарк-ориентир: 100 тыс. раздач на 6 игроков (цель на JVM ≈ 150 мс, на телефоне ≤ 1 с).
 * Время печатается; тест падает только при грубом замедлении (под Kover и на CI — с запасом).
 */
class EquityBenchmarkTest {
    private val settings = EquitySettings(samples = 100_000, seed = 5)

    private suspend fun measure(name: String, request: EquityRequest): Duration {
        EquityCalculator.calculate(request, settings.copy(samples = 20_000)) // прогрев JIT
        val mark = TimeSource.Monotonic.markNow()
        val result = EquityCalculator.calculate(request, settings)
        val elapsed = mark.elapsedNow()
        println("Бенчмарк $name: $elapsed, ${result.samples} раздач, exact=${result.exact}")
        return elapsed
    }

    @Test
    fun `100 тысяч раздач на 6 игроков`() = runTest {
        val limit = 5.seconds
        val hole = cards("Ah Kd")
        val timings = listOf(
            measure("префлоп", EquityRequest(hole, players = 6)),
            measure("флоп", EquityRequest(hole, cards("Qh 7h 2c"), players = 6)),
            measure("тёрн", EquityRequest(hole, cards("Qh 7h 2c 5d"), players = 6)),
            measure("ривер", EquityRequest(hole, cards("Qh 7h 2c 5d 9s"), players = 6)),
        )
        timings.forEach { assertTrue(it < limit, "Слишком медленно: $it") }
    }

    @Test
    fun `оценщик — миллион рук`() {
        val bits = Deck.cards.map { it.bit }.toLongArray()
        var checksum = 0L
        val mark = TimeSource.Monotonic.markNow()
        repeat(1_000_000) { index ->
            // Детерминированный обход разных масок (карты могут совпасть — тогда их меньше семи).
            val a = index % 46
            val mask = bits[a] or bits[a + 1] or bits[a + 2] or bits[a + 3] or bits[a + 4] or
                bits[(index / 46) % 52] or bits[(index * 7 + 3) % 52]
            checksum += HandEvaluator.evaluate(mask)
        }
        val elapsed = mark.elapsedNow()
        println("Бенчмарк оценщика: $elapsed на 1 млн рук (checksum $checksum)")
        assertTrue(elapsed < 5.seconds, "Слишком медленно: $elapsed")
    }
}
