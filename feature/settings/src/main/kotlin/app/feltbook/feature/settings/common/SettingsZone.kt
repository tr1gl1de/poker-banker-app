package app.feltbook.feature.settings.common

import java.time.ZoneId
import javax.inject.Inject

/** Часовой пояс для имени файла копии `feltbook-2026-11-02.feltbook`; в тестах — фиксированный. */
open class SettingsZone @Inject constructor() {
    open fun zone(): ZoneId = ZoneId.systemDefault()
}
