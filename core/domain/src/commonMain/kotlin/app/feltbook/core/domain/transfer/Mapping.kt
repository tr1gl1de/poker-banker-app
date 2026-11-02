package app.feltbook.core.domain.transfer

import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.EntryId
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.Game
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.Handover
import app.feltbook.core.domain.model.HandoverId
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.Player
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.model.RakeMode
import kotlin.time.Instant
import kotlin.uuid.Uuid

// Маппинг GameSnapshot ↔ GameDto: поля 1:1, CREATED при чтении → RUNNING (m1-domain.md §6).
// Ошибки разбора (битый UUID, неизвестный enum) — IllegalArgumentException, кодек превращает их в Malformed.

internal fun GameSnapshot.toDto(): GameDto = GameDto(
    id = game.id.value.toString(),
    title = game.title,
    createdAt = game.createdAt.toEpochMilliseconds(),
    closedAt = game.closedAt?.toEpochMilliseconds(),
    status = game.status.name,
    currentBankerName = game.currentBankerName,
    bankerPlayerId = game.bankerPlayerId?.value?.toString(),
    rules = game.rules.toDto(),
    editedAfterClose = game.editedAfterClose,
    schemaVersion = game.schemaVersion,
    players = players.map { PlayerDto(it.id.value.toString(), it.name, it.joinedAt.toEpochMilliseconds()) },
    entries = entries.map { it.toDto() },
    handovers = handovers.map { it.toDto() },
)

internal fun GameDto.toDomain(): GameSnapshot {
    val gameId = GameId(uuid(id))
    val status = enumByName<GameStatus>(status).let { if (it == GameStatus.CREATED) GameStatus.RUNNING else it }
    val game = Game(
        id = gameId,
        title = title,
        createdAt = instant(createdAt),
        closedAt = closedAt?.let(::instant),
        status = status,
        currentBankerName = currentBankerName,
        bankerPlayerId = bankerPlayerId?.let { PlayerId(uuid(it)) },
        rules = rules.toDomain(),
        editedAfterClose = editedAfterClose,
        schemaVersion = schemaVersion,
    )
    return GameSnapshot(
        game = game,
        players = players.map { Player(PlayerId(uuid(it.id)), gameId, it.name, instant(it.joinedAt)) },
        entries = entries.map { it.toDomain(gameId) },
        handovers = handovers.map { it.toDomain(gameId) },
    )
}

internal fun GameRules.toDto(): GameRulesDto = GameRulesDto(
    bankerFeeMode = bankerFeeMode.name,
    bankerFeeValue = bankerFeeValue,
    rakeMode = rakeMode.name,
    rakeValue = rakeValue,
    minBuyIn = minBuyIn,
    splitPayment = splitPayment,
    minPlayMinutes = minPlayMinutes,
)

internal fun GameRulesDto.toDomain(): GameRules = GameRules(
    bankerFeeMode = enumByName<BankerFeeMode>(bankerFeeMode),
    bankerFeeValue = bankerFeeValue,
    rakeMode = enumByName<RakeMode>(rakeMode),
    rakeValue = rakeValue,
    minBuyIn = minBuyIn,
    splitPayment = splitPayment,
    minPlayMinutes = minPlayMinutes,
)

internal fun PlayerName.toDto(): PlayerNameDto = PlayerNameDto(name, firstSeenAt.toEpochMilliseconds(), mergedInto)

internal fun PlayerNameDto.toDomain(): PlayerName = PlayerName(name, instant(firstSeenAt), mergedInto)

private fun LedgerEntry.toDto(): LedgerEntryDto = LedgerEntryDto(
    id = id.value.toString(),
    seq = seq,
    operationId = operationId.value.toString(),
    type = type.name,
    playerId = playerId?.value?.toString(),
    amount = amount,
    counterpartyPlayerId = counterpartyPlayerId?.value?.toString(),
    bankerName = bankerName,
    createdAt = createdAt.toEpochMilliseconds(),
    reversesEntryId = reversesEntryId?.value?.toString(),
    note = note,
    earlyExit = earlyExit,
)

private fun LedgerEntryDto.toDomain(gameId: GameId): LedgerEntry = LedgerEntry(
    id = EntryId(uuid(id)),
    gameId = gameId,
    seq = seq,
    operationId = OperationId(uuid(operationId)),
    type = enumByName<EntryType>(type),
    playerId = playerId?.let { PlayerId(uuid(it)) },
    amount = amount,
    counterpartyPlayerId = counterpartyPlayerId?.let { PlayerId(uuid(it)) },
    bankerName = bankerName,
    createdAt = instant(createdAt),
    reversesEntryId = reversesEntryId?.let { EntryId(uuid(it)) },
    note = note,
    earlyExit = earlyExit,
)

private fun Handover.toDto(): HandoverDto = HandoverDto(
    id = id.value.toString(),
    fromBanker = fromBanker,
    toBanker = toBanker,
    cashHandedOver = cashHandedOver,
    incomeTaken = incomeTaken,
    ledgerSeq = ledgerSeq,
    at = at.toEpochMilliseconds(),
)

private fun HandoverDto.toDomain(gameId: GameId): Handover = Handover(
    id = HandoverId(uuid(id)),
    gameId = gameId,
    fromBanker = fromBanker,
    toBanker = toBanker,
    cashHandedOver = cashHandedOver,
    incomeTaken = incomeTaken,
    ledgerSeq = ledgerSeq,
    at = instant(at),
)

internal fun uuid(text: String): Uuid = Uuid.parse(text)

internal fun instant(epochMs: Long): Instant = Instant.fromEpochMilliseconds(epochMs)

private inline fun <reified T : Enum<T>> enumByName(name: String): T = enumValues<T>().firstOrNull { it.name == name }
    ?: throw IllegalArgumentException("unknown ${T::class.simpleName} '$name'")
