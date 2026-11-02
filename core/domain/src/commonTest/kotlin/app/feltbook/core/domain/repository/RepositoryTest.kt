package app.feltbook.core.domain.repository

import app.feltbook.core.domain.engine.ImportConflictReason
import app.feltbook.core.domain.gameId
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.transfer.DecodeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class RepositoryTest {
    private class RecordingRepository : GameRepository {
        val commits = mutableListOf<List<GameChange>>()

        override fun observeGame(id: GameId): Flow<GameSnapshot?> = flowOf(null)

        override fun observeActiveGame(): Flow<GameSnapshot?> = flowOf(null)

        override fun observeAllGames(): Flow<List<GameSnapshot>> = flowOf(emptyList())

        override suspend fun getGame(id: GameId): GameSnapshot? = null

        override suspend fun getActiveGame(): GameSnapshot? = null

        override suspend fun commit(changes: List<GameChange>) {
            commits += changes
        }

        override suspend fun deleteGames(ids: Set<GameId>) = Unit

        override suspend fun deleteAllGames() = Unit
    }

    @Test
    fun `одно изменение коммитится списком из одного элемента`() = runTest {
        val repository = RecordingRepository()
        val change = GameChange(gameId(1), baseSeq = 0)
        repository.commit(change)
        assertEquals(listOf(listOf(change)), repository.commits)
    }

    @Test
    fun `отчёт импорта`() {
        val report = ImportReport(
            failure = ImportFailure.Unreadable(DecodeResult.ChecksumMismatch),
            added = 1,
            extended = 2,
            identical = 3,
            conflicts = listOf(gameId(1) to ImportConflictReason.DIVERGED_LEDGER),
            playerNamesAdded = 4,
        )
        assertEquals(ImportFailure.Unreadable(DecodeResult.ChecksumMismatch), report.failure)
        assertEquals(4, report.playerNamesAdded)
        val storage = IllegalStateException("disk")
        assertEquals(storage, (ImportFailure.StorageFailed(storage) as ImportFailure.StorageFailed).cause)
        assertEquals("BACKUP", DecodeResult.WrongKind(expected = "BACKUP", found = "HANDOVER").expected)
    }

    @Test
    fun `отказ коммита несёт причину и исходную ошибку`() {
        val cause = IllegalStateException("constraint")
        val error = CommitRejectedException(CommitRejectedException.Reason.DUPLICATE_ID, "entry 1", cause)
        assertEquals(CommitRejectedException.Reason.DUPLICATE_ID, error.reason)
        assertEquals("entry 1", error.message)
        assertEquals(cause, error.cause)
        assertEquals(null, CommitRejectedException(CommitRejectedException.Reason.STALE_SEQ, "seq").cause)
        assertEquals(5, CommitRejectedException.Reason.entries.size)
    }
}
