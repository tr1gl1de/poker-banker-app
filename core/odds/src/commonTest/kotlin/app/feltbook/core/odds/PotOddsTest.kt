package app.feltbook.core.odds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PotOddsTest {
    @Test
    fun `нужная доля — ставка к банку со ставкой`() {
        // Макет: банк 1 200, ставка 300 → нужно 20,0 %.
        val odds = PotOdds(pot = 1_200, call = 300)
        assertEquals(0.2, odds.required, 1e-12)
        assertEquals(PotOddsVerdict.PAYS_OFF, odds.verdict(0.384))
        assertEquals(PotOddsVerdict.DOES_NOT_PAY, odds.verdict(0.19))
        assertEquals(PotOddsVerdict.DOES_NOT_PAY, odds.verdict(0.2))
    }

    @Test
    fun `пустой банк — нужно 100 процентов`() {
        assertEquals(1.0, PotOdds(pot = 0, call = 100).required)
        assertEquals(PotOddsVerdict.DOES_NOT_PAY, PotOdds(pot = 0, call = 100).verdict(1.0))
    }

    @Test
    fun `нет ставки — нет расчёта`() {
        assertNull(PotOdds.ofOrNull(pot = 1_000, call = 0))
        assertNull(PotOdds.ofOrNull(pot = -1, call = 100))
        assertEquals(PotOdds(500, 100), PotOdds.ofOrNull(pot = 500, call = 100))
        assertFailsWith<IllegalArgumentException> { PotOdds(pot = 100, call = 0) }
        assertFailsWith<IllegalArgumentException> { PotOdds(pot = -5, call = 10) }
    }
}
