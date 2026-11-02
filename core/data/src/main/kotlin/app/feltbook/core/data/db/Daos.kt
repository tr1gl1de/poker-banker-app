package app.feltbook.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import app.feltbook.core.domain.model.GameStatus
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

// DAO только suspend/Flow — совместимо с Room KMP (ADR 0001). Порядок строк задают запросы, не вызывающий код.
// Upsert (INSERT … ON CONFLICT DO UPDATE) — не REPLACE: REPLACE удалил бы строку игры и каскадом весь журнал.

@Dao
internal interface GameDao {
    @Upsert
    suspend fun upsert(game: GameEntity)

    /** Новые сверху. */
    @Query("SELECT * FROM games ORDER BY createdAt DESC, rowid DESC")
    suspend fun all(): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun byId(id: Uuid): GameEntity?

    @Query("SELECT id FROM games WHERE status IN (:statuses) ORDER BY createdAt DESC, rowid DESC")
    suspend fun idsWithStatus(statuses: List<GameStatus>): List<Uuid>

    /** Каскадом удаляет игроков, журнал и акты (FK ON DELETE CASCADE). */
    @Query("DELETE FROM games WHERE id IN (:ids)")
    suspend fun delete(ids: List<Uuid>): Int

    @Query("DELETE FROM games")
    suspend fun deleteAll(): Int
}

@Dao
internal interface PlayerDao {
    @Upsert
    suspend fun upsert(players: List<PlayerEntity>)

    /** Порядок посадки: rowid сохраняется при upsert (переименование не двигает игрока). */
    @Query("SELECT * FROM players WHERE gameId = :gameId ORDER BY rowid")
    suspend fun byGame(gameId: Uuid): List<PlayerEntity>

    @Query("SELECT * FROM players ORDER BY rowid")
    suspend fun all(): List<PlayerEntity>

    /** Какие из `ids` уже принадлежат другой игре (upsert перенёс бы чужого игрока). */
    @Query("SELECT id FROM players WHERE id IN (:ids) AND gameId != :gameId")
    suspend fun idsInOtherGames(ids: List<Uuid>, gameId: Uuid): List<Uuid>
}

/** Журнал только дописывается: здесь нет и не должно быть `@Update`/`@Delete` (CLAUDE.md, правило 2). */
@Dao
internal interface LedgerDao {
    /** Дубль id или `(gameId, seq)` — SQLiteConstraintException и откат транзакции. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entries: List<LedgerEntryEntity>)

    @Query("SELECT * FROM ledger_entries WHERE gameId = :gameId ORDER BY seq")
    suspend fun byGame(gameId: Uuid): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries ORDER BY gameId, seq")
    suspend fun all(): List<LedgerEntryEntity>

    @Query("SELECT MAX(seq) FROM ledger_entries WHERE gameId = :gameId")
    suspend fun lastSeq(gameId: Uuid): Long?

    @Query("SELECT id FROM ledger_entries WHERE id IN (:ids) AND gameId != :gameId")
    suspend fun idsInOtherGames(ids: List<Uuid>, gameId: Uuid): List<Uuid>
}

/** Акты передачи — только INSERT, как журнал. Порядок — по `ledgerSeq` (часы телефонов могут расходиться). */
@Dao
internal interface HandoverDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(handovers: List<HandoverEntity>)

    @Query("SELECT * FROM handovers WHERE gameId = :gameId ORDER BY ledgerSeq, rowid")
    suspend fun byGame(gameId: Uuid): List<HandoverEntity>

    @Query("SELECT * FROM handovers ORDER BY gameId, ledgerSeq, rowid")
    suspend fun all(): List<HandoverEntity>

    @Query("SELECT id FROM handovers WHERE id IN (:ids) AND gameId != :gameId")
    suspend fun idsInOtherGames(ids: List<Uuid>, gameId: Uuid): List<Uuid>
}

@Dao
internal interface PlayerNameDao {
    /** -1 — имя уже есть (ничего не меняется). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(name: PlayerNameEntity): Long

    /** Имя встретилось раньше, чем считалось (импорт старой игры) — сдвинуть `firstSeenAt` назад. */
    @Query("UPDATE player_names SET firstSeenAt = :at WHERE name = :name AND firstSeenAt > :at")
    suspend fun lowerFirstSeen(name: String, at: Instant)

    @Query("UPDATE player_names SET mergedInto = :into WHERE name = :name")
    suspend fun setMergedInto(name: String, into: String?): Int

    @Query("SELECT * FROM player_names ORDER BY name")
    fun observeAll(): Flow<List<PlayerNameEntity>>

    @Query("SELECT * FROM player_names ORDER BY name")
    suspend fun all(): List<PlayerNameEntity>

    @Query("SELECT COUNT(*) FROM player_names")
    suspend fun count(): Int
}
