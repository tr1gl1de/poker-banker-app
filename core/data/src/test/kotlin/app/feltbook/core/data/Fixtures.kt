package app.feltbook.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.feltbook.core.data.db.FeltbookDatabase
import app.feltbook.core.data.repository.GameStore
import app.feltbook.core.data.repository.RoomGameRepository
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.CloseConfirmation
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.NewGameInput
import app.feltbook.core.domain.engine.SettlementChoice
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.apply
import app.feltbook.core.domain.repository.GameRepository
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

val T0: Instant = Instant.parse("2026-10-18T19:00:00Z")

/** Детерминированные id и время; каждый [stamp] сдвигает часы на минуту. */
class TestClock(var now: Instant = T0) : Clock {
    private var nextId = 1L

    override fun now(): Instant = now

    fun uuid(): Uuid = Uuid.fromLongs(0x0FE1_7B00_DA7AL, nextId++)

    fun stamp(): Stamp {
        now += 1.minutes
        return Stamp(now, ::uuid)
    }

    fun advance(duration: Duration) {
        now += duration
    }
}

internal fun inMemoryDatabase(): FeltbookDatabase = Room
    .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FeltbookDatabase::class.java)
    .allowMainThreadQueries()
    .build()

internal fun FeltbookDatabase.gameRepository(): RoomGameRepository = RoomGameRepository(this, GameStore(this))

/**
 * Игра в памяти и в репозитории одновременно: каждое изменение коммитится и применяется к снимку, так что тест
 * сравнивает прочитанное из базы с ожидаемым снимком.
 */
class TableScript(private val repository: GameRepository, val clock: TestClock) {
    lateinit var snapshot: GameSnapshot
        private set

    suspend fun start(banker: String = "Олег", title: String = "Пятница", bankerPlays: Boolean = false): TableScript {
        val change = Commands.startGame(
            NewGameInput(title, "", banker, bankerPlays, GameRules.DEFAULT),
            activeGame = repository.getActiveGame()?.game,
            stamp = clock.stamp(),
        )
        repository.commit(listOf(change))
        snapshot = GameSnapshot(change.game!!, change.players, change.entries, change.handovers)
        return this
    }

    /** Продолжить игру, уже лежащую в репозитории (например, после импорта). */
    fun resume(existing: GameSnapshot): TableScript {
        snapshot = existing
        return this
    }

    suspend fun play(build: (GameSnapshot, Stamp) -> GameChange): GameChange {
        val change = build(snapshot, clock.stamp())
        repository.commit(listOf(change))
        snapshot = snapshot.apply(change)
        return change
    }

    fun player(name: String) = snapshot.players.first { it.name == name }.id

    /** Влад платит сразу и выходит в плюсе, Дима берёт в долг и уходит с долгом; игра закрыта. */
    suspend fun closedGameWithDebt(): TableScript {
        start()
        play { s, st -> Commands.addPlayer(s, "Влад", 1_000, BuyInPayment.PAID, st) }
        play { s, st -> Commands.addPlayer(s, "Дима", 1_000, BuyInPayment.DEBT, st) }
        play { s, st -> Commands.buyIn(s, player("Дима"), 500, BuyInPayment.DEBT, st) }
        clock.advance(2.hours)
        play { s, st -> Commands.cashOut(s, player("Влад"), 2_000, null, SettlementChoice.Full, false, st) }
        play { s, st -> Commands.cashOut(s, player("Дима"), 500, null, SettlementChoice.None, false, st) }
        play { s, _ -> Commands.finish(s) }
        play { s, st -> Commands.close(s, payRake = true, CloseConfirmation.entries.toSet(), st) }
        return this
    }

    /** Идущая игра с двумя игроками. */
    suspend fun runningGame(banker: String = "Олег"): TableScript {
        start(banker = banker, title = "Суббота")
        play { s, st -> Commands.addPlayer(s, "Влад", 1_000, BuyInPayment.PAID, st) }
        play { s, st -> Commands.addPlayer(s, "Аня", 2_000, BuyInPayment.DEBT, st) }
        return this
    }
}

/** `assertThrows` для suspend-кода внутри `runTest`. */
suspend inline fun <reified T : Throwable> assertThrowsSuspend(block: suspend () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw AssertionError("expected ${T::class.simpleName}, got $error", error)
    }
    throw AssertionError("expected ${T::class.simpleName}, nothing was thrown")
}
