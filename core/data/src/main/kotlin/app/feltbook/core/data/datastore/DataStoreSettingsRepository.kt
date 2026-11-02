package app.feltbook.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * Настройки в DataStore Preferences. Отсутствующий ключ (или нечитаемое значение перечисления) — значение по умолчанию
 * из `AppSettings()`, поэтому пустое хранилище = `AppSettings()`. Ошибка чтения файла (IOException) — один раз значения
 * по умолчанию, затем повторные попытки чтения; испорченный файл заменяется пустым ([SettingsDataStore]).
 */
internal class DataStoreSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            // Один раз — значения по умолчанию, чтобы экран не ждал; дальше продолжаем читать файл с паузами.
            if (attempt == 0L) emit(emptyPreferences())
            delay(RETRY_DELAY * (1 shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT).toInt()))
            true
        }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        val rules = defaults.rulesForNewGame
        return AppSettings(
            rulesForNewGame = GameRules(
                bankerFeeMode = enumOr(this[Keys.BANKER_FEE_MODE], rules.bankerFeeMode),
                bankerFeeValue = this[Keys.BANKER_FEE_VALUE] ?: rules.bankerFeeValue,
                rakeMode = enumOr(this[Keys.RAKE_MODE], rules.rakeMode),
                rakeValue = this[Keys.RAKE_VALUE] ?: rules.rakeValue,
                minBuyIn = this[Keys.MIN_BUY_IN] ?: rules.minBuyIn,
                splitPayment = this[Keys.SPLIT_PAYMENT] ?: rules.splitPayment,
                minPlayMinutes = this[Keys.MIN_PLAY_MINUTES] ?: rules.minPlayMinutes,
            ),
            lastBankerName = this[Keys.LAST_BANKER_NAME] ?: defaults.lastBankerName,
            bankerPlays = this[Keys.BANKER_PLAYS] ?: defaults.bankerPlays,
            themeMode = enumOr(this[Keys.THEME_MODE], defaults.themeMode),
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            haptics = this[Keys.HAPTICS] ?: defaults.haptics,
            oddsCalculatorVisible = this[Keys.ODDS_CALCULATOR_VISIBLE] ?: defaults.oddsCalculatorVisible,
        )
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        val rules = settings.rulesForNewGame
        this[Keys.BANKER_FEE_MODE] = rules.bankerFeeMode.name
        this[Keys.BANKER_FEE_VALUE] = rules.bankerFeeValue
        this[Keys.RAKE_MODE] = rules.rakeMode.name
        this[Keys.RAKE_VALUE] = rules.rakeValue
        this[Keys.MIN_BUY_IN] = rules.minBuyIn
        this[Keys.SPLIT_PAYMENT] = rules.splitPayment
        this[Keys.MIN_PLAY_MINUTES] = rules.minPlayMinutes
        this[Keys.LAST_BANKER_NAME] = settings.lastBankerName
        this[Keys.BANKER_PLAYS] = settings.bankerPlays
        this[Keys.THEME_MODE] = settings.themeMode.name
        this[Keys.DYNAMIC_COLOR] = settings.dynamicColor
        this[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn
        this[Keys.HAPTICS] = settings.haptics
        this[Keys.ODDS_CALCULATOR_VISIBLE] = settings.oddsCalculatorVisible
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default

    /** Имена ключей — часть формата хранения: не переименовывать без миграции. */
    private object Keys {
        val BANKER_FEE_MODE = stringPreferencesKey("rules_bankerFeeMode")
        val BANKER_FEE_VALUE = longPreferencesKey("rules_bankerFeeValue")
        val RAKE_MODE = stringPreferencesKey("rules_rakeMode")
        val RAKE_VALUE = longPreferencesKey("rules_rakeValue")
        val MIN_BUY_IN = longPreferencesKey("rules_minBuyIn")
        val SPLIT_PAYMENT = booleanPreferencesKey("rules_splitPayment")
        val MIN_PLAY_MINUTES = intPreferencesKey("rules_minPlayMinutes")
        val LAST_BANKER_NAME = stringPreferencesKey("lastBankerName")
        val BANKER_PLAYS = booleanPreferencesKey("bankerPlays")
        val THEME_MODE = stringPreferencesKey("themeMode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamicColor")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keepScreenOn")
        val HAPTICS = booleanPreferencesKey("haptics")
        val ODDS_CALCULATOR_VISIBLE = booleanPreferencesKey("oddsCalculatorVisible")
    }

    private companion object {
        val RETRY_DELAY = 500.milliseconds
        const val MAX_BACKOFF_SHIFT = 6L
    }
}
