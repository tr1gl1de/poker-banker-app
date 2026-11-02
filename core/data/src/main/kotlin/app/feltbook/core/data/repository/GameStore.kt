package app.feltbook.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import app.feltbook.core.data.db.FeltbookDatabase
import app.feltbook.core.data.db.PlayerNameEntity
import app.feltbook.core.data.db.snapshotOf
import app.feltbook.core.data.db.toEntity
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.Player
import app.feltbook.core.domain.repository.CommitRejectedException
import app.feltbook.core.domain.repository.CommitRejectedException.Reason
import javax.inject.Inject
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Чтение снимков и запись изменений агрегата «игра». Методы не открывают транзакцию сами: их вызывают внутри
 * транзакции ([RoomGameRepository], импорт резервной копии), чтобы вся операция была атомарной.
 * Сообщения исключений — только id и `seq`, без имён и сумм.
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

    /** Id игроков, записей и актов снимка, которые в базе принадлежат другим играм. */
    suspend fun foreignIds(snapshot: GameSnapshot): Set<Uuid> = foreignIds(
        gameId = snapshot.game.id,
        players = snapshot.players.map { it.id.value },
        entries = snapshot.entries.map { it.id.value },
        handovers = snapshot.handovers.map { it.id.value },
    )

    private suspend fun foreignIds(
        gameId: GameId,
        players: List<Uuid>,
        entries: List<Uuid>,
        handovers: List<Uuid>,
    ): Set<Uuid> = buildSet {
        val game = gameId.value
        players.chunked(MAX_VARIABLES).forEach { addAll(db.players().idsInOtherGames(it, game)) }
        entries.chunked(MAX_VARIABLES).forEach { addAll(db.ledger().idsInOtherGames(it, game)) }
        handovers.chunked(MAX_VARIABLES).forEach { addAll(db.handovers().idsInOtherGames(it, game)) }
    }

    /**
     * Пишет изменения по порядку и проверяет инварианты; нарушение — [CommitRejectedException] (время не в целых
     * миллисекундах — IllegalArgumentException), вызывающая транзакция откатывается целиком.
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
        checkInstants(change)
        checkOwnership(change)
        val lastSeq = db.ledger().lastSeq(gameId.value) ?: 0L
        if (lastSeq != change.baseSeq) {
            reject(Reason.STALE_SEQ, "game $gameId: change built at seq ${change.baseSeq}, ledger is at $lastSeq")
        }
        change.entries.forEachIndexed { index, entry ->
            if (entry.seq != lastSeq + 1 + index) {
                reject(Reason.STALE_SEQ, "game $gameId: seq ${entry.seq}, expected ${lastSeq + 1 + index}")
            }
        }
        val game = change.game
        if (game != null) {
            db.games().upsert(game.toEntity())
        } else if (db.games().byId(gameId.value) == null) {
            reject(Reason.UNKNOWN_GAME, "game $gameId does not exist")
        }
        try {
            if (change.players.isNotEmpty()) db.players().upsert(change.players.map { it.toEntity() })
            if (change.entries.isNotEmpty()) db.ledger().insert(change.entries.map { it.toEntity() })
            if (change.handovers.isNotEmpty()) db.handovers().insert(change.handovers.map { it.toEntity() })
        } catch (duplicate: SQLiteConstraintException) {
            throw CommitRejectedException(Reason.DUPLICATE_ID, "game $gameId: duplicate row id", duplicate)
        }
        change.players.forEach { rememberName(it) }
    }

    /** Строки изменения — этой игры, а их id не заняты строками других игр. */
    private suspend fun checkOwnership(change: GameChange) {
        val id = change.gameId
        val foreign = buildList {
            change.game?.takeIf { it.id != id }?.let { add("game ${it.id}") }
            change.players.filter { it.gameId != id }.forEach { add("player ${it.id}") }
            change.entries.filter { it.gameId != id }.forEach { add("entry ${it.id}") }
            change.handovers.filter { it.gameId != id }.forEach { add("handover ${it.id}") }
        }
        if (foreign.isNotEmpty()) reject(Reason.FOREIGN_ROW, "change for $id contains ${foreign.joinToString()}")
        val taken = foreignIds(
            gameId = id,
            players = change.players.map { it.id.value },
            entries = change.entries.map { it.id.value },
            handovers = change.handovers.map { it.id.value },
        )
        if (taken.isNotEmpty()) reject(Reason.FOREIGN_ROW, "ids of other games in change for $id: $taken")
    }

    /** Время хранится в мс; доменный `Stamp` уже обрезает, здесь — страховка от потери точности при записи. */
    private fun checkInstants(change: GameChange) {
        val instants = buildList {
            change.game?.let {
                add(it.createdAt)
                it.closedAt?.let(::add)
            }
            change.players.forEach { add(it.joinedAt) }
            change.entries.forEach { add(it.createdAt) }
            change.handovers.forEach { add(it.at) }
        }
        require(instants.all { it == Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) }) {
            "game ${change.gameId}: instants must be whole milliseconds"
        }
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

    companion object {
        /** Предел параметров запроса SQLite на старых Android — 999. */
        const val MAX_VARIABLES = 900

        private val ACTIVE: List<GameStatus> = GameStatus.entries.filter { it.isActive }
    }
}
