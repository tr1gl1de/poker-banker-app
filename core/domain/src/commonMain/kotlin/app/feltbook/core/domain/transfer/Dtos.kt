package app.feltbook.core.domain.transfer

import kotlinx.serialization.Serializable

/**
 * Конверт файла `.feltbook` и пакета Nearby (один формат, ADR 0007).
 * `payload` — JSON-строка полезной нагрузки (по `kind`); `sha256` — hex (lowercase) SHA-256 от UTF-8 байтов `payload`.
 * Хеш считается по точной строке, поэтому не зависит от порядка ключей и пробелов при повторной сериализации.
 * @property format "feltbook"
 * @property schemaVersion `FeltbookCodec.SCHEMA_VERSION`
 * @property kind "HANDOVER" | "TEMPLATE" | "BACKUP"
 * @property createdAt epoch ms
 */
@Serializable
data class EnvelopeDto(
    val format: String,
    val schemaVersion: Int,
    val kind: String,
    val createdAt: Long,
    val sha256: String,
    val payload: String,
)

@Serializable
data class HandoverPayloadDto(val game: GameDto, val handoverId: String)

@Serializable
data class TemplatePayloadDto(val fromBanker: String, val rules: GameRulesDto, val playerNames: List<String>)

@Serializable
data class BackupPayloadDto(val exportedAt: Long, val games: List<GameDto>, val playerNames: List<PlayerNameDto>)

/** Время — epoch ms (Long), id — строка UUID, перечисления — имя константы (явный маппинг, не доменные enum). */
@Serializable
data class GameDto(
    val id: String,
    val title: String,
    val createdAt: Long,
    val closedAt: Long?,
    val status: String,
    val currentBankerName: String,
    val bankerPlayerId: String?,
    val rules: GameRulesDto,
    val editedAfterClose: Boolean,
    val schemaVersion: Int,
    val players: List<PlayerDto>,
    val entries: List<LedgerEntryDto>,
    val handovers: List<HandoverDto>,
)

@Serializable
data class GameRulesDto(
    val bankerFeeMode: String,
    val bankerFeeValue: Long,
    val rakeMode: String,
    val rakeValue: Long,
    val minBuyIn: Long,
    val splitPayment: Boolean,
    val minPlayMinutes: Int,
)

@Serializable
data class PlayerDto(val id: String, val name: String, val joinedAt: Long)

@Serializable
data class LedgerEntryDto(
    val id: String,
    val seq: Long,
    val operationId: String,
    val type: String,
    val playerId: String?,
    val amount: Long,
    val counterpartyPlayerId: String?,
    val bankerName: String,
    val createdAt: Long,
    val reversesEntryId: String?,
    val note: String?,
    val earlyExit: Boolean,
)

@Serializable
data class HandoverDto(
    val id: String,
    val fromBanker: String,
    val toBanker: String,
    val cashHandedOver: Long,
    val incomeTaken: Long,
    val ledgerSeq: Long,
    val at: Long,
)

@Serializable
data class PlayerNameDto(val name: String, val firstSeenAt: Long, val mergedInto: String?)
