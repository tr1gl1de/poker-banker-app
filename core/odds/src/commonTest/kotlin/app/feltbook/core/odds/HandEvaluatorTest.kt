package app.feltbook.core.odds

import app.feltbook.core.odds.HandCategory.FLUSH
import app.feltbook.core.odds.HandCategory.FOUR_OF_A_KIND
import app.feltbook.core.odds.HandCategory.FULL_HOUSE
import app.feltbook.core.odds.HandCategory.HIGH_CARD
import app.feltbook.core.odds.HandCategory.ONE_PAIR
import app.feltbook.core.odds.HandCategory.STRAIGHT
import app.feltbook.core.odds.HandCategory.STRAIGHT_FLUSH
import app.feltbook.core.odds.HandCategory.THREE_OF_A_KIND
import app.feltbook.core.odds.HandCategory.TWO_PAIR
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandEvaluatorTest {
    private fun assertStronger(stronger: String, weaker: String) {
        assertTrue(rankOf(stronger) > rankOf(weaker), "$stronger должна быть сильнее $weaker")
    }

    private fun assertSplit(a: String, b: String) {
        assertEquals(rankOf(a), rankOf(b), "$a и $b должны делить банк")
    }

    @Test
    fun `категории известных рук`() {
        assertEquals(HIGH_CARD, categoryOf("As Kd 9c 7h 2s"))
        assertEquals(ONE_PAIR, categoryOf("As Ad 9c 7h 2s"))
        assertEquals(TWO_PAIR, categoryOf("As Ad 9c 9h 2s"))
        assertEquals(THREE_OF_A_KIND, categoryOf("As Ad Ac 7h 2s"))
        assertEquals(STRAIGHT, categoryOf("9s 8d 7c 6h 5s"))
        assertEquals(STRAIGHT, categoryOf("As 2d 3c 4h 5s"))
        assertEquals(FLUSH, categoryOf("As Js 9s 7s 2s"))
        assertEquals(FULL_HOUSE, categoryOf("As Ad Ac 7h 7s"))
        assertEquals(FOUR_OF_A_KIND, categoryOf("As Ad Ac Ah 2s"))
        assertEquals(STRAIGHT_FLUSH, categoryOf("9s 8s 7s 6s 5s"))
        assertEquals(STRAIGHT_FLUSH, categoryOf("As 2s 3s 4s 5s"))
    }

    @Test
    fun `роял-флеш — сильнейшая рука`() {
        val royal = rankOf("Ah Kh Qh Jh Th")
        assertTrue(HandEvaluator.isRoyalFlush(royal))
        assertEquals(HandEvaluator.royalFlushRank, royal)
        assertFalse(HandEvaluator.isRoyalFlush(rankOf("Kh Qh Jh Th 9h")))
        assertTrue(HandEvaluator.isRoyalFlush(rankOf("Ah Kh Qh Jh Th 9h 2c")))
    }

    @Test
    fun `порядок категорий`() {
        assertStronger("2s 3s 4s 5s 6s", "As Ad Ac Ah Ks")
        assertStronger("2s 2d 2c 2h 3s", "As Ad Ac Kh Ks")
        assertStronger("2s 2d 2c 3h 3s", "As Ks Qs Js 9s")
        assertStronger("2s 4s 5s 6s 7s", "As Kd Qc Jh Ts")
        assertStronger("As 2d 3c 4h 5s", "As Ad Ac Kh Qs")
        assertStronger("2s 2d 2c 3h 4s", "As Ad Kc Kh Qs")
        assertStronger("2s 2d 3c 3h 4s", "As Ad Kc Qh Js")
        assertStronger("2s 2d 3c 4h 5s", "As Kd Qc Jh 9s")
    }

    @Test
    fun `колесо A-5 — младший стрит`() {
        assertStronger("2d 3c 4h 5s 6d", "As 2d 3c 4h 5s")
        assertStronger("As 2s 3s 4s 6s", "As 2d 3c 4h 5s")
        assertStronger("6s 2s 3s 4s 5s", "As 2s 3s 4s 5s")
        assertSplit("As 2d 3c 4h 5s 6d Kc", "2d 3c 4h 5s 6d")
        assertEquals(HIGH_CARD, categoryOf("Ks As 2d 3c 4h"))
    }

    @Test
    fun `кикеры решают`() {
        assertStronger("As Ad Kc 7d 2h", "As Ad Qc Jd Th")
        assertStronger("As Ad Kc 7d 3h", "As Ad Kc 7d 2h")
        assertStronger("Ks Kd 2c 2d Ah", "Qs Qd Jc Jd Ah")
        assertStronger("Ks Kd 2c 2d Ah", "Ks Kd 2c 2d Qh")
        assertStronger("Ks Kd 3c 3d 2h", "Ks Kd 2c 2d Ah")
        assertStronger("9s 9d 9c 9h Ad", "9s 9d 9c 9h Kd")
        assertStronger("Ts Td Tc 2h 2d", "9s 9d 9c Ah Ad")
        assertStronger("Ts Td Tc 3h 3d", "Ts Td Tc 2h 2d")
        assertStronger("7s 7d 7c Ah 2d", "7s 7d 7c Kh Qd")
        assertStronger("As Ks 9s 7s 3s", "As Ks 9s 7s 2s")
        assertStronger("Ah Kd Qc Jh 9s", "Ah Kd Qc Jh 8s")
        assertSplit("Ah Kd Qc Jh 9s", "Ad Kc Qh Js 9d")
    }

    @Test
    fun `семь карт — лучшие пять`() {
        // Три пары: играют две старшие + кикер.
        assertSplit("As Ad Ks Kd Qs Qd 2c", "As Ad Ks Kd Qc 3h 4h")
        assertStronger("As Ad Ks Kd Qs Qd 2c", "As Ad Ks Kd Jc 3h 4h")
        // Два сета — фулл-хаус со старшим сетом.
        assertSplit("As Ad Ac Ks Kd Kc 2h", "As Ad Ac Ks Kd 3c 2h")
        assertEquals(FULL_HOUSE, categoryOf("2s 2d 2c 3s 3d 3c 4h"))
        // Сет + две пары — фулл-хаус со старшей парой.
        assertSplit("7s 7d 7c Ks Kd 2s 2d", "7s 7d 7c Ks Kd 4h 3h")
        // Каре + пара: кикер — старшая из остальных.
        assertSplit("9s 9d 9c 9h Ks Kd Ad", "9s 9d 9c 9h As 2d 3d")
        // Шесть карт масти — старшие пять.
        assertSplit("Ah Kh 9h 7h 4h 2h 3c", "Ah Kh 9h 7h 4h 3d 2c")
        // Стрит из шести подряд — старший.
        assertSplit("As Kd Qc Jh Ts 9d 8c", "As Kd Qc Jh Ts 2d 3c")
        // Стрит-флеш сильнее каре на тех же семи картах.
        assertEquals(STRAIGHT_FLUSH, categoryOf("9s 8s 7s 6s 5s 9d 9c"))
        // Флеш сильнее стрита на тех же семи картах.
        assertEquals(FLUSH, categoryOf("9s 8d 7s 6s 5c 2s As"))
    }

    @Test
    fun `меньше пяти карт`() {
        assertEquals(ONE_PAIR, categoryOf("As Ad"))
        assertEquals(HIGH_CARD, categoryOf("As"))
        assertEquals(TWO_PAIR, categoryOf("As Ad Ks Kd"))
        assertEquals(FOUR_OF_A_KIND, categoryOf("As Ad Ac Ah"))
        assertStronger("As Ad", "Ks Kd")
    }

    @Test
    fun `неверный ввод`() {
        assertFailsWith<IllegalArgumentException> { HandEvaluator.rank(emptyList()) }
        assertFailsWith<IllegalArgumentException> { rankOf("As Ks Qs Js Ts 9s 8s 7s") }
        assertFailsWith<IllegalArgumentException> { rankOf("As As Kd Qc 2h") }
    }

    @Test
    fun `6 и 7 карт = лучшая из пятёрок`() {
        val random = Random(20261102)
        repeat(20_000) { index ->
            val size = if (index % 2 == 0) 6 else 7
            val hand = Deck.cards.shuffled(random).take(size)
            val best = fiveCardSubsets(hand).maxOf { HandEvaluator.rank(it) }
            assertEquals(best, HandEvaluator.rank(hand), "Рука $hand")
        }
    }

    private fun fiveCardSubsets(hand: List<Card>): List<List<Card>> {
        val result = mutableListOf<List<Card>>()
        fun pick(start: Int, chosen: List<Card>) {
            if (chosen.size == 5) {
                result += chosen
            } else {
                for (i in start until hand.size) pick(i + 1, chosen + hand[i])
            }
        }
        pick(0, emptyList())
        return result
    }

    @Test
    fun `полный перебор 5 карт по категориям`() {
        val counts = enumerateAll(5)
        assertEquals(
            listOf(1_302_540L, 1_098_240L, 123_552L, 54_912L, 10_200L, 5_108L, 3_744L, 624L, 40L),
            counts.byCategory.toList(),
        )
        assertEquals(4L, counts.royal)
        assertEquals(2_598_960L, counts.byCategory.sum())
    }

    /**
     * Эталон: все 133 784 560 семикарточных рук (Wikipedia, «Poker probability», 7-card hands).
     * Перебор инкрементальный (маска растёт по уровням), на JVM — секунды.
     */
    @Test
    fun `полный перебор 7 карт по категориям`() {
        val counts = enumerateAll(7)
        assertEquals(
            listOf(
                23_294_460L,
                58_627_800L,
                31_433_400L,
                6_461_620L,
                6_180_020L,
                4_047_644L,
                3_473_184L,
                224_848L,
                41_584L,
            ),
            counts.byCategory.toList(),
        )
        assertEquals(4_324L, counts.royal)
        assertEquals(133_784_560L, counts.byCategory.sum())
    }

    private class Counts {
        val byCategory = LongArray(HandCategory.entries.size)
        var royal = 0L
    }

    private fun enumerateAll(size: Int): Counts {
        val counts = Counts()
        val bits = Deck.cards.map { it.bit }.toLongArray()

        fun level(start: Int, left: Int, mask: Long) {
            if (left == 1) {
                for (i in start until bits.size) {
                    val rank = HandEvaluator.evaluate(mask or bits[i])
                    counts.byCategory[rank ushr 26]++
                    if (rank == HandEvaluator.royalFlushRank) counts.royal++
                }
            } else {
                for (i in start..bits.size - left) level(i + 1, left - 1, mask or bits[i])
            }
        }
        level(0, size, 0L)
        return counts
    }
}
