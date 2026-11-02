package app.feltbook

import org.junit.Assert.assertEquals
import org.junit.Test

class EasterEggCounterTest {
    private val counter = EasterEggCounter()

    private fun tapSeries(count: Int, start: Long = 0L, step: Long = 500L): List<EasterEggTap> =
        (0 until count).map { counter.tap(start + it * step, alreadyUnlocked = false) }

    @Test
    fun hintsFromSeventhTapAndUnlockOnEleventh() {
        val results = tapSeries(11)
        assertEquals(List(6) { EasterEggTap.Silent }, results.take(6))
        assertEquals(
            listOf(EasterEggTap.Hint(4), EasterEggTap.Hint(3), EasterEggTap.Hint(2), EasterEggTap.Hint(1)),
            results.subList(6, 10),
        )
        assertEquals(EasterEggTap.Unlocked, results.last())
    }

    @Test
    fun pauseLongerThanOneSecondRestartsCount() {
        tapSeries(6) // последнее — на 2 500 мс
        // Пауза 1 001 мс: счёт заново, 7-е подряд нажатие — снова первая подсказка.
        val restarted = tapSeries(7, start = 3_501L)
        assertEquals(List(6) { EasterEggTap.Silent }, restarted.take(6))
        assertEquals(EasterEggTap.Hint(4), restarted.last())
    }

    @Test
    fun exactlyOneSecondStillCounts() {
        counter.tap(0L, alreadyUnlocked = false)
        repeat(5) { counter.tap((it + 1) * 1_000L, alreadyUnlocked = false) }
        assertEquals(EasterEggTap.Hint(4), counter.tap(6_000L, alreadyUnlocked = false))
    }

    @Test
    fun noHintsWhenAlreadyUnlocked() {
        val results = (0 until 11).map { counter.tap(it * 100L, alreadyUnlocked = true) }
        assertEquals(List(11) { EasterEggTap.Silent }, results)
    }
}
