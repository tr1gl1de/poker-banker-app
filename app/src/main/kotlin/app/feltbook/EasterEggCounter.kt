package app.feltbook

/** Итог нажатия на «Настройки» для пасхалки (m3-ui-spec §8.3). */
sealed interface EasterEggTap {
    data object Silent : EasterEggTap

    /** «Ещё N…» с 7-го нажатия. */
    data class Hint(val remaining: Int) : EasterEggTap

    /** 11-е нажатие: калькулятор открыт. */
    data object Unlocked : EasterEggTap
}

/**
 * Счётчик 11 нажатий по «Настройкам»: если с прошлого прошло больше [windowMillis] — счёт с 1, иначе +1.
 * С 7-го — подсказка «Ещё 4…» … «Ещё 1…», 11-е — открытие и сброс. Уже открытый калькулятор — без подсказок.
 */
class EasterEggCounter(
    private val taps: Int = TAPS_TO_UNLOCK,
    private val hintFrom: Int = HINT_FROM_TAP,
    private val windowMillis: Long = TAP_WINDOW_MILLIS,
) {
    private var count = 0
    private var lastTapAt: Long? = null

    fun tap(nowMillis: Long, alreadyUnlocked: Boolean): EasterEggTap {
        val last = lastTapAt
        count = if (last == null || nowMillis - last > windowMillis) 1 else count + 1
        lastTapAt = nowMillis
        if (alreadyUnlocked) return EasterEggTap.Silent
        return when {
            count >= taps -> {
                count = 0
                lastTapAt = null
                EasterEggTap.Unlocked
            }

            count >= hintFrom -> EasterEggTap.Hint(taps - count)

            else -> EasterEggTap.Silent
        }
    }

    companion object {
        const val TAPS_TO_UNLOCK = 11
        const val HINT_FROM_TAP = 7
        const val TAP_WINDOW_MILLIS = 1_000L
    }
}
