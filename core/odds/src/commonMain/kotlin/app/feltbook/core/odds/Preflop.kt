package app.feltbook.core.odds

/**
 * Класс стартовой руки из 169: пара («AA»), одномастная («AKs») или разномастная («AKo»).
 * [high] ≥ [low]; у пары [suited] = false.
 */
data class HandClass(val high: Rank, val low: Rank, val suited: Boolean) {
    init {
        require(high >= low) { "Старшая карта не младше младшей: $high, $low" }
        require(!(isPair && suited)) { "Пара не бывает одномастной" }
    }

    val isPair: Boolean get() = high == low

    /** Число раздач класса из 1326: пара — 6, одномастная — 4, разномастная — 12. */
    val combos: Int get() = when {
        isPair -> PAIR_COMBOS
        suited -> SUITED_COMBOS
        else -> OFFSUIT_COMBOS
    }

    /** «AA», «AKs», «T9o». */
    val label: String
        get() = buildString {
            append(high.symbol)
            append(low.symbol)
            if (!isPair) append(if (suited) 's' else 'o')
        }

    override fun toString(): String = label

    companion object {
        const val PAIR_COMBOS = 6
        const val SUITED_COMBOS = 4
        const val OFFSUIT_COMBOS = 12
        const val TOTAL_COMBOS = 1326
        const val COUNT = 169
        private const val LABEL_UNPAIRED = 3

        fun of(first: Card, second: Card): HandClass {
            require(first != second) { "Две одинаковые карты: $first" }
            val (high, low) = if (first.rank >= second.rank) first to second else second to first
            return HandClass(high.rank, low.rank, suited = first.suit == second.suit && first.rank != second.rank)
        }

        /** Разбор «AA», «AKs», «AKo». */
        fun parse(label: String): HandClass = requireNotNull(parseOrNull(label)) { "Не класс руки: «$label»" }

        fun parseOrNull(label: String): HandClass? {
            val high = label.getOrNull(0)?.let(Rank::fromSymbol)
            val low = label.getOrNull(1)?.let(Rank::fromSymbol)
            val suffix = label.getOrNull(2)
            val shapeOk = when (label.length) {
                2 -> high == low
                LABEL_UNPAIRED -> high != low && (suffix == 's' || suffix == 'o')
                else -> false
            }
            return if (!shapeOk || high == null || low == null) {
                null
            } else {
                if (high >= low) HandClass(high, low, suited = suffix == 's') else null
            }
        }
    }
}

/**
 * Сила стартовой руки на префлопе: место [rank] из 169 (1 — сильнейшая), эквити против одной
 * случайной руки [equityVsRandom] и процентиль «топ [topPercent] % рук» — доля всех 1326 раздач,
 * которые не слабее этой (с учётом числа комбинаций класса).
 */
data class PreflopStrength(val handClass: HandClass, val rank: Int, val equityVsRandom: Double, val topPercent: Double)

/**
 * Таблица силы 169 классов.
 *
 * Метод: эквити класса хедз-ап против одной случайной руки до вскрытия (делёж = ½), Монте-Карло по
 * 3 000 000 раздач на класс (погрешность ≈ ±0,05 %), xorshift128+ с фиксированным зерном, тот же
 * битовый оценщик, что [HandEvaluator]. Пересчёт и сверка — jvmTest `PreflopTableRegeneratorTest`
 * с `FELTBOOK_REGEN_PREFLOP=1`. Классы упорядочены по убыванию эквити; значение — эквити
 * × 10 000, округлённое. Сверка в тестах: таблица согласована с [EquityCalculator].
 * Это ориентир силы руки, а не стратегия: против нескольких соперников порядок немного иной.
 */
object PreflopTable {
    private const val PERMYRIAD = 10_000.0
    private const val PERCENT = 100.0

