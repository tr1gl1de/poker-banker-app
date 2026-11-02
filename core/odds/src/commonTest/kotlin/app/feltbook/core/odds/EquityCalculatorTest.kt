package app.feltbook.core.odds

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * Эталоны — независимый перебор/Монте-Карло на C с тем же алгоритмом оценки (3 млн раздач) и
 * общеизвестные значения: AA против одной случайной руки ≈ 85,2 %, против пяти ≈ 49,2 %.
 */
class EquityCalculatorTest {
    private val seeded = EquitySettings(samples = 200_000, seed = 42)

    private fun request(hole: String, board: String = "", players: Int = 2, dead: String = "") = EquityRequest(
        hole = cards(hole),
        board = cards(board),
        players = players,
        dead = cards(dead).toSet(),
    )

    private fun assertNear(expected: Double, actual: Double, tolerance: Double, what: String) {
        assertTrue(abs(expected - actual) <= tolerance, "$what: ожидали $expected ± $tolerance, получили $actual")
    }

    private fun assertSumsToOne(result: EquityResult) {
        assertNear(1.0, result.win + result.tie + result.lose, 1e-9, "win + tie + lose")
        assertTrue(result.equity >= result.win && result.equity <= result.win + result.tie)
    }

    @Test
    fun `AA против одной случайной руки ≈ 85,2 процента`() = runTest {
        val result = EquityCalculator.calculate(request("As Ad"), seeded)
        assertFalse(result.exact)
        assertTrue(result.isFinal)
        assertEquals(200_000L, result.samples)
        assertNear(0.8522, result.equity, 0.006, "эквити")
        assertNear(0.8495, result.win, 0.006, "победа")
        assertSumsToOne(result)
    }

    @Test
    fun `AA против пяти случайных рук ≈ 49,2 процента`() = runTest {
        val result = EquityCalculator.calculate(request("As Ad", players = 6), seeded)
        assertNear(0.4916, result.equity, 0.008, "эквити")
        assertNear(0.4894, result.win, 0.008, "победа")
        assertSumsToOne(result)
    }

    @Test
    fun `72o против пяти и KQs против двух`() = runTest {
        val weak = EquityCalculator.calculate(request("7c 2d", players = 6), seeded)
        assertNear(0.0859, weak.equity, 0.005, "72o эквити")
        assertNear(0.0222, weak.tie, 0.004, "72o ничья")
        val suited = EquityCalculator.calculate(request("Ks Qs", players = 3), seeded)
        assertNear(0.4712, suited.equity, 0.008, "KQs эквити")
    }

    @Test
    fun `тёрн хедз-ап — точный перебор`() = runTest {
        val req = request("Ah Kh", "Qh 7h 2c 5d")
        assertEquals(45_540L, EquityCalculator.combinations(req))
        val result = EquityCalculator.calculate(req)
        assertTrue(result.exact)
        assertTrue(result.isFinal)
        assertEquals(45_540L, result.samples)
        assertNear(0.587505, result.win, 1e-6, "победа")
        assertNear(0.006917, result.tie, 1e-6, "ничья")
        assertNear(0.590964, result.equity, 1e-6, "эквити")
        assertEquals(0.0, result.winMargin)
        assertEquals(0.0, result.equityMargin)
    }

    @Test
    fun `флоп хедз-ап — точный перебор совпадает с Монте-Карло`() = runTest {
        val req = request("Ah Kh", "Qh 7h 2c")
        assertEquals(1_070_190L, EquityCalculator.combinations(req))
        val exact = EquityCalculator.calculate(req)
        assertTrue(exact.exact)
        assertNear(0.723226, exact.win, 1e-6, "победа")
        assertNear(0.726830, exact.equity, 1e-6, "эквити")
        val sampled = EquityCalculator.calculate(req, seeded.copy(exactLimit = 0))
        assertFalse(sampled.exact)
        assertNear(exact.equity, sampled.equity, 3 * sampled.equityMargin, "Монте-Карло")
    }

    @Test
    fun `ривер хедз-ап`() = runTest {
        val result = EquityCalculator.calculate(request("Jd Jc", "Ts 9s 2d 2c Kh"))
        assertTrue(result.exact)
        assertEquals(990L, result.samples)
        assertNear(0.760606, result.win, 1e-6, "победа")
        assertNear(0.761111, result.equity, 1e-6, "эквити")
    }

    @Test
    fun `роял на столе — делёж на всех`() = runTest {
        val req = request("2c 3d", "Ah Kh Qh Jh Th", players = 3)
        assertEquals(893_970L, EquityCalculator.combinations(req))
        val result = EquityCalculator.calculate(req)
        assertTrue(result.exact)
        assertEquals(1.0, result.tie)
        assertEquals(0.0, result.win)
        assertNear(1.0 / 3, result.equity, 1e-9, "доля банка")
        assertNear(1.0 / 3, result.tieShare, 1e-9, "доля ничьей")
    }

    @Test
    fun `свой роял — победа всегда`() = runTest {
        val result = EquityCalculator.calculate(request("As Ks", "Qs Js Ts 2d 3c", players = 3))
        assertEquals(1.0, result.win)
        assertEquals(1.0, result.equity)
    }

