package app.feltbook.core.ui.format

import kotlin.math.absoluteValue
import kotlin.math.roundToLong

/**
 * Форматирование чисел и денег (m3-ui-spec §0.1). Без `Locale`: результат одинаков на любом телефоне.
 * Тысячи — через неразрывный пробел U+00A0 (с 1 000), минус — U+2212, перед `₽` и `%` — неразрывный пробел.
 */
object MoneyFormat {
    const val NBSP: Char = '\u00A0'
    const val MINUS: Char = '\u2212'
    const val PLUS: Char = '+'
    const val RUBLE: Char = '\u20BD'
    const val DECIMAL_COMMA: Char = ','
    const val PLUS_MINUS: Char = '\u00B1'

    /** `500`, `1 053`, `−3 400`. */
    fun number(value: Long): String {
        val grouped = group(value.toString().removePrefix("-"))
        return if (value < 0) "$MINUS$grouped" else grouped
    }

    /** Со знаком у ненулевых: `+2 700`, `−500`, `0`. */
    fun signedNumber(value: Long): String = if (value > 0) "$PLUS${number(value)}" else number(value)

    /** `2 300 ₽`, `−3 400 ₽`. */
    fun rub(value: Long): String = number(value) + NBSP + RUBLE

    /** `+353 ₽`, `−353 ₽`, `0 ₽`. */
    fun signedRub(value: Long): String = signedNumber(value) + NBSP + RUBLE

    /** Целый процент: `10 %`. */
    fun percent(value: Int): String = number(value.toLong()) + NBSP + '%'

    /** Процент с одним знаком после запятой (калькулятор): `38,4 %`. Округление половины от нуля. */
    fun percentOneDecimal(value: Double): String = tenths((value * TEN).roundToLong()) + NBSP + '%'

    /** Погрешность калькулятора: `±0,3 %`. */
    fun plusMinusPercent(value: Double): String = "$PLUS_MINUS${percentOneDecimal(value.absoluteValue)}"

    /**
     * «За игру» с одним знаком: `numerator / denominator`, округление половины вверх в целых (`1,4`).
     * [denominator] > 0; значения неотрицательные.
     */
    fun ratioOneDecimal(numerator: Long, denominator: Long): String {
        require(denominator > 0) { "denominator must be positive" }
        require(numerator >= 0) { "numerator must be non-negative" }
        // round(x / d) половина вверх = floor((2x + d) / 2d), где x = numerator * 10.
        val scaled = numerator * TEN_L
        val rounded = (2 * scaled + denominator) / (2 * denominator)
        return tenths(rounded)
    }

    private fun tenths(value: Long): String {
        val sign = if (value < 0) MINUS.toString() else ""
        val abs = value.absoluteValue
        return "$sign${group((abs / TEN_L).toString())}$DECIMAL_COMMA${abs % TEN_L}"
    }

    private fun group(digits: String): String {
        if (digits.length <= GROUP) return digits
        val out = StringBuilder(digits.length + digits.length / GROUP)
        val head = digits.length % GROUP
        if (head > 0) out.append(digits, 0, head)
        var i = head
        while (i < digits.length) {
            if (out.isNotEmpty()) out.append(NBSP)
            out.append(digits, i, i + GROUP)
            i += GROUP
        }
        return out.toString()
    }

    private const val GROUP = 3
    private const val TEN = 10.0
    private const val TEN_L = 10L
}
