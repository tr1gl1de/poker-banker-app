package app.feltbook.core.odds

/** Девять стандартных категорий комбинаций по возрастанию силы. Роял-флеш — старший [STRAIGHT_FLUSH]. */
enum class HandCategory {
    HIGH_CARD,
    ONE_PAIR,
    TWO_PAIR,
    THREE_OF_A_KIND,
    STRAIGHT,
    FLUSH,
    FULL_HOUSE,
    FOUR_OF_A_KIND,
    STRAIGHT_FLUSH,
}

/**
 * Оценщик лучшей пятикарточной комбинации из 1–7 карт (для эквити — 5, 6 или 7).
 * Собственная реализация, вдохновлённая идеями OMPEval (код и таблицы OMPEval не переносились).
 *
 * Рука — 64-битная маска: четыре блока по 16 бит (масти), в блоке — 13 бит рангов. Масти и
 * совпадения рангов находятся битовыми операциями над четырьмя масками мастей, а стриты и
 * «старшие K рангов» — по таблицам на 8192 входа (все 13-битные маски), заполняемым при запуске.
 * Без сортировки и без выделения памяти на руку.
 *
 * Ранг — `Int`, больше = сильнее, равные ранги — делёж банка:
 * `категория << 26 | старшая часть << 13 | кикеры`, где части — маски рангов (бит на ранг);
 * сравнение масок одинаковой длины как чисел — лексикографическое сравнение рангов по убыванию.
 */
object HandEvaluator {
    private const val RANK_BITS = 13
    private const val RANK_MASK = (1 shl RANK_BITS) - 1
    private const val TABLE_SIZE = 1 shl RANK_BITS
    private const val CATEGORY_SHIFT = 26
    private const val PRIMARY_SHIFT = 13
    private const val FLUSH_CARDS = 5
    private const val STRAIGHT_CARDS = 5
    private const val WHEEL = 0x100F // A, 2, 3, 4, 5
    private const val WHEEL_TOP = 3 // «пятёрка»
    private const val THREE_KICKERS = 3
    private const val SUIT_1 = 16
    private const val SUIT_2 = 32
    private const val SUIT_3 = 48
    private const val NO_STRAIGHT = -1

    /** Старший ранг стрита (индекс 0…12, колесо A–5 = 3) или −1. */
    private val straightTop = IntArray(TABLE_SIZE) { straightTopOf(it) }
    private val top1 = IntArray(TABLE_SIZE) { topBits(it, 1) }
    private val top2 = IntArray(TABLE_SIZE) { topBits(it, 2) }
    private val top3 = IntArray(TABLE_SIZE) { topBits(it, THREE_KICKERS) }
    private val top5 = IntArray(TABLE_SIZE) { topBits(it, FLUSH_CARDS) }

    /** Ранг роял-флеша — самый сильный из возможных. */
    val royalFlushRank: Int = encode(HandCategory.STRAIGHT_FLUSH, 1 shl Rank.ACE.ordinal, 0)

    /** Ранг лучшей комбинации из [cards] (1–7 разных карт). */
    fun rank(cards: Collection<Card>): Int {
        require(cards.size in 1..MAX_CARDS) { "Нужно от 1 до 7 карт, передано ${cards.size}" }
        val mask = cards.toMask()
        require(mask.countOneBits() == cards.size) { "Карты повторяются: $cards" }
        return evaluate(mask)
    }

    fun category(cards: Collection<Card>): HandCategory = categoryOf(rank(cards))

    fun categoryOf(rank: Int): HandCategory = HandCategory.entries[rank ushr CATEGORY_SHIFT]

    fun isRoyalFlush(rank: Int): Boolean = rank == royalFlushRank

    /** Ранг по маске (без проверок) — горячий путь эквити. */
    internal fun evaluate(mask: Long): Int {
        val s0 = (mask and RANK_MASK.toLong()).toInt()
        val s1 = ((mask ushr SUIT_1) and RANK_MASK.toLong()).toInt()
        val s2 = ((mask ushr SUIT_2) and RANK_MASK.toLong()).toInt()
        val s3 = ((mask ushr SUIT_3) and RANK_MASK.toLong()).toInt()
        val flush = when {
            s0.countOneBits() >= FLUSH_CARDS -> s0
            s1.countOneBits() >= FLUSH_CARDS -> s1
            s2.countOneBits() >= FLUSH_CARDS -> s2
            s3.countOneBits() >= FLUSH_CARDS -> s3
            else -> 0
        }
        return if (flush != 0) flushRank(flush) else plainRank(s0, s1, s2, s3)
    }

    private fun flushRank(suit: Int): Int {
        val top = straightTop[suit]
        return if (top != NO_STRAIGHT) {
            encode(HandCategory.STRAIGHT_FLUSH, 1 shl top, 0)
        } else {
            encode(HandCategory.FLUSH, top5[suit], 0)
        }
    }

    /** Без флеша: (s0…s3) — маски рангов по мастям. */
    private fun plainRank(a: Int, b: Int, c: Int, d: Int): Int {
        val all = a or b or c or d
        val quads = a and b and c and d
        val three = (a and b and c) or (a and b and d) or (a and c and d) or (b and c and d)
        val two = (a and b) or (a and c) or (a and d) or (b and c) or (b and d) or (c and d)
        val topTrips = top1[three]
        val straight = straightTop[all]
        return when {
            quads != 0 -> encode(HandCategory.FOUR_OF_A_KIND, quads, top1[all and quads.inv()])

            three != 0 && (two and topTrips.inv()) != 0 ->
                encode(HandCategory.FULL_HOUSE, topTrips, top1[two and topTrips.inv()])

            straight != NO_STRAIGHT -> encode(HandCategory.STRAIGHT, 1 shl straight, 0)

            three != 0 -> encode(HandCategory.THREE_OF_A_KIND, three, top2[all and three.inv()])

            (two and (two - 1)) != 0 -> {
                val pairs = top2[two]
                encode(HandCategory.TWO_PAIR, pairs, top1[all and pairs.inv()])
            }

            two != 0 -> encode(HandCategory.ONE_PAIR, two, top3[all and two.inv()])

            else -> encode(HandCategory.HIGH_CARD, top5[all], 0)
        }
    }

    private fun encode(category: HandCategory, primary: Int, kickers: Int): Int =
        (category.ordinal shl CATEGORY_SHIFT) or (primary shl PRIMARY_SHIFT) or kickers

    private fun straightTopOf(mask: Int): Int {
        val run = (1 shl STRAIGHT_CARDS) - 1
        val top = (Rank.ACE.ordinal downTo STRAIGHT_CARDS - 1).firstOrNull { high ->
            val window = run shl (high - (STRAIGHT_CARDS - 1))
            (mask and window) == window
        }
        return top ?: if ((mask and WHEEL) == WHEEL) WHEEL_TOP else NO_STRAIGHT
    }

    /** Оставить [count] старших битов маски. */
    private fun topBits(mask: Int, count: Int): Int {
        var rest = mask
        var result = 0
        repeat(count) {
            val high = rest.takeHighestOneBit()
            result = result or high
            rest = rest and high.inv()
        }
        return result
    }

    internal const val MAX_CARDS = 7
}
