package app.feltbook.core.domain

/**
 * Процент от суммы в рублях, округлённый математически до рубля (железное правило №1).
 * Заглушка M0: движок расчёта появится на этапе M1.
 */
fun percentOf(amount: Long, percent: Long): Long {
    val scaled = amount * percent
    val quotient = scaled / PERCENT_BASE
    val remainder = scaled % PERCENT_BASE
    return if (remainder * 2 >= PERCENT_BASE) quotient + 1 else quotient
}

private const val PERCENT_BASE = 100L
