package app.feltbook.core.domain.engine

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Время и генератор id для одной операции. Движок не читает часы сам — так команды чистые и детерминированные в тестах.
 * @param at обрезается до миллисекунд.
 */
class Stamp(at: Instant, private val newUuid: () -> Uuid = { Uuid.random() }) {
    val at: Instant = Instant.fromEpochMilliseconds(at.toEpochMilliseconds())

    fun nextUuid(): Uuid = newUuid()

    companion object {
        fun now(clock: Clock = Clock.System): Stamp = Stamp(clock.now())
    }
}