    private val ROWS = listOf(
        "AA:8520 KK:8237 QQ:7991 JJ:7746 TT:7499 99:7205 88:6913 AKs:6703 AQs:6625 77:6622",
        "AJs:6537 AKo:6533 ATs:6462 AQo:6440 AJo:6355 KQs:6340 66:6329 A9s:6279 ATo:6270 KJs:6256",
        "A8s:6196 KTs:6177 KQo:6144 A7s:6093 A9o:6074 KJo:6054 55:6038 QJs:6022 K9s:6002 A6s:5991",
        "A5s:5988 A8o:5983 KTo:5974 QTs:5946 A4s:5898 A7o:5887 K8s:5832 A3s:5819 QJo:5814 K9o:5781",
        "A6o:5768 A5o:5768 Q9s:5760 K7s:5757 JTs:5748 A2s:5741 QTo:5729 44:5705 A4o:5677 K6s:5665",
        "Q8s:5605 K8o:5601 A3o:5583 K5s:5574 J9s:5566 Q9o:5540 JTo:5524 K7o:5520 A2o:5494 K4s:5490",
        "Q7s:5426 K6o:5422 K3s:5409 T9s:5405 J8s:5401 33:5368 Q6s:5364 Q8o:5362 K5o:5338 J9o:5330",
        "K2s:5319 Q5s:5274 T8s:5237 J7s:5234 K4o:5233 Q4s:5186 Q7o:5178 T9o:5155 J8o:5147 K3o:5143",
        "Q6o:5107 Q3s:5102 98s:5077 T7s:5070 J6s:5063 K2o:5047 22:5033 Q2s:5017 Q5o:5012 J5s:5005",
        "J7o:4970 T8o:4969 Q4o:4916 97s:4909 J4s:4907 T6s:4894 Q3o:4823 J3s:4822 98o:4809 87s:4795",
        "T7o:4793 J6o:4787 96s:4744 J2s:4738 Q2o:4734 J5o:4719 T5s:4719 T4s:4656 97o:4632 86s:4621",
        "J4o:4619 T6o:4612 95s:4574 T3s:4571 76s:4540 J3o:4529 87o:4502 T2s:4486 85s:4452 96o:4447",
        "J2o:4435 T5o:4426 94s:4388 75s:4368 T4o:4350 93s:4329 86o:4325 65s:4313 95o:4272 84s:4272",
        "T3o:4257 92s:4241 76o:4233 74s:4191 T2o:4165 54s:4145 85o:4139 64s:4133 83s:4090 94o:4068",
        "75o:4049 82s:4026 73s:4004 65o:3997 93o:3996 53s:3970 63s:3948 84o:3946 92o:3908 43s:3865",
        "74o:3851 72s:3814 54o:3813 64o:3801 52s:3787 62s:3770 83o:3747 82o:3682 42s:3680 73o:3658",
        "53o:3627 63o:3607 32s:3601 43o:3508 72o:3460 52o:3431 62o:3410 42o:3324 32o:3227",
    )

    /** Классы по убыванию силы. */
    val ranking: List<HandClass>

    private val strengths: Map<HandClass, PreflopStrength>

    init {
        val entries = ROWS.flatMap { it.split(' ') }.map { cell ->
            val (label, value) = cell.split(':')
            HandClass.parse(label) to value.toInt() / PERMYRIAD
        }
        ranking = entries.map { it.first }
        var cumulative = 0
        strengths = entries.mapIndexed { index, (handClass, equity) ->
            cumulative += handClass.combos
            handClass to PreflopStrength(
                handClass = handClass,
                rank = index + 1,
                equityVsRandom = equity,
                topPercent = cumulative * PERCENT / HandClass.TOTAL_COMBOS,
            )
        }.toMap()
    }

    fun strength(handClass: HandClass): PreflopStrength = strengths.getValue(handClass)

    fun strength(hole: List<Card>): PreflopStrength {
        require(hole.size == EquityRequest.HOLE_CARDS) { "Своих карт должно быть 2, передано ${hole.size}" }
        return strength(HandClass.of(hole[0], hole[1]))
    }
}