    @Test
    fun `мёртвые карты убирают ауты`() = runTest {
        val open = EquityCalculator.calculate(request("Ah Kh", "Qh 7h 2c 5d"))
        val dead = EquityCalculator.calculate(request("Ah Kh", "Qh 7h 2c 5d", dead = "2h 3h 4h 6h 8h"))
        assertEquals(41L * 780, dead.samples)
        assertTrue(dead.equity < open.equity)
    }

    @Test
    fun `промежуточные результаты пачками`() = runTest {
        val settings = EquitySettings(samples = 25_000, batchSize = 10_000, seed = 7)
        val results = EquityCalculator.equity(request("As Kd", players = 6), settings).toList()
        assertEquals(listOf(10_000L, 20_000L, 25_000L), results.map { it.samples })
        assertEquals(listOf(false, false, true), results.map { it.isFinal })
        results.forEach(::assertSumsToOne)
        val last = results.last()
        assertNear(1.96 * sqrt(last.win * (1 - last.win) / 25_000), last.winMargin, 1e-12, "погрешность")
    }

    @Test
    fun `одинаковое зерно — одинаковый результат, без зерна — случайный`() = runTest {
        val settings = EquitySettings(samples = 20_000, seed = 1)
        val a = EquityCalculator.calculate(request("Qs Jh", "9c 8d 2s"), settings.copy(exactLimit = 0))
        val b = EquityCalculator.calculate(request("Qs Jh", "9c 8d 2s"), settings.copy(exactLimit = 0))
        assertEquals(a, b)
        val unseeded = EquityCalculator.calculate(request("Qs Jh", players = 4), EquitySettings(samples = 5_000))
        assertEquals(5_000L, unseeded.samples)
        assertSumsToOne(unseeded)
    }

    @Test
    fun `отмена останавливает Монте-Карло`() = runTest {
        val settings = EquitySettings(samples = 10_000_000, batchSize = 5_000, seed = 3)
        val first = EquityCalculator.equity(request("As Kd", players = 9), settings).take(2).toList()
        assertEquals(listOf(5_000L, 10_000L), first.map { it.samples })
    }

    @Test
    fun `отмена останавливает точный перебор`() = runTest {
        // Флоп на троих — ~966 млн вариантов: без отмены считали бы минуты.
        val req = request("Ah Kh", "Qh 7h 2c", players = 3)
        val job = launch(Dispatchers.Default) {
            EquityCalculator.equity(req, EquitySettings(exactLimit = Long.MAX_VALUE)).collect {
                error("Перебор не должен успеть закончиться")
            }
        }
        withContext(Dispatchers.Default) { delay(200) }
        val mark = TimeSource.Monotonic.markNow()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(mark.elapsedNow() < 2.seconds, "отмена заняла ${mark.elapsedNow()}")
    }

    @Test
    fun `число вариантов и порог перебора`() {
        assertEquals(990L, EquityCalculator.combinations(request("As Ad", "2c 3c 4c 5d 9h")))
        assertEquals(990L * 903, EquityCalculator.combinations(request("As Ad", "2c 3c 4c 5d 9h", players = 3)))
        assertEquals(2_118_760L * 990, EquityCalculator.combinations(request("As Ad")))
        assertEquals(Long.MAX_VALUE, EquityCalculator.combinations(request("As Ad", players = 10)))
    }

    @Test
    fun `проверка ввода`() {
        assertFailsWith<IllegalArgumentException> { request("As") }
        assertFailsWith<IllegalArgumentException> { request("As Ad", "2c 3c") }
        assertFailsWith<IllegalArgumentException> { request("As Ad", players = 1) }
        assertFailsWith<IllegalArgumentException> { request("As Ad", players = 11) }
        assertFailsWith<IllegalArgumentException> { request("As Ad", "As 3c 4c") }
        assertFailsWith<IllegalArgumentException> { request("As Ad", dead = "Ad") }
        assertFailsWith<IllegalArgumentException> { EquitySettings(samples = 0) }
        assertFailsWith<IllegalArgumentException> { EquitySettings(batchSize = 0) }
        // 10 игроков: 18 карт на руки + 5 на стол; 30 мёртвых — не хватает колоды.
        val dead = Deck.cards.drop(20).take(30).joinToString(" ")
        assertFailsWith<IllegalArgumentException> { request("2c 2d", players = 10, dead = dead) }
    }

    @Test
    fun `сочетания`() {
        assertEquals(1L, Combinatorics.choose(5, 0))
        assertEquals(0L, Combinatorics.choose(2, 3))
        assertEquals(0L, Combinatorics.choose(2, -1))
        assertEquals(2_598_960L, Combinatorics.choose(52, 5))
        assertEquals(133_784_560L, Combinatorics.choose(52, 7))
        assertEquals(Long.MAX_VALUE, Combinatorics.saturatingTimes(Long.MAX_VALUE / 2, 3))
        assertEquals(0L, Combinatorics.saturatingTimes(5, 0))
    }
}
