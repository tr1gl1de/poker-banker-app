package app.feltbook.core.domain.model

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор игры. В БД и в пакете — строка UUID (`Uuid.toString()`). */
@JvmInline
value class GameId(val value: Uuid)

/**
 * Идентификатор игрока внутри игры. Один человек в двух играх — два разных `PlayerId`, связь — по имени (`PlayerName`).
 */
@JvmInline
value class PlayerId(val value: Uuid)

/** Идентификатор записи журнала. */
@JvmInline
value class EntryId(val value: Uuid)

/**
 * Идентификатор пользовательской операции. Все записи, созданные одним действием банкира
 * (закуп = фишки + рейк + сбор + оплата), имеют общий `OperationId`; отмена в интерфейсе — по операции.
 */
@JvmInline
value class OperationId(val value: Uuid)

/** Идентификатор акта передачи кассы. */
@JvmInline
value class HandoverId(val value: Uuid)
