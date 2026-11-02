package app.feltbook.core.odds

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Воспроизводит [PreflopTable] из репозитория: тот же метод, что в KDoc таблицы.
 * Хедз-ап против одной случайной руки, 3 000 000 раздач на класс, xorshift128+ с фиксированным
 * состоянием, общий для всех классов в порядке: старшая карта A→2, младшая от старшей вниз,
 * сначала разномастная, потом одномастная. Долго (десятки секунд), поэтому только при
 * `FELTBOOK_REGEN_PREFLOP=1`; печатает строки таблицы и сверяет значения с хранимыми.
 */
class PreflopTableRegeneratorTest {
    private var s0 = 0x9E3779B97F4A7C15uL
    private var s1 = 0xD1B54A32D192ED03uL

    private fun next(): ULong {
        var a = s0
        val b = s1
        s0 = b
        a = a xor (a shl 23)
        s1 = a xor b xor (a shr 17) xor (b shr 26)
        return s1 + b
    }

    /** Классы в порядке генерации (см. KDoc). */
    private fun classesInOrder(): List<HandClass> = Rank.entries.reversed().flatMap { high ->
        Rank.entries.filter { it <= high }.reversed().flatMap { low ->
            if (high ==
                low
            ) {
                listOf(HandClass(high, low, false))
            } else {
                listOf(false, true).map { HandClass(high, low, it) }
            }
        }
    }

    private fun permyriad(handClass: HandClass, samples: Int): Int {
        val bits = Deck.cards.map { it.bit }
        val first = handClass.high.ordinal * 4
        val second = handClass.low.ordinal * 4 + if (handClass.suited) 0 else 1
        val deck = (0 until 52).filter { it != first && it != second }.map { bits[it] }.toLongArray()
        val hero = bits[first] or bits[second]
        var equity = 0.0
        repeat(samples) {
            for (k in 0 until 7) {
                val j = k + (next() % (deck.size - k).toULong()).toInt()
                val card = deck[k]
                deck[k] = deck[j]
                deck[j] = card
            }
            val board = deck[0] or deck[1] or deck[2] or deck[3] or deck[4]
            val h = HandEvaluator.evaluate(hero or board)
            val o = HandEvaluator.evaluate(board or deck[5] or deck[6])
            equity += if (h > o) {
                1.0
            } else if (h == o) {
                0.5
            } else {
                0.0
            }
        }
        return (equity / samples * 10_000 + 0.5).toInt()
    }

    @Test
    fun `пересчёт таблицы префлопа`() {
        if (System.getenv("FELTBOOK_REGEN_PREFLOP") != "1") return
        val computed = classesInOrder().map { it to permyriad(it, 3_000_000) }
        computed.sortedByDescending { it.second }.chunked(10).forEach { row ->
            println("        \"" + row.joinToString(" ") { "${it.first.label}:${it.second}" } + "\",")
        }
        for ((handClass, value) in computed) {
            val stored = (PreflopTable.strength(handClass).equityVsRandom * 10_000 + 0.5).toInt()
            assertEquals(stored, value, "Класс $handClass")
        }
    }
}
