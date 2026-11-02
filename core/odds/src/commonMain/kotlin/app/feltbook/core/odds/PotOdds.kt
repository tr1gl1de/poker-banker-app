package app.feltbook.core.odds

/** Сравнение эквити с шансами банка — только математика, без советов. */
enum class PotOddsVerdict {
    /** Эквити больше нужной доли: «Колл окупается по шансам банка». */
    PAYS_OFF,

    /** «Колл не окупается». */
    DOES_NOT_PAY,
}

/**
 * Шансы банка: [pot] — банк до нашего колла, [call] — ставка, которую нужно уравнять (рубли/фишки).
 * Нужная доля `call / (pot + call)` — [pot odds](https://en.wikipedia.org/wiki/Pot_odds).
 */
data class PotOdds(val pot: Long, val call: Long) {
    init {
        require(pot >= 0) { "Банк не может быть отрицательным: $pot" }
        require(call > 0) { "Ставка должна быть больше нуля: $call" }
    }

    /** Нужная доля от 0 до 1. */
    val required: Double get() = call.toDouble() / (pot + call)

    /** [equity] — доля от 0 до 1 ([EquityResult.equity]). Равенство — «не окупается» (колл в ноль). */
    fun verdict(equity: Double): PotOddsVerdict =
        if (equity > required) PotOddsVerdict.PAYS_OFF else PotOddsVerdict.DOES_NOT_PAY

    companion object {
        /** `null`, если ставки нет (UI: «введите банк и ставку») или банк отрицательный. */
        fun ofOrNull(pot: Long, call: Long): PotOdds? = if (pot >= 0 && call > 0) PotOdds(pot, call) else null
    }
}
