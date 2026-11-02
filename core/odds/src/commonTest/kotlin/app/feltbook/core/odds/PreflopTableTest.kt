package app.feltbook.core.odds

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PreflopTableTest {
    @Test
    fun `169 классов, 1326 раздач`() {
        val ranking = PreflopTable.ranking
        assertEquals(HandClass.COUNT, ranking.size)
        assertEquals(HandClass.COUNT, ranking.toSet().size)
        assertEquals(HandClass.TOTAL_COMBOS, ranking.sumOf { it.combos })
        assertEquals(13, ranking.count { it.isPair })
        assertEquals(78, ranking.count { it.suited })
    }

    @Test
    fun `таблица по убыванию эквити`() {
        val equities = PreflopTable.ranking.map { PreflopTable.strength(it).equityVsRandom }
        assertEquals(equities.sortedDescending(), equities)
        assertEquals((1..169).toList(), PreflopTable.ranking.map { PreflopTable.strength(it).rank })
    }

    @Test
    fun `известные руки`() {
        val aces = PreflopTable.strength(cards("As Ad"))
        assertEquals(1, aces.rank)
        assertEquals(0.852, aces.equityVsRandom)
        assertEquals(6 * 100.0 / 1326, aces.topPercent, 1e-9)
        assertEquals(2, PreflopTable.strength(HandClass.parse("KK")).rank)
        assertEquals(12, PreflopTable.strength(cards("Ah Kd")).rank)
        assertEquals(16, PreflopTable.strength(cards("Kc Qc")).rank)
        val worst = PreflopTable.strength(cards("3d 2c"))
        assertEquals(169, worst.rank)
        assertEquals(100.0, worst.topPercent, 1e-9)
        assertTrue(PreflopTable.strength(cards("7c 2d")).topPercent > 96)
    }

    /** Таблица согласована с калькулятором: хедз-ап Монте-Карло 100 тыс. раздач. */
    @Test
    fun `таблица совпадает с калькулятором`() = runTest {
        for (hole in listOf("As Ad", "Kc Qc", "2s 2h", "7c 2d", "Th 9h")) {
            val stored = PreflopTable.strength(cards(hole)).equityVsRandom
            val computed = EquityCalculator.calculate(
                EquityRequest(cards(hole)),
                EquitySettings(samples = 100_000, seed = 11),
            ).equity
            assertTrue(abs(stored - computed) < 0.01, "$hole: в таблице $stored, посчитано $computed")
        }
    }

    @Test
    fun `класс по картам`() {
        assertEquals("AKs", HandClass.of(Card.parse("Kh"), Card.parse("Ah")).label)
        assertEquals("AKo", HandClass.of(Card.parse("Ad"), Card.parse("Kh")).label)
        assertEquals("TT", HandClass.of(Card.parse("Td"), Card.parse("Th")).label)
        assertEquals("T9o", HandClass.parse("T9o").toString())
        assertEquals(6, HandClass.parse("22").combos)
        assertEquals(4, HandClass.parse("AKs").combos)
        assertEquals(12, HandClass.parse("AKo").combos)
        assertFalse(HandClass.parse("AA").suited)
    }

    @Test
    fun `неверные классы`() {
        assertNull(HandClass.parseOrNull("AK"))
        assertNull(HandClass.parseOrNull("AAs"))
        assertNull(HandClass.parseOrNull("KAs"))
        assertNull(HandClass.parseOrNull("AKx"))
        assertNull(HandClass.parseOrNull("A"))
        assertNull(HandClass.parseOrNull("XKs"))
        assertNull(HandClass.parseOrNull("AKso"))
        assertFailsWith<IllegalArgumentException> { HandClass.parse("AK") }
        assertFailsWith<IllegalArgumentException> { HandClass(Rank.TWO, Rank.ACE, suited = true) }
        assertFailsWith<IllegalArgumentException> { HandClass(Rank.ACE, Rank.ACE, suited = true) }
        assertFailsWith<IllegalArgumentException> { HandClass.of(Card.parse("As"), Card.parse("As")) }
        assertFailsWith<IllegalArgumentException> { PreflopTable.strength(cards("As")) }
    }
}
