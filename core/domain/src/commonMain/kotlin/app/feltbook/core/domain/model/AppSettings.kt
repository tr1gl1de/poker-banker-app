package app.feltbook.core.domain.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/**
 * Настройки (DataStore). Значения по умолчанию — ТЗ §3.1, §5.1 и макет Settings.
 *
 * @property rulesForNewGame правила, которыми заполняется «Новая игра». Одно значение: его меняют «Настройки → Правила
 *   по умолчанию» и старт игры с изменёнными правилами («подставляются из последних использованных», ТЗ §4).
 *   QUESTIONS №4.
 * @property lastBankerName имя банкира для «Новой игры», имя для Nearby-рекламы и `bankerName` погашений вне
 *   активной игры.
 * @property bankerPlays последнее «Я тоже играю» в «Новой игре» (QUESTIONS №21), по умолчанию — играет.
 * @property dynamicColor «Цвета обоев — Material You вместо сукна», по умолчанию выключено.
 * @property oddsCalculatorVisible пасхалка открыта и пункт «Калькулятор шансов» виден; «скрыть» = false.
 */
data class AppSettings(
    val rulesForNewGame: GameRules = GameRules.DEFAULT,
    val lastBankerName: String = "",
    val bankerPlays: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val keepScreenOn: Boolean = true,
    val haptics: Boolean = true,
    val oddsCalculatorVisible: Boolean = false,
)
