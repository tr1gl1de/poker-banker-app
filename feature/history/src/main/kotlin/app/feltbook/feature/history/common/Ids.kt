package app.feltbook.feature.history.common

import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.PlayerId
import kotlin.uuid.Uuid

/** Id в маршрутах и `UiState` — строки UUID (типобезопасные маршруты сериализуют только примитивы). */
fun GameId.key(): String = value.toString()

fun PlayerId.key(): String = value.toString()

fun OperationId.key(): String = value.toString()

fun String.toGameId(): GameId = GameId(Uuid.parse(this))

fun String.toPlayerId(): PlayerId = PlayerId(Uuid.parse(this))

fun String.toOperationId(): OperationId = OperationId(Uuid.parse(this))

/** Строка — корректный UUID (аргумент маршрута мог прийти извне). */
fun String.isUuid(): Boolean = runCatching { Uuid.parse(this) }.isSuccess
