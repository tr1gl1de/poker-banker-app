package app.feltbook.feature.game.common

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Тикер таймеров «в игре 47 мин»: текущее время сразу и затем раз в минуту. */
fun minuteTicker(clock: Clock, periodMillis: Long = TICK_MILLIS): Flow<Instant> = flow {
    while (true) {
        emit(clock.now())
        delay(periodMillis)
    }
}

const val TICK_MILLIS: Long = 60_000L

/** Часовой пояс для «20:05» и «18 окт»; в тестах и превью подменяется фиксированным. */
val LocalTimeZone = staticCompositionLocalOf<ZoneId> { ZoneId.systemDefault() }
