package app.feltbook.core.data.repository

import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import androidx.room.withTransaction
import app.feltbook.core.data.db.FeltbookDatabase
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.repository.GameRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Room-реализация агрегата «игра» (ADR 0006). Снимки собираются в одной транзакции чтения, поэтому Flow никогда
 * не отдаёт половину операции; новое значение — после каждого коммита, затронувшего таблицы игр.
 */
internal class RoomGameRepository @Inject constructor(private val db: FeltbookDatabase, private val store: GameStore) :
    GameRepository {
    override fun observeGame(id: GameId): Flow<GameSnapshot?> = observe { store.load(id) }

    override fun observeActiveGame(): Flow<GameSnapshot?> = observe { loadActive() }

    override fun observeAllGames(): Flow<List<GameSnapshot>> = observe { store.loadAll() }

    override suspend fun getGame(id: GameId): GameSnapshot? = read { store.load(id) }

    override suspend fun getActiveGame(): GameSnapshot? = read { loadActive() }

    override suspend fun commit(changes: List<GameChange>) {
        if (changes.isEmpty()) return
        db.withTransaction { store.write(changes) }
    }

    override suspend fun deleteGames(ids: Set<GameId>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            ids.map { it.value }.chunked(GameStore.MAX_VARIABLES).forEach { db.games().delete(it) }
        }
    }

    override suspend fun deleteAllGames() {
        db.withTransaction { db.games().deleteAll() }
    }

    private suspend fun loadActive(): GameSnapshot? = store.activeGameId()?.let { store.load(it) }

    @Suppress("SpreadOperator") // createFlow принимает только vararg; копия из четырёх строк на подписку.
    private fun <T> observe(load: suspend () -> T): Flow<T> = db.invalidationTracker
        .createFlow(*FeltbookDatabase.GAME_TABLES)
        .conflate()
        .map { read(load) }
        .distinctUntilChanged()

    /** Согласованное чтение нескольких таблиц: одна отложенная (DEFERRED) транзакция на соединении чтения. */
    private suspend fun <T> read(load: suspend () -> T): T =
        db.useReaderConnection { connection -> connection.deferredTransaction { load() } }
}
