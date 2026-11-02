package app.feltbook.core.domain.engine

object Money {
    /** Верхняя граница любой суммы во входе (1e8 ₽): `amount * 100` заведомо помещается в Long. */
    const val MAX_AMOUNT: Long = 100_000_000L

    /**
     * Верхняя граница суммы любой записи журнала (1e12 ₽) — для итоговых записей, которые движок считает сам:
     * оплата покупки целиком, расчёт при выходе, рейк к отдаче, забранный доход, прощение всего долга.
     * Каждый ввод ≤ [MAX_AMOUNT], поэтому итоговая запись больше 1e8 законна; 1e12 оставляет запас до `Long.MAX_VALUE`
     * (≈ 9,2e18) в 9 миллионов раз: суммы по журналу из миллиона записей (≤ 1e18) не переполняются.
     * Валидатор и кодек принимают записи `≤ MAX_TOTAL`; команда, которая записала бы больше, — `AmountTooLarge`.
     */
    const val MAX_TOTAL: Long = 1_000_000_000_000L

    private const val PERCENT_BASE: Long = 100L

    /**
     * `round(amount * percent / 100)` математически, половина — вверх: `(amount * percent + 50) / 100`.
     * 1 250 × 10 % = 125; 1 255 × 10 % = 126 (125,5 → 126); 1 254 × 10 % = 125.
     * Отрицательные суммы не округляются — их не бывает по контракту (все `amount` ≥ 0), поэтому `require`.
     * @throws IllegalArgumentException если `amount !in 0..MAX_AMOUNT` или `percent !in 0..100`.
     */
    fun percentOf(amount: Long, percent: Long): Long {
        require(amount in 0..MAX_AMOUNT) { "amount $amount out of 0..$MAX_AMOUNT" }
        require(percent in 0..PERCENT_BASE) { "percent $percent out of 0..$PERCENT_BASE" }
        return divRoundHalfUp(amount * percent, PERCENT_BASE)
    }

    /** Деление с округлением половины вверх для средних (средний закуп). `numerator ≥ 0`, `denominator > 0`. */
    fun divRoundHalfUp(numerator: Long, denominator: Long): Long {
        require(numerator >= 0) { "numerator $numerator < 0" }
        require(denominator > 0) { "denominator $denominator <= 0" }
        val quotient = numerator / denominator
        val remainder = numerator % denominator
        return if (remainder >= denominator - remainder) quotient + 1 else quotient
    }
}
