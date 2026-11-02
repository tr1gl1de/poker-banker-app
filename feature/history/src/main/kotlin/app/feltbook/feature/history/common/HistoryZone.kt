package app.feltbook.feature.history.common

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.ZoneId
import javax.inject.Inject

/**
 * Часовой пояс для группировки по месяцам и границ периодов во ViewModel. В тестах — фиксированный
 * (`object : HistoryZone() { override fun zone() = ZoneOffset.UTC }`).
 */
open class HistoryZone @Inject constructor() {
    open fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Часовой пояс экрана для «20:05» и «18 окт»; в тестах и превью подменяется фиксированным. */
val LocalHistoryTimeZone = staticCompositionLocalOf<ZoneId> { ZoneId.systemDefault() }
