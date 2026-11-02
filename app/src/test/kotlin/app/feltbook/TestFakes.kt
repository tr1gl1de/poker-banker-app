package app.feltbook

import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.model.apply
import app.feltbook.core.domain.repository.CommitRejectedException
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.PlayerNameRepository
import app.feltbook.core.domain.repository.SettingsRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Репозиторий в памяти с теми же проверками, что Room: `baseSeq`, одна активная игра. */
class FakeGameRepository(initial: List<GameSnapshot> = emptyList()) : GameRepository {
    val games = MutableStateFlow(initial.associateBy { it.game.id })
    var commitCount = 0
        private set

    /** Вызывается перед каждой записью (имитация гонки: кто-то записал раньше). */
    var beforeCommit: (suspend FakeGameRepository.() -> Unit)? = null

    fun snapshot(id: GameId): GameSnapshot = checkNotNull(games.value[id])

    override fun observeGame(id: GameId): Flow<GameSnapshot?> = games.map { it[id] }.distinctUntilChanged()

    override fun observeActiveGame(): Flow<GameSnapshot?> =
        games.map { map -> map.values.firstOrNull { it.game.status.isActive } }.distinctUntilChanged()

    override fun observeAllGames(): Flow<List<GameSnapshot>> =
        games.map { map -> map.values.sortedByDescending { it.game.createdAt } }

    override suspend fun getGame(id: GameId): GameSnapshot? = games.value[id]

    override suspend fun getActiveGame(): GameSnapshot? = games.value.values.firstOrNull { it.game.status.isActive }

    override suspend fun commit(changes: List<GameChange>) {
        beforeCommit?.let { hook ->
            beforeCommit = null
            hook()
        }
        var map = games.value
        changes.forEach { change ->
            val existing = map[change.gameId]
            val next = if (existing == null) {
                val game = change.game
                    ?: throw CommitRejectedException(CommitRejectedException.Reason.UNKNOWN_GAME, "unknown")
                if (change.baseSeq != 0L) {
                    throw CommitRejectedException(CommitRejectedException.Reason.STALE_SEQ, "stale")
                }
                GameSnapshot(game, change.players, change.entries, change.handovers)
            } else {
                if (change.baseSeq != existing.lastSeq) {
                    throw CommitRejectedException(CommitRejectedException.Reason.STALE_SEQ, "stale")
                }
                existing.apply(change)
            }
            map = map + (change.gameId to next)
        }
        if (map.values.count { it.game.status.isActive } > 1) {
            throw CommitRejectedException(CommitRejectedException.Reason.SECOND_ACTIVE_GAME, "second active")
        }
        games.value = map
        commitCount++
    }

    /** Каждый вызов `deleteGames` (проверка «до истечения окна отмены ничего не удалено»). */
    val deletedIds = mutableListOf<Set<GameId>>()

    override suspend fun deleteGames(ids: Set<GameId>) {
        deletedIds += ids
        games.update { map -> map - ids }
    }

    override suspend fun deleteAllGames() {
        games.value = emptyMap()
    }
}

class FakePlayerNameRepository(initial: List<PlayerName> = emptyList()) : PlayerNameRepository {
    val names = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<PlayerName>> = names

    override suspend fun merge(from: String, into: String) {
        names.update { list -> list.map { if (it.name == from) it.copy(mergedInto = into) else it } }
    }

    override suspend fun unmerge(name: String) {
        names.update { list -> list.map { if (it.name == name) it.copy(mergedInto = null) else it } }
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
    }
}

/** Часы теста: время двигает сам тест. */
class TestClock(var current: Instant = Instant.parse("2026-11-02T17:05:00Z")) : Clock {
    override fun now(): Instant = current
}
