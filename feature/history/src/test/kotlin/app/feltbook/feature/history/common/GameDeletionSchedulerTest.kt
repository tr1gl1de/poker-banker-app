package app.feltbook.feature.history.common

import app.feltbook.core.ui.component.UNDO_SNACKBAR_MILLIS
import app.feltbook.feature.history.testing.FakeGameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val games = PreviewHistoryData.games()
    private val first = games[0].game.id
    private val second = games[1].game.id

    @Test
    fun deletesOnlyAfterUndoWindow() = runTest {
        val repository = FakeGameRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        scheduler.schedule(setOf(first))
        assertEquals(setOf(first), scheduler.pending.value?.ids)

        advanceTimeBy(UNDO_SNACKBAR_MILLIS - 1)
        runCurrent()
        assertTrue("nothing deleted inside the window", repository.deletedIds.isEmpty())
        assertTrue(first in repository.games.value)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(setOf(first)), repository.deletedIds)
        assertFalse(first in repository.games.value)
        assertNull(scheduler.pending.value)
    }

    @Test
    fun undoKeepsGamesWithoutTouchingStorage() = runTest {
        val repository = FakeGameRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val token = scheduler.schedule(setOf(first, second))

        assertTrue(scheduler.undo(token))
        advanceTimeBy(UNDO_SNACKBAR_MILLIS * 2)
        runCurrent()

        assertNull(scheduler.pending.value)
        assertTrue(repository.deletedIds.isEmpty())
        assertEquals(games.size, repository.games.value.size)
    }

    @Test
    fun undoAfterWindowIsRejected() = runTest {
        val repository = FakeGameRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val token = scheduler.schedule(setOf(first))
        advanceTimeBy(UNDO_SNACKBAR_MILLIS + 1)
        runCurrent()

        assertFalse(scheduler.undo(token))
        assertFalse(first in repository.games.value)
    }

    @Test
    fun newDeletionCommitsPreviousOne() = runTest {
        val repository = FakeGameRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val old = scheduler.schedule(setOf(first))
        scheduler.schedule(setOf(second))

        assertEquals(listOf(setOf(first)), repository.deletedIds)
        assertEquals(setOf(second), scheduler.pending.value?.ids)
        assertFalse("the replaced deletion is no longer undoable", scheduler.undo(old))
    }

    @Test
    fun extendKeepsUndoOpenLonger() = runTest {
        val repository = FakeGameRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val token = scheduler.schedule(setOf(first))
        scheduler.extend(token, windowMillis = UNDO_SNACKBAR_MILLIS * 3)

        advanceTimeBy(UNDO_SNACKBAR_MILLIS * 2)
        runCurrent()
        assertTrue(repository.deletedIds.isEmpty())
        assertTrue(scheduler.undo(token))
    }

    @Test
    fun commitNowDeletesImmediately() = runTest {
        val repository = FakeGameRepository(games)
        val scheduler = GameDeletionScheduler(repository, backgroundScope)
        val token = scheduler.schedule(setOf(first))
        scheduler.commitNow(token)

        assertEquals(listOf(setOf(first)), repository.deletedIds)
        assertNull(scheduler.pending.value)
    }
}
