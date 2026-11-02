package app.feltbook.core.odds

import app.feltbook.core.odds.HandCategory.FLUSH
import app.feltbook.core.odds.HandCategory.HIGH_CARD
import app.feltbook.core.odds.HandCategory.ONE_PAIR
import app.feltbook.core.odds.HandCategory.STRAIGHT
import app.feltbook.core.odds.HandCategory.THREE_OF_A_KIND
import app.feltbook.core.odds.HandCategory.TWO_PAIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutsCalculatorTest {
    private fun outs(hole: String, board: String, dead: String = "") =
        OutsCalculator.outs(cards(hole), cards(board), cards(dead).toSet())

    @Test
    fun `флеш-дро и оверкарты на флопе`() {
        val result = outs("Ah Kh", "Qh 7h 2c")
        assertEquals(HIGH_CARD, result.current)
        assertEquals(listOf(FLUSH, ONE_PAIR), result.outs.keys.toList())
        assertEquals(9, result.outs.getValue(FLUSH).size)
        assertTrue(result.outs.getValue(FLUSH).all { it.suit == Suit.HEARTS })
        assertEquals(cards("Ad As Kc Kd Ks Ac").toSet(), result.outs.getValue(ONE_PAIR).toSet())
        assertEquals(FLUSH, result.strongest)
        assertEquals(15, result.total)
        assertEquals(47, result.unseen)
        assertEquals(2, result.streetsLeft)
        assertEquals(15.0 / 47, result.nextCard)
        // 1 − C(32, 2) / C(47, 2) = 1 − 496 / 1081.
        assertEquals(1.0 - 496.0 / 1081.0, result.byRiver)
        assertEquals(30, result.ruleOfTwo)
        assertEquals(60, result.ruleOfFour)
    }

    @Test
    fun `двусторонний стрит-дро`() {
        val result = outs("9c 8d", "7s 6h 2c")
        assertEquals(8, result.outs.getValue(STRAIGHT).size)
        assertEquals(setOf(Rank.TEN, Rank.FIVE), result.outs.getValue(STRAIGHT).map { it.rank }.toSet())
        assertEquals(STRAIGHT, result.strongest)
    }

    @Test
    fun `тёрн — одна улица, без правила 4`() {
        val result = outs("Ah Kh", "Qh 7h 2c 5d")
        assertEquals(1, result.streetsLeft)
        assertEquals(46, result.unseen)
        assertEquals(9, result.outs.getValue(FLUSH).size)
        assertNull(result.byRiver)
        assertNull(result.ruleOfFour)
        assertEquals(result.total * 2, result.ruleOfTwo)
    }

    @Test
    fun `карта, которая усиливает только стол, — не аут`() {
        val result = outs("Ac Kd", "7s 7h 2c")
        assertEquals(ONE_PAIR, result.current)
        assertEquals(listOf(TWO_PAIR), result.outs.keys.toList())
        assertEquals(cards("As Ah Ad Ks Kh Kc").toSet(), result.outs.getValue(TWO_PAIR).toSet())
    }

    @Test
    fun `стрит на столе — не аут, сет — аут`() {
        val result = outs("Ac Ad", "9s 8d 7c 6h")
        assertEquals(setOf(THREE_OF_A_KIND), result.outs.keys)
        assertEquals(2, result.total)
    }

    @Test
    fun `карманная пара под столом — ауты только на сет`() {
        val result = outs("8c 8d", "As Kh 2c")
        assertEquals(mapOf(THREE_OF_A_KIND to cards("8h 8s")), result.outs)
    }

    @Test
    fun `мёртвые карты не считаются аутами`() {
        val result = outs("Ah Kh", "Qh 7h 2c", dead = "3h 4h")
        assertEquals(7, result.outs.getValue(FLUSH).size)
        assertEquals(45, result.unseen)
    }

    @Test
    fun `без аутов`() {
        val result = outs("As Ks", "Qs Js Ts 2d")
        assertEquals(0, result.total)
        assertNull(result.strongest)
        assertEquals(0.0, result.nextCard)
        assertEquals(0, result.ruleOfTwo)
    }

    @Test
    fun `правила 2 и 4 не больше 100 процентов`() {
        val result = OutsResult(HIGH_CARD, mapOf(ONE_PAIR to Deck.cards.take(30)), unseen = 47, streetsLeft = 2)
        assertEquals(60, result.ruleOfTwo)
        assertEquals(100, result.ruleOfFour)
    }

    @Test
    fun `проверка ввода`() {
        assertFailsWith<IllegalArgumentException> { outs("Ah", "Qh 7h 2c") }
        assertFailsWith<IllegalArgumentException> { outs("Ah Kh", "") }
        assertFailsWith<IllegalArgumentException> { outs("Ah Kh", "Qh 7h 2c 5d 9s") }
        assertFailsWith<IllegalArgumentException> { outs("Ah Kh", "Ah 7h 2c") }
    }
}
