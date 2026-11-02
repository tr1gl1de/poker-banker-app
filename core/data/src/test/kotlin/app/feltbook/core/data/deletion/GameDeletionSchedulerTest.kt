package app.feltbook.core.data.deletion

import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.repository.GameRepository
import kotlin.uuid.Uuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Отложенное удаление (roadmap M4.3): до истечения окна в базе ничего не удаляется, отмена — без восстановления. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDeletionSchedulerTest {
    private val first = GameId(Uuid.fromLongs(0L, 1L))
    private val second = GameId(Uuid.fromLongs(0L, 2L))
    private val games = setOf(first, second, GameId(Uuid.fromLongs(0L, 3L)))

    @Test
    fun deletesOnlyAfterUndoWindow() = runTest {
        val repository = IdRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        scheduler.schedule(setOf(first))
        assertEquals(setOf(first), scheduler.pending.value?.ids)

        advanceTimeBy(GameDeletionScheduler.UNDO_WINDOW_MILLIS - 1)
        runCurrent()
        assertTrue("nothing deleted inside the window", repository.deletedIds.isEmpty())
        assertTrue(first in repository.ids)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(setOf(first)), repository.deletedIds)
        assertFalse(first in repository.ids)
        assertNull(scheduler.pending.value)
    }

    @Test
    fun undoKeepsGamesWithoutTouchingStorage() = runTest {
        val repository = IdRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val token = scheduler.schedule(setOf(first, second))

        assertTrue(scheduler.undo(token))
        advanceTimeBy(GameDeletionScheduler.UNDO_WINDOW_MILLIS * 2)
        runCurrent()

        assertNull(scheduler.pending.value)
        assertTrue(repository.deletedIds.isEmpty())
        assertEquals(games.size, repository.ids.size)
    }

    @Test
    fun undoAfterWindowIsRejected() = runTest {
        val repository = IdRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val token = scheduler.schedule(setOf(first))
        advanceTimeBy(GameDeletionScheduler.UNDO_WINDOW_MILLIS + 1)
        runCurrent()

        assertFalse(scheduler.undo(token))
        assertFalse(first in repository.ids)
    }

    @Test
    fun newDeletionCommitsPreviousOne() = runTest {
        val repository = IdRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val old = scheduler.schedule(setOf(first))
        scheduler.schedule(setOf(second))

        assertEquals(listOf(setOf(first)), repository.deletedIds)
        assertEquals(setOf(second), scheduler.pending.value?.ids)
        assertFalse("the replaced deletion is no longer undoable", scheduler.undo(old))
    }

    @Test
    fun extendKeepsUndoOpenLonger() = runTest {
        val repository = IdRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val token = scheduler.schedule(setOf(first))
        scheduler.extend(token, windowMillis = GameDeletionScheduler.UNDO_WINDOW_MILLIS * 3)

        advanceTimeBy(GameDeletionScheduler.UNDO_WINDOW_MILLIS * 2)
        runCurrent()
        assertTrue(repository.deletedIds.isEmpty())
        assertTrue(scheduler.undo(token))
    }

    @Test
    fun failedDeleteIsReportedAndGamesReappear() = runTest {
        val repository = IdRepository(games, failDeletes = true)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val failures = mutableListOf<Set<GameId>>()
        backgroundScope.launch { scheduler.failures.collect { failures += it } }
        runCurrent()
        scheduler.schedule(setOf(first))
        advanceTimeBy(GameDeletionScheduler.UNDO_WINDOW_MILLIS + 1)
        runCurrent()

        assertEquals(listOf(setOf(first)), failures)
        assertNull(scheduler.pending.value)
        assertTrue(first in repository.ids)
    }

    @Test
    fun flushDeletesPendingBeforeBackup() = runTest {
        val repository = IdRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        scheduler.schedule(setOf(second))
        scheduler.flush()

        assertEquals(listOf(setOf(second)), repository.deletedIds)
        assertNull(scheduler.pending.value)
    }

    @Test
    fun commitNowDeletesImmediately() = runTest {
        val repository = IdRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val token = scheduler.schedule(setOf(first))
        scheduler.commitNow(token)

        assertEquals(listOf(setOf(first)), repository.deletedIds)
        assertNull(scheduler.pending.value)
    }
}

/** Хранит только id игр: планировщику нужны лишь `deleteGames`. */
private class IdRepository(initial: Set<GameId>, var failDeletes: Boolean = false) : GameRepository {
    val ids = initial.toMutableSet()
    val deletedIds = mutableListOf<Set<GameId>>()

    override fun observeGame(id: GameId): Flow<GameSnapshot?> = emptyFlow()

    override fun observeActiveGame(): Flow<GameSnapshot?> = emptyFlow()

    override fun observeAllGames(): Flow<List<GameSnapshot>> = emptyFlow()

    override suspend fun getGame(id: GameId): GameSnapshot? = null

    override suspend fun getActiveGame(): GameSnapshot? = null

    override suspend fun commit(changes: List<GameChange>) = Unit

    override suspend fun deleteGames(ids: Set<GameId>) {
        check(!failDeletes) { "disk full" }
        deletedIds += ids
        this.ids -= ids
    }

    override suspend fun deleteAllGames() = ids.clear()
}
