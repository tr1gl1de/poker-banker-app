package app.feltbook.core.data.repository

import app.feltbook.core.data.db.FeltbookDatabase
import app.feltbook.core.data.db.PlayerNameEntity
import app.feltbook.core.data.db.snapshotOf
import app.feltbook.core.data.db.toEntity
import app.feltbook.core.data.repository.CommitRejectedException.Reason
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.Player
import javax.inject.Inject

/**
 * Чтение снимков и запись изменений агрегата «игра». Методы не открывают транзакцию сами: их вызывают внутри
 * `withTransaction` ([RoomGameRepository.commit], импорт резервной копии), чтобы вся операция была атомарной.
 */
internal class GameStore @Inject constructor(private val db: FeltbookDatabase) {
    suspend fun load(id: GameId): GameSnapshot? {
        val game = db.games().byId(id.value) ?: return null
        return snapshotOf(
            game = game,
            players = db.players().byGame(id.value),
            entries = db.ledger().byGame(id.value),
            handovers = db.handovers().byGame(id.value),
        )
    }

    /** Все игры, новые сверху. */
    suspend fun loadAll(): List<GameSnapshot> {
        val games = db.games().all()
        val players = db.players().all().groupBy { it.gameId }
        val entries = db.ledger().all().groupBy { it.gameId }
        val handovers = db.handovers().all().groupBy { it.gameId }
        return games.map { game ->
            snapshotOf(
                game = game,
                players = players[game.id].orEmpty(),
                entries = entries[game.id].orEmpty(),
                handovers = handovers[game.id].orEmpty(),
            )
        }
    }

    suspend fun activeGameId(): GameId? = activeIds().firstOrNull()

    /**
     * Пишет изменения по порядку и проверяет инварианты; нарушение — [CommitRejectedException] (или
     * SQLiteConstraintException от уникальных индексов), вызывающая транзакция откатывается целиком.
     */
    suspend fun write(changes: List<GameChange>) {
        changes.forEach { write(it) }
        val active = activeIds()
        if (active.size > 1) {
            reject(Reason.SECOND_ACTIVE_GAME, "active games after commit: ${active.joinToString()}")
        }
    }

    private suspend fun write(change: GameChange) {
        val gameId = change.gameId
        checkOwnership(change)
        val game = change.game
        if (game != null) {
            db.games().upsert(game.toEntity())
        } else if (db.games().byId(gameId.value) == null) {
            reject(Reason.UNKNOWN_GAME, "game $gameId does not exist")
        }
        if (change.players.isNotEmpty()) {
            db.players().upsert(change.players.map { it.toEntity() })
            change.players.forEach { rememberName(it) }
        }
        if (change.entries.isNotEmpty()) {
            val expected = (db.ledger().lastSeq(gameId.value) ?: 0L) + 1L
            change.entries.forEachIndexed { index, entry ->
                if (entry.seq != expected + index) {
                    reject(Reason.STALE_SEQ, "game $gameId: seq ${entry.seq}, expected ${expected + index}")
                }
            }
            db.ledger().insert(change.entries.map { it.toEntity() })
        }
        if (change.handovers.isNotEmpty()) {
            db.handovers().insert(change.handovers.map { it.toEntity() })
        }
    }

    private fun checkOwnership(change: GameChange) {
        val id = change.gameId
        val foreign = buildList {
            change.game?.takeIf { it.id != id }?.let { add("game ${it.id}") }
            change.players.filter { it.gameId != id }.forEach { add("player ${it.id}") }
            change.entries.filter { it.gameId != id }.forEach { add("entry ${it.id}") }
            change.handovers.filter { it.gameId != id }.forEach { add("handover ${it.id}") }
        }
        if (foreign.isNotEmpty()) reject(Reason.FOREIGN_ROW, "change for $id contains ${foreign.joinToString()}")
    }

    /** Справочник имён: новое имя — `firstSeenAt = joinedAt`; известное раньше — не трогаем (кроме сдвига назад). */
    private suspend fun rememberName(player: Player) {
        val name = PlayerNames.normalize(player.name)
        if (name.isEmpty()) return
        val dao = db.playerNames()
        if (dao.insertIfAbsent(PlayerNameEntity(name, player.joinedAt, mergedInto = null)) == -1L) {
            dao.lowerFirstSeen(name, player.joinedAt)
        }
    }

    private suspend fun activeIds(): List<GameId> = db.games().idsWithStatus(ACTIVE).map(::GameId)

    private fun reject(reason: Reason, message: String): Nothing = throw CommitRejectedException(reason, message)

    private companion object {
        val ACTIVE: List<GameStatus> = GameStatus.entries.filter { it.isActive }
    }
}
