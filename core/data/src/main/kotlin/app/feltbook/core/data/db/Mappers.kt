@file:Suppress("TooManyFunctions") // По паре функций «в сущность / в домен» на каждую сущность.

package app.feltbook.core.data.db

import app.feltbook.core.domain.model.EntryId
import app.feltbook.core.domain.model.Game
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.Handover
import app.feltbook.core.domain.model.HandoverId
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.Player
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerName

// Сущности ↔ доменные модели, поля 1:1.

internal fun Game.toEntity(): GameEntity = GameEntity(
    id = id.value,
    title = title,
    createdAt = createdAt,
    closedAt = closedAt,
    status = status,
    currentBankerName = currentBankerName,
    bankerPlayerId = bankerPlayerId?.value,
    rules = rules.toEntity(),
    editedAfterClose = editedAfterClose,
    schemaVersion = schemaVersion,
)

internal fun GameEntity.toDomain(): Game = Game(
    id = GameId(id),
    title = title,
    createdAt = createdAt,
    closedAt = closedAt,
    status = status,
    currentBankerName = currentBankerName,
    bankerPlayerId = bankerPlayerId?.let(::PlayerId),
    rules = rules.toDomain(),
    editedAfterClose = editedAfterClose,
    schemaVersion = schemaVersion,
)

internal fun GameRules.toEntity(): GameRulesEntity = GameRulesEntity(
    bankerFeeMode = bankerFeeMode,
    bankerFeeValue = bankerFeeValue,
    rakeMode = rakeMode,
    rakeValue = rakeValue,
    minBuyIn = minBuyIn,
    splitPayment = splitPayment,
    minPlayMinutes = minPlayMinutes,
)

internal fun GameRulesEntity.toDomain(): GameRules = GameRules(
    bankerFeeMode = bankerFeeMode,
    bankerFeeValue = bankerFeeValue,
    rakeMode = rakeMode,
    rakeValue = rakeValue,
    minBuyIn = minBuyIn,
    splitPayment = splitPayment,
    minPlayMinutes = minPlayMinutes,
)

internal fun Player.toEntity(): PlayerEntity = PlayerEntity(id.value, gameId.value, name, joinedAt)

internal fun PlayerEntity.toDomain(): Player = Player(PlayerId(id), GameId(gameId), name, joinedAt)

internal fun LedgerEntry.toEntity(): LedgerEntryEntity = LedgerEntryEntity(
    id = id.value,
    gameId = gameId.value,
    seq = seq,
    operationId = operationId.value,
    type = type,
    playerId = playerId?.value,
    amount = amount,
    counterpartyPlayerId = counterpartyPlayerId?.value,
    bankerName = bankerName,
    createdAt = createdAt,
    reversesEntryId = reversesEntryId?.value,
    note = note,
    earlyExit = earlyExit,
)

internal fun LedgerEntryEntity.toDomain(): LedgerEntry = LedgerEntry(
    id = EntryId(id),
    gameId = GameId(gameId),
    seq = seq,
    operationId = OperationId(operationId),
    type = type,
    playerId = playerId?.let(::PlayerId),
    amount = amount,
    counterpartyPlayerId = counterpartyPlayerId?.let(::PlayerId),
    bankerName = bankerName,
    createdAt = createdAt,
    reversesEntryId = reversesEntryId?.let(::EntryId),
    note = note,
    earlyExit = earlyExit,
)

internal fun Handover.toEntity(): HandoverEntity =
    HandoverEntity(id.value, gameId.value, fromBanker, toBanker, cashHandedOver, incomeTaken, ledgerSeq, at)

internal fun HandoverEntity.toDomain(): Handover = Handover(
    id = HandoverId(id),
    gameId = GameId(gameId),
    fromBanker = fromBanker,
    toBanker = toBanker,
    cashHandedOver = cashHandedOver,
    incomeTaken = incomeTaken,
    ledgerSeq = ledgerSeq,
    at = at,
)

internal fun PlayerName.toEntity(): PlayerNameEntity = PlayerNameEntity(name, firstSeenAt, mergedInto)

internal fun PlayerNameEntity.toDomain(): PlayerName = PlayerName(name, firstSeenAt, mergedInto)

/** Снимок из строк одной игры; порядок строк уже задан запросами DAO. */
internal fun snapshotOf(
    game: GameEntity,
    players: List<PlayerEntity>,
    entries: List<LedgerEntryEntity>,
    handovers: List<HandoverEntity>,
): GameSnapshot = GameSnapshot(
    game = game.toDomain(),
    players = players.map { it.toDomain() },
    entries = entries.map { it.toDomain() },
    handovers = handovers.map { it.toDomain() },
)
