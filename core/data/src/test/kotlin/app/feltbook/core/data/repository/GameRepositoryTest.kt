package app.feltbook.core.data.repository

import androidx.room.withTransaction
import app.cash.turbine.test
import app.feltbook.core.data.TableScript
import app.feltbook.core.data.TestClock
import app.feltbook.core.data.assertThrowsSuspend
import app.feltbook.core.data.db.toDomain
import app.feltbook.core.data.gameRepository
import app.feltbook.core.data.inMemoryDatabase
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.NewGameInput
import app.feltbook.core.domain.engine.SettlementChoice
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.repository.CommitRejectedException
import app.feltbook.core.domain.repository.CommitRejectedException.Reason
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameRepositoryTest {
    private val db = inMemoryDatabase()
    private val repository = db.gameRepository()
    private val clock = TestClock()

    @After
    fun close() = db.close()

    private fun table() = TableScript(repository, clock)

    private suspend fun names(): List<PlayerName> = db.playerNames().all().map { it.toDomain() }

    @Test
    fun `сквозная игра — снимок из базы равен снимку в памяти`() = runTest {
        val table = table().closedGameWithDebt()

        val stored = repository.getGame(table.snapshot.game.id)

        assertEquals(table.snapshot, stored)
        assertEquals(GameStatus.CLOSED, stored?.game?.status)
        assertNull(repository.getActiveGame())
    }

    @Test
    fun `повторное нажатие по тому же снимку отклоняется — STALE_SEQ, журнал не меняется`() = runTest {
        val table = table().runningGame()
        val before = table.snapshot
        val first = Commands.buyIn(before, table.player("Влад"), 500, BuyInPayment.PAID, clock.stamp())
        val second = Commands.buyIn(before, table.player("Влад"), 500, BuyInPayment.PAID, clock.stamp())
        repository.commit(listOf(first))

        val error = assertThrowsSuspend<CommitRejectedException> { repository.commit(listOf(second)) }

        assertEquals(Reason.STALE_SEQ, error.reason)
        assertEquals(before.entries + first.entries, repository.getGame(before.game.id)?.entries)
    }

    @Test
    fun `пропуск seq отклоняется`() = runTest {
        val table = table().runningGame()
        val change = Commands.buyIn(table.snapshot, table.player("Аня"), 500, BuyInPayment.PAID, clock.stamp())
        val gap = change.copy(entries = change.entries.map { it.copy(seq = it.seq + 1) })

        val error = assertThrowsSuspend<CommitRejectedException> { repository.commit(listOf(gap)) }

        assertEquals(Reason.STALE_SEQ, error.reason)
        assertEquals(table.snapshot, repository.getGame(table.snapshot.game.id))
    }

    @Test
    fun `вторая активная игра отклоняется и откатывает весь коммит`() = runTest {
        val running = table().runningGame()
        val newGame = Commands.startGame(
            NewGameInput("Ещё", "", "Олег", false, running.snapshot.game.rules),
            activeGame = null,
            stamp = clock.stamp(),
        )
        val buyIn = Commands.buyIn(running.snapshot, running.player("Влад"), 500, BuyInPayment.PAID, clock.stamp())

        val error = assertThrowsSuspend<CommitRejectedException> { repository.commit(listOf(buyIn, newGame)) }

        assertEquals(Reason.SECOND_ACTIVE_GAME, error.reason)
        assertEquals(listOf(running.snapshot), repository.observeAllGamesOnce())
    }

    @Test
    fun `ошибка во втором изменении откатывает первое — нет половины операции`() = runTest {
        val table = table().runningGame()
        val good = Commands.buyIn(table.snapshot, table.player("Влад"), 500, BuyInPayment.PAID, clock.stamp())
        val next = table.snapshot.copy(entries = table.snapshot.entries + good.entries)
        val reusedId = Commands.buyIn(next, table.player("Аня"), 500, BuyInPayment.PAID, clock.stamp()).let { bad ->
            bad.copy(entries = bad.entries.map { it.copy(id = good.entries.first().id) })
        }

        val error = assertThrowsSuspend<CommitRejectedException> { repository.commit(listOf(good, reusedId)) }

        assertEquals(Reason.DUPLICATE_ID, error.reason)

        assertEquals(table.snapshot, repository.getGame(table.snapshot.game.id))
    }

    @Test
    fun `сбой посреди транзакции (как убитый процесс) не оставляет ни одной записи операции`() = runTest {
        val table = table().runningGame()
        val newPlayer = Commands.addPlayer(table.snapshot, "Костя", 1_000, BuyInPayment.DEBT, clock.stamp())
        assertTrue(newPlayer.entries.size > 1)

        assertThrowsSuspend<IllegalStateException> {
            db.withTransaction {
                GameStore(db).write(listOf(newPlayer))
                error("процесс убит посреди commit")
            }
        }

        assertEquals(table.snapshot, repository.getGame(table.snapshot.game.id))
        assertTrue(names().none { it.name == "Костя" })
    }

    @Test
    fun `изменение несуществующей игры без строки игры отклоняется`() = runTest {
        val error = assertThrowsSuspend<CommitRejectedException> {
            repository.commit(listOf(GameChange(GameId(Uuid.fromLongs(7, 7)), baseSeq = 0)))
        }
        assertEquals(Reason.UNKNOWN_GAME, error.reason)
    }

    @Test
    fun `изменение только строки игры по устаревшему снимку отклоняется`() = runTest {
        val table = table().runningGame()
        val stale = table.snapshot
        table.play { s, st -> Commands.buyIn(s, table.player("Влад"), 500, BuyInPayment.PAID, st) }

        val error = assertThrowsSuspend<CommitRejectedException> { repository.commit(listOf(Commands.finish(stale))) }

        assertEquals(Reason.STALE_SEQ, error.reason)
        assertEquals(GameStatus.RUNNING, repository.getGame(stale.game.id)?.game?.status)
        // По свежему снимку то же действие проходит.
        repository.commit(listOf(Commands.finish(table.snapshot)))
        assertEquals(GameStatus.SETTLING, repository.getGame(stale.game.id)?.game?.status)
    }

    @Test
    fun `id игрока или записи другой игры отклоняется до записи — чужая строка не перезаписывается`() = runTest {
        val closed = table().closedGameWithDebt()
        val running = table().runningGame()
        val vlad = closed.snapshot.players.first { it.name == "Влад" }
        val stolenPlayer = Commands.addPlayer(running.snapshot, "Костя", 1_000, BuyInPayment.PAID, clock.stamp())
            .let { change ->
                val newId = change.players.single().id
                change.copy(
                    players = change.players.map { it.copy(id = vlad.id) },
                    entries = change.entries.map { if (it.playerId == newId) it.copy(playerId = vlad.id) else it },
                )
            }
        val stolenEntry = Commands.buyIn(running.snapshot, running.player("Аня"), 500, BuyInPayment.DEBT, clock.stamp())
            .let { change ->
                change.copy(entries = change.entries.mapIndexed { i, e -> e.copy(id = closed.snapshot.entries[i].id) })
            }

        val first = assertThrowsSuspend<CommitRejectedException> { repository.commit(listOf(stolenPlayer)) }
        val second = assertThrowsSuspend<CommitRejectedException> { repository.commit(listOf(stolenEntry)) }

        assertEquals(Reason.FOREIGN_ROW, first.reason)
        assertEquals(Reason.FOREIGN_ROW, second.reason)
        assertEquals(closed.snapshot, repository.getGame(closed.snapshot.game.id))
        assertEquals(running.snapshot, repository.getGame(running.snapshot.game.id))
    }

    @Test
    fun `время с долями миллисекунды — команды через Stamp сохраняются без потерь, сырое отклоняется`() = runTest {
        val subMs = TestClock(app.feltbook.core.data.T0 + 123_456.nanoseconds)
        val table = TableScript(repository, subMs).runningGame()

        assertEquals(table.snapshot, repository.getGame(table.snapshot.game.id))

        val change = Commands.buyIn(table.snapshot, table.player("Влад"), 500, BuyInPayment.PAID, subMs.stamp())
        val raw = change.copy(entries = change.entries.map { it.copy(createdAt = it.createdAt + 1.nanoseconds) })
        assertThrowsSuspend<IllegalArgumentException> { repository.commit(listOf(raw)) }
        assertEquals(table.snapshot, repository.getGame(table.snapshot.game.id))
    }

    @Test
    fun `строки чужой игры в изменении отклоняются`() = runTest {
        val table = table().runningGame()
        val change = Commands.buyIn(table.snapshot, table.player("Влад"), 500, BuyInPayment.PAID, clock.stamp())
        val foreign = change.copy(gameId = GameId(Uuid.fromLongs(7, 7)))

        val error = assertThrowsSuspend<CommitRejectedException> { repository.commit(listOf(foreign)) }

        assertEquals(Reason.FOREIGN_ROW, error.reason)
    }

    @Test
    fun `справочник имён пополняется новыми и переименованными игроками, firstSeenAt не перезаписывается`() = runTest {
        val first = table().closedGameWithDebt()
        val vladSeen = first.snapshot.players.first { it.name == "Влад" }.joinedAt
        clock.advance(60.minutes)
        val second = table().runningGame()
        second.play { s, _ -> Commands.renamePlayer(s, second.player("Аня"), "Анна") }

        val directory = names().associateBy { it.name }
        assertEquals(setOf("Влад", "Дима", "Аня", "Анна"), directory.keys)
        assertEquals(vladSeen, directory.getValue("Влад").firstSeenAt)
        assertTrue(directory.values.all { it.mergedInto == null })
    }

    @Test
    fun `удаление игр каскадом, справочник имён остаётся`() = runTest {
        val closed = table().closedGameWithDebt()
        val running = table().runningGame()

        repository.deleteGames(setOf(closed.snapshot.game.id))

        assertNull(repository.getGame(closed.snapshot.game.id))
        assertEquals(running.snapshot, repository.getGame(running.snapshot.game.id))
        assertTrue(db.ledger().all().all { it.gameId == running.snapshot.game.id.value })
        assertTrue(names().any { it.name == "Дима" })

        repository.deleteAllGames()
        assertTrue(repository.observeAllGamesOnce().isEmpty())
        assertTrue(db.players().all().isEmpty())
        assertTrue(names().isNotEmpty())
    }

    @Test
    fun `observeActiveGame — нет игры, игра, каждое действие, закрытие`() = runTest {
        repository.observeActiveGame().test {
            assertNull(awaitItem())
            val table = table().start()
            assertEquals(table.snapshot, awaitItem())
            table.play { s, st -> Commands.addPlayer(s, "Влад", 1_000, BuyInPayment.PAID, st) }
            assertEquals(table.snapshot, awaitItem())
            table.play { s, _ -> Commands.finish(s) }
            assertEquals(GameStatus.SETTLING, awaitItem()?.game?.status)
            table.play { s, st ->
                Commands.cashOut(s, table.player("Влад"), 1_000, null, SettlementChoice.Full, true, st)
            }
            assertEquals(table.snapshot, awaitItem())
            table.play { s, st -> Commands.close(s, payRake = true, emptySet(), st) }
            assertNull(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `observeGame отдаёт целую операцию за раз и не повторяет равные снимки`() = runTest {
        val table = table().runningGame()
        repository.observeGame(table.snapshot.game.id).test {
            assertEquals(table.snapshot, awaitItem())
            val change = table.play { s, st -> Commands.addPlayer(s, "Костя", 1_000, BuyInPayment.DEBT, st) }
            val item = awaitItem()
            assertEquals(table.snapshot, item)
            assertTrue(item!!.entries.containsAll(change.entries))
            // Изменение имён в справочнике не трогает таблицы игры — снимок не переотправляется.
            db.playerNames().setMergedInto("Костя", "Влад")
            expectNoEvents()
            repository.deleteGames(setOf(table.snapshot.game.id))
            assertNull(awaitItem())
        }
    }

    @Test
    fun `observeAllGames — новые сверху`() = runTest {
        val closed = table().closedGameWithDebt()
        clock.advance(60.minutes)
        val running = table().runningGame()

        repository.observeAllGames().test {
            assertEquals(listOf(running.snapshot, closed.snapshot), awaitItem())
        }
    }

    private suspend fun RoomGameRepository.observeAllGamesOnce() = db.withTransaction { GameStore(db).loadAll() }
}
