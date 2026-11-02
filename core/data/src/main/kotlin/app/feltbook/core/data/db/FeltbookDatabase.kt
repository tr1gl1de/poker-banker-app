package app.feltbook.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * База Feltbook. Схема каждой версии экспортируется в `core/data/schemas/` (в git); новая версия = миграция + тест
 * миграции (MigrationTestHelper).
 */
@Database(
    entities = [
        GameEntity::class,
        PlayerEntity::class,
        LedgerEntryEntity::class,
        HandoverEntity::class,
        PlayerNameEntity::class,
    ],
    version = FeltbookDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
internal abstract class FeltbookDatabase : RoomDatabase() {
    abstract fun games(): GameDao

    abstract fun players(): PlayerDao

    abstract fun ledger(): LedgerDao

    abstract fun handovers(): HandoverDao

    abstract fun playerNames(): PlayerNameDao

    companion object {
        const val VERSION: Int = 1
        const val NAME: String = "feltbook.db"

        /** Таблицы агрегата «игра» — их изменение пересобирает снимки. */
        val GAME_TABLES: Array<String> = arrayOf(TABLE_GAMES, TABLE_PLAYERS, TABLE_LEDGER, TABLE_HANDOVERS)
    }
}
