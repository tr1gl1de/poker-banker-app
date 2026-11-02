package app.feltbook.core.domain.model

import app.feltbook.core.domain.engine.Money

/** Режим сбора банкира (ТЗ §3.1–3.2). */
enum class BankerFeeMode {
    /** Разово, фиксированная сумма `bankerFeeValue` ₽ — только с первого закупа игрока в этой игре. */
    ONCE_FIXED,

    /** Разово, `bankerFeeValue` % от первого закупа. */
    ONCE_PERCENT,

    /** С каждого закупа и докупа, фиксированная сумма. */
    EACH_FIXED,

    /** С каждого закупа и докупа, `bankerFeeValue` % от покупки. */
    EACH_PERCENT,
    ;

    val isOnce: Boolean get() = this == ONCE_FIXED || this == ONCE_PERCENT
    val isPercent: Boolean get() = this == ONCE_PERCENT || this == EACH_PERCENT
}

/** Режим рейка заведению: процент от каждой покупки или фиксированная сумма за каждую. */
enum class RakeMode { PERCENT, FIXED }

/**
 * Слепок правил, копируется в [Game] при создании и больше не меняется (CLAUDE.md, правило 3).
 *
 * Значения `bankerFeeValue`/`rakeValue` трактуются по режиму: в `*_FIXED` — рубли,
 * в `*_PERCENT` — целые проценты 0..100 (ADR 0005: дробных процентов в v1 нет).
 *
 * @property minBuyIn минимальная покупка в фишках (ТЗ §3.1, «Минимальная покупка»); любая сумма ≥ минимума, без шага.
 * @property splitPayment раздельная оплата: в шторке два переключателя «Фишки» и «Рейк и сбор».
 * @property minPlayMinutes минимальное время игры от первого закупа игрока (ТЗ §3.4).
 */
data class GameRules(
    val bankerFeeMode: BankerFeeMode = BankerFeeMode.ONCE_FIXED,
    val bankerFeeValue: Long = 100,
    val rakeMode: RakeMode = RakeMode.PERCENT,
    val rakeValue: Long = 10,
    val minBuyIn: Long = 500,
    val splitPayment: Boolean = false,
    val minPlayMinutes: Int = 60,
) {
    /** Пустой список — правила допустимы. Экран «Новая игра» и «Настройки» не сохраняют правила с проблемами. */
    fun problems(): List<RulesProblem> = buildList {
        when {
            bankerFeeValue < 0 -> add(RulesProblem.BANKER_FEE_NEGATIVE)
            bankerFeeMode.isPercent && bankerFeeValue > MAX_PERCENT -> add(RulesProblem.BANKER_FEE_PERCENT_ABOVE_MAX)
            bankerFeeValue > Money.MAX_AMOUNT -> add(RulesProblem.BANKER_FEE_ABOVE_MAX_AMOUNT)
        }
        when {
            rakeValue < 0 -> add(RulesProblem.RAKE_NEGATIVE)
            rakeMode == RakeMode.PERCENT && rakeValue > MAX_PERCENT -> add(RulesProblem.RAKE_PERCENT_ABOVE_MAX)
            rakeValue > Money.MAX_AMOUNT -> add(RulesProblem.RAKE_ABOVE_MAX_AMOUNT)
        }
        when {
            minBuyIn <= 0 -> add(RulesProblem.MIN_BUY_IN_NOT_POSITIVE)
            minBuyIn > Money.MAX_AMOUNT -> add(RulesProblem.MIN_BUY_IN_ABOVE_MAX_AMOUNT)
        }
        if (minPlayMinutes !in 0..MAX_MIN_PLAY_MINUTES) add(RulesProblem.MIN_PLAY_MINUTES_OUT_OF_RANGE)
    }

    companion object {
        /** Значения по умолчанию из ТЗ §3.1: разово 100 ₽, рейк 10 %, минимум 500, одним статусом, 60 мин. */
        val DEFAULT: GameRules = GameRules()
        const val MAX_PERCENT: Long = 100
        const val MAX_MIN_PLAY_MINUTES: Int = 24 * 60
    }
}

enum class RulesProblem {
    BANKER_FEE_NEGATIVE,
    BANKER_FEE_PERCENT_ABOVE_MAX,
    BANKER_FEE_ABOVE_MAX_AMOUNT,
    RAKE_NEGATIVE,
    RAKE_PERCENT_ABOVE_MAX,
    RAKE_ABOVE_MAX_AMOUNT,
    MIN_BUY_IN_NOT_POSITIVE,
    MIN_BUY_IN_ABOVE_MAX_AMOUNT,
    MIN_PLAY_MINUTES_OUT_OF_RANGE,
}
