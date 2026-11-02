package app.feltbook.feature.settlement

import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.apply
import app.feltbook.core.domain.repository.CommitRejectedException
import app.feltbook.core.domain.repository.GameRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Репозиторий в памяти с проверкой `baseSeq` (как Room). */
class FakeGameRepository(initial: List<GameSnapshot>) : GameRepository {
    val games = MutableStateFlow(initial.associateBy { it.game.id })

    fun snapshot(id: GameId): GameSnapshot = checkNotNull(games.value[id])

    override fun observeGame(id: GameId): Flow<GameSnapshot?> = games.map { it[id] }.distinctUntilChanged()

    override fun observeActiveGame(): Flow<GameSnapshot?> =
        games.map { map -> map.values.firstOrNull { it.game.status.isActive } }

    override fun observeAllGames(): Flow<List<GameSnapshot>> = games.map { it.values.toList() }

    override suspend fun getGame(id: GameId): GameSnapshot? = games.value[id]

    override suspend fun getActiveGame(): GameSnapshot? = games.value.values.firstOrNull { it.game.status.isActive }

    override suspend fun commit(changes: List<GameChange>) {
        var map = games.value
        changes.forEach { change ->
            val existing = checkNotNull(map[change.gameId])
            if (change.baseSeq != existing.lastSeq) {
                throw CommitRejectedException(CommitRejectedException.Reason.STALE_SEQ, "stale")
            }
            map = map + (change.gameId to existing.apply(change))
        }
        games.value = map
    }

    override suspend fun deleteGames(ids: Set<GameId>) {
        games.value = games.value - ids
    }

    override suspend fun deleteAllGames() {
        games.value = emptyMap()
    }
}

class TestClock(var current: Instant = Instant.parse("2026-10-18T20:40:00Z")) : Clock {
    override fun now(): Instant = current
}
