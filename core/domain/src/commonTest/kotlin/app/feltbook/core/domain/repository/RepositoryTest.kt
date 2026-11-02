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
        val change = GameChange(gameId(1))
        repository.commit(change)
        assertEquals(listOf(listOf(change)), repository.commits)
    }

    @Test
    fun `отчёт импорта`() {
        val report = ImportReport(
            failure = DecodeResult.ChecksumMismatch,
            added = 1,
            extended = 2,
            identical = 3,
            conflicts = listOf(gameId(1) to ImportConflictReason.DIVERGED_LEDGER),
            playerNamesAdded = 4,
        )
        assertEquals(DecodeResult.ChecksumMismatch, report.failure)
        assertEquals(4, report.playerNamesAdded)
    }
}
