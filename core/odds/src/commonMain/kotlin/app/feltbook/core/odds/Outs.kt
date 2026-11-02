package app.feltbook.core.odds

import kotlin.math.min

/**
 * Ауты на флопе или тёрне.
 *
 * Аут — неизвестная карта, после которой категория руки становится выше текущей [current], причём
 * отрыв от того, что показывает один стол, растёт (в порядке [HandCategory]). Так не считаются
 * улучшения, общие для всех: спарившийся стол (AA на 9-8-7 + 9 — «две пары» есть у каждого), стрит
 * или флеш целиком на столе. Сет из пары, флеш, стрит, вторая пара с нашей картой — ауты.
 * Пара из «старшей карты» аутом не считается (такая пара редко выигрывает): карты, спаривающие
 * свою карту старше всего стола, — [overcards], отдельно и не входят в [total] и шансы.
 * [outs] — ауты по категории, которую они дают (от сильной к слабой); [unseen] — неизвестных карт
 * (колода без своих, стола и мёртвых; карты соперников неизвестны и входят сюда).
 */
data class OutsResult(
    val current: HandCategory,
    val outs: Map<HandCategory, List<Card>>,
    val unseen: Int,
    val streetsLeft: Int,
    val overcards: List<Card> = emptyList(),
) {
    val total: Int get() = outs.values.sumOf { it.size }

    /** Самый сильный вид дро («флеш-дро») или `null`, если аутов нет. */
    val strongest: HandCategory? get() = outs.keys.maxOrNull()

    /**
     * Сколько разных рангов закрывает стрит: 1 — гатшот (4 аута), 2 — двусторонний (8 аутов); 0 — стрит-дро нет.
     */
    val straightRanks: Int get() = outs[HandCategory.STRAIGHT]?.map { it.rank }?.distinct()?.size ?: 0

    /** Точный шанс усилиться следующей картой: `outs / unseen`. */
    val nextCard: Double get() = total.toDouble() / unseen

    /** Точный шанс к риверу (только на флопе): `1 − C(unseen − outs, 2) / C(unseen, 2)`. */
    val byRiver: Double?
        get() = if (streetsLeft < 2) {
            null
        } else {
            1.0 - Combinatorics.choose(unseen - total, 2).toDouble() / Combinatorics.choose(unseen, 2)
        }

    /** Правило 2: ауты × 2 % на одну улицу (не больше 100). */
    val ruleOfTwo: Int get() = min(total * 2, PERCENT)

    /** Правило 4: ауты × 4 % на две улицы — только на флопе. */
    val ruleOfFour: Int? get() = if (streetsLeft < 2) null else min(total * RULE_FOUR, PERCENT)

    private companion object {
        const val PERCENT = 100
        const val RULE_FOUR = 4
    }
}

object OutsCalculator {
    private const val FLOP = 3
    private const val TURN = 4

    /** Ауты для [hole] (2 карты) и [board] (3 или 4 карты); [dead] исключаются из колоды. */
    fun outs(hole: List<Card>, board: List<Card>, dead: Set<Card> = emptySet()): OutsResult {
        require(hole.size == EquityRequest.HOLE_CARDS) { "Своих карт должно быть 2, передано ${hole.size}" }
        require(board.size == FLOP || board.size == TURN) { "Ауты считаются на флопе и тёрне" }
        val known = hole + board + dead
        require(known.toSet().size == known.size) { "Карты повторяются: $known" }

        val handMask = (hole + board).toMask()
        val boardMask = board.toMask()
        val current = HandEvaluator.categoryOf(HandEvaluator.evaluate(handMask))
        val lead = current.ordinal - HandEvaluator.categoryOf(HandEvaluator.evaluate(boardMask)).ordinal
        val unseenCards = Deck.cards - known.toSet()
        val improving = unseenCards.mapNotNull { card ->
            val improved = HandEvaluator.categoryOf(HandEvaluator.evaluate(handMask or card.bit))
            val boardOnly = HandEvaluator.categoryOf(HandEvaluator.evaluate(boardMask or card.bit))
            val improves = improved > current && improved.ordinal - boardOnly.ordinal > lead
            if (improves) improved to card else null
        }
        // Пара из старшей карты — не аут; спаривание своей карты старше стола показываем как оверкарты.
        val (pairs, real) = improving.partition { (improved, _) ->
            current == HandCategory.HIGH_CARD && improved == HandCategory.ONE_PAIR
        }
        val boardTop = board.maxOf { it.rank }
        val overRanks = hole.map { it.rank }.filter { it > boardTop }.toSet()
        val outs = real
            .groupBy({ it.first }, { it.second })
            .toList()
            .sortedByDescending { it.first }
            .toMap()
        return OutsResult(
            current = current,
            outs = outs,
            unseen = unseenCards.size,
            streetsLeft = EquityRequest.BOARD_CARDS - board.size,
            overcards = pairs.map { it.second }.filter { it.rank in overRanks },
        )
    }
}
