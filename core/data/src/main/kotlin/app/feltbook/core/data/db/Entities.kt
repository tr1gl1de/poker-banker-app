package app.feltbook.core.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.RakeMode
import kotlin.time.Instant
import kotlin.uuid.Uuid

// Схема БД v1. Сущности не выходят за пределы :core:data (internal), наружу — доменные модели (Mappers.kt).
// Id — TEXT (UUID), время — INTEGER (epoch ms), перечисления — TEXT (имя константы), см. Converters.
// Все дочерние таблицы — FK на games ON DELETE CASCADE: удаление игры целиком — одним DELETE.

internal const val TABLE_GAMES = "games"
internal const val TABLE_PLAYERS = "players"
internal const val TABLE_LEDGER = "ledger_entries"
internal const val TABLE_HANDOVERS = "handovers"
internal const val TABLE_PLAYER_NAMES = "player_names"

@Entity(
    tableName = TABLE_GAMES,
    indices = [Index("createdAt"), Index("status")],
)
internal data class GameEntity(
    @PrimaryKey val id: Uuid,
    val title: String,
    val createdAt: Instant,
    val closedAt: Instant?,
    val status: GameStatus,
    val currentBankerName: String,
    val bankerPlayerId: Uuid?,
    @Embedded(prefix = "rules_") val rules: GameRulesEntity,
    val editedAfterClose: Boolean,
    val schemaVersion: Int,
)

/** Слепок правил, встроенный в строку игры (ТЗ §8: `@Embedded`). */
internal data class GameRulesEntity(
    val bankerFeeMode: BankerFeeMode,
    val bankerFeeValue: Long,
    val rakeMode: RakeMode,
    val rakeValue: Long,
    val minBuyIn: Long,
    val splitPayment: Boolean,
    val minPlayMinutes: Int,
)

@Entity(
    tableName = TABLE_PLAYERS,
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId"), Index("joinedAt")],
)
internal data class PlayerEntity(@PrimaryKey val id: Uuid, val gameId: Uuid, val name: String, val joinedAt: Instant)

/**
 * Журнал: только INSERT (LedgerDao). Уникальный `(gameId, seq)` — защита от двойного нажатия и устаревших
 * изменений.
 */
@Entity(
    tableName = TABLE_LEDGER,
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // Покрывает и поиск по gameId (ведущая колонка), отдельный индекс gameId не нужен.
        Index(value = ["gameId", "seq"], unique = true),
        Index("playerId"),
        Index("createdAt"),
    ],
)
internal data class LedgerEntryEntity(
    @PrimaryKey val id: Uuid,
    val gameId: Uuid,
    val seq: Long,
    val operationId: Uuid,
    val type: EntryType,
    val playerId: Uuid?,
    val amount: Long,
    val counterpartyPlayerId: Uuid?,
    val bankerName: String,
    val createdAt: Instant,
    val reversesEntryId: Uuid?,
    val note: String?,
    val earlyExit: Boolean,
)

@Entity(
    tableName = TABLE_HANDOVERS,
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["gameId", "ledgerSeq"])],
)
internal data class HandoverEntity(
    @PrimaryKey val id: Uuid,
    val gameId: Uuid,
    val fromBanker: String,
    val toBanker: String,
    val cashHandedOver: Long,
    val incomeTaken: Long,
    val ledgerSeq: Long,
    val at: Instant,
)

/** Справочник имён; не зависит от игр (удаление игр его не трогает). */
@Entity(tableName = TABLE_PLAYER_NAMES, indices = [Index("firstSeenAt")])
internal data class PlayerNameEntity(@PrimaryKey val name: String, val firstSeenAt: Instant, val mergedInto: String?)
