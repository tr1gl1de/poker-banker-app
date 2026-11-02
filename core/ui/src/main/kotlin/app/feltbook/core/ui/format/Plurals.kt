package app.feltbook.core.ui.format

import kotlin.math.absoluteValue

/**
 * Количество для `plurals` по `Long` (m3-ui-spec §0.1): русское правило зависит только от двух последних цифр,
 * поэтому `n % 100` (+100 для n ≥ 100, чтобы не путать «1» и «101» в языках с правилом по целому числу).
 * Знак отбрасывается: «минус 21 рубль».
 */
fun pluralQuantity(n: Long): Int {
    val lastTwo = (n % HUNDRED).toInt().absoluteValue
    val large = n >= HUNDRED || n <= -HUNDRED
    return lastTwo + if (large) HUNDRED.toInt() else 0
}

private const val HUNDRED = 100L
