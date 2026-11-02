package app.feltbook.core.domain

import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.EntryId
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.Game
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.PlayerId
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Детерминированные id и время для тестов. */
class TestIds(private var next: Long = 1) {
    fun uuid(): Uuid = Uuid.fromLongs(0x0FE1_7B00_C000_0000L, next++)
}

val T0: Instant = Instant.parse("2026-10-18T19:00:00Z")

/** Часы теста: каждая операция — свой штамп, время двигает тест. */
class TestClock(var now: Instant = T0, private val ids: TestIds = TestIds()) {
    fun stamp(): Stamp = Stamp(now, ids::uuid)

    fun advance(duration: Duration) {
        now += duration
    }

    fun uuid(): Uuid = ids.uuid()
}

fun gameId(n: Long): GameId = GameId(Uuid.fromLongs(1, n))

fun playerId(n: Long): PlayerId = PlayerId(Uuid.fromLongs(2, n))

fun entryId(n: Long): EntryId = EntryId(Uuid.fromLongs(3, n))

fun operationId(n: Long): OperationId = OperationId(Uuid.fromLongs(4, n))

fun emptyGame(
    id: GameId = gameId(1),
    status: GameStatus = GameStatus.RUNNING,
    rules: GameRules = GameRules.DEFAULT,
    banker: String = "Олег",
    bankerPlayerId: PlayerId? = null,
): Game = Game(
    id = id,
    title = "Пятница",
    createdAt = T0,
    closedAt = null,
    status = status,
    currentBankerName = banker,
    bankerPlayerId = bankerPlayerId,
    rules = rules,
)

fun emptySnapshot(game: Game = emptyGame()): GameSnapshot = GameSnapshot(game, emptyList(), emptyList(), emptyList())

fun entry(
    seq: Long,
    type: EntryType,
    player: PlayerId?,
    amount: Long,
    op: Long = seq,
    game: GameId = gameId(1),
    counterparty: PlayerId? = null,
    reverses: EntryId? = null,
    note: String? = null,
    earlyExit: Boolean = false,
    banker: String = "Олег",
    at: Instant = T0,
): LedgerEntry = LedgerEntry(
    id = entryId(seq),
    gameId = game,
    seq = seq,
    operationId = operationId(op),
    type = type,
    playerId = player,
    amount = amount,
    counterpartyPlayerId = counterparty,
    bankerName = banker,
    createdAt = at,
    reversesEntryId = reverses,
    note = note,
    earlyExit = earlyExit,
)
