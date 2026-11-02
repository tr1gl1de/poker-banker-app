package app.feltbook.core.ui.component

import androidx.compose.runtime.Immutable

/** Клавиша цифровой клавиатуры. */
sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey {
        init {
            require(value in 0..MAX_DIGIT) { "digit $value" }
        }
    }

    data object TripleZero : KeypadKey

    data object Backspace : KeypadKey

    /** Долгое нажатие на «Стереть». */
    data object Clear : KeypadKey
}

private const val MAX_DIGIT = 9
private const val TEN = 10L
private const val THOUSAND = 1_000L

/** Предел ввода по умолчанию — `Money.MAX_AMOUNT` домена (100 000 000). */
const val DEFAULT_MAX_AMOUNT: Long = 100_000_000L

/**
 * Состояние ввода суммы с [AmountKeypad] (m3-ui-spec §4.3). `value == 0` — пусто. Ведущие нули не вводятся,
 * «000» к пустому — ничего. [replaceOnNextInput] — сумма поставлена фишкой: следующая цифра начинает новое число.
 * Чистая логика: ViewModel хранит `AmountEntry` в `UiState`.
 */
@Immutable
data class AmountEntry(val value: Long = 0L, val replaceOnNextInput: Boolean = false) {
    val isEmpty: Boolean get() = value == 0L

    /** Новое состояние после [key]; `null` — нажатие отклонено (превышение [maxValue]): короткая вибрация отказа. */
    fun press(key: KeypadKey, maxValue: Long = DEFAULT_MAX_AMOUNT): AmountEntry? {
        val base = if (replaceOnNextInput) 0L else value
        return when (key) {
            is KeypadKey.Digit ->
                when {
                    base == 0L && key.value == 0 -> AmountEntry(0L)
                    base > (maxValue - key.value) / TEN -> null
                    else -> AmountEntry(base * TEN + key.value)
                }

            KeypadKey.TripleZero ->
                when {
                    base == 0L -> AmountEntry(0L)
                    base > maxValue / THOUSAND -> null
                    else -> AmountEntry(base * THOUSAND)
                }

            KeypadKey.Backspace -> AmountEntry(value / TEN)

            KeypadKey.Clear -> AmountEntry(0L)
        }
    }

    companion object {
        /** Сумма, поставленная фишкой или подставленная по умолчанию. */
        fun preset(amount: Long): AmountEntry = AmountEntry(amount, replaceOnNextInput = true)
    }
}
