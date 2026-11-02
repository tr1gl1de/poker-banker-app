package app.feltbook

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Интерфейс только на русском (ТЗ §9): строки лежат в `values/` без квалификатора, поэтому `plurals` выбирались бы
 * по правилам языка телефона («2 игрока» стало бы «2 игроков» на английском). Контекст с локалью `ru` даёт русские
 * правила множественного числа на любом устройстве.
 */
object RussianLocale {
    val LOCALE: Locale = Locale.forLanguageTag("ru-RU")

    fun wrap(base: Context): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList(LOCALE))
        return base.createConfigurationContext(configuration)
    }
}
