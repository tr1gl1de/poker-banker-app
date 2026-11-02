package app.feltbook.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.feltbook.core.data.T0
import app.feltbook.core.data.gameRepository
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameStatus
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Инфраструктура тестов миграций (основа M2.6): база по экспортированной схеме, проверка схемы и открытие Room
 * поверх файла. Новая версия схемы добавляет сюда тест `N-1 → N` (`runMigrationsAndValidate(name, N, true, …)`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FeltbookDatabase::class.java,
    )

    private val gameId = Uuid.fromLongs(1, 1)

    @Test
    fun `схема 1 из schemas совпадает с сущностями, Room читает созданную по ней базу`() = runTest {
        helper.createDatabase(NAME, 1).use { db ->
            val rules = GameRules.DEFAULT
            db.execSQL(
                "INSERT INTO games (id, title, createdAt, closedAt, status, currentBankerName, bankerPlayerId, " +
                    "editedAfterClose, schemaVersion, rules_bankerFeeMode, rules_bankerFeeValue, rules_rakeMode, " +
                    "rules_rakeValue, rules_minBuyIn, rules_splitPayment, rules_minPlayMinutes) " +
                    "VALUES (?, 'Пятница', ?, NULL, 'RUNNING', 'Олег', NULL, 0, 1, ?, ?, ?, ?, ?, 0, ?)",
                arrayOf<Any>(
                    gameId.toString(),
                    T0.toEpochMilliseconds(),
                    rules.bankerFeeMode.name,
                    rules.bankerFeeValue,
                    rules.rakeMode.name,
                    rules.rakeValue,
                    rules.minBuyIn,
                    rules.minPlayMinutes,
                ),
            )
        }
        helper.runMigrationsAndValidate(NAME, FeltbookDatabase.VERSION, true, *Migrations.ALL).close()

        val room = Room
            .databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FeltbookDatabase::class.java, NAME)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val game = room.gameRepository().getGame(GameId(gameId))?.game
            assertEquals(GameStatus.RUNNING, game?.status)
            assertEquals(GameRules.DEFAULT, game?.rules)
            assertEquals(T0, game?.createdAt)
        } finally {
            room.close()
        }
    }

    private companion object {
        const val NAME = "migration-test.db"
    }
}
