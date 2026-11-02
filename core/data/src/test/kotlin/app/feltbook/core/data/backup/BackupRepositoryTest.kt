package app.feltbook.core.data.backup

import androidx.room.withTransaction
import app.feltbook.core.data.TableScript
import app.feltbook.core.data.TestClock
import app.feltbook.core.data.db.FeltbookDatabase
import app.feltbook.core.data.db.toDomain
import app.feltbook.core.data.gameRepository
import app.feltbook.core.data.inMemoryDatabase
import app.feltbook.core.data.repository.GameStore
import app.feltbook.core.data.repository.RoomPlayerNameRepository
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.Handovers
import app.feltbook.core.domain.engine.ImportConflictReason
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.repository.ImportReport
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRepositoryTest {
    private val clock = TestClock()
    private val source = inMemoryDatabase()
    private val target = inMemoryDatabase()

    @After
    fun close() {
        source.close()
        target.close()
    }

    private fun TestScope.backup(db: FeltbookDatabase) =
        RoomBackupRepository(db, GameStore(db), clock, UnconfinedTestDispatcher(testScheduler))

    private suspend fun FeltbookDatabase.dump(): Pair<List<GameSnapshot>, List<PlayerName>> =
        withTransaction { GameStore(this).loadAll() to playerNames().all().map { it.toDomain() } }

    /** Закрытая игра с долгом, переданная посреди игры (акт + INCOME_TAKEN), идущая игра, объединённые имена. */
    private suspend fun seed(db: FeltbookDatabase): TableScript {
        val repository = db.gameRepository()
        TableScript(repository, clock).closedGameWithDebt()
        clock.advance(60.minutes)
        val handedOver = TableScript(repository, clock).runningGame(banker = "Олег")
        handedOver.play { s, st -> Handovers.prepare(s, "Влад", keepIncome = true, st).senderChange }
        clock.advance(60.minutes)
        val running = TableScript(repository, clock).runningGame(banker = "Костя")
        RoomPlayerNameRepository(db).merge("Аня", "Влад")
        return running
    }

    private fun report(
        added: Int = 0,
        extended: Int = 0,
        identical: Int = 0,
        conflicts: List<Pair<GameId, ImportConflictReason>> = emptyList(),
        names: Int = 0,
    ) = ImportReport(null, added, extended, identical, conflicts, names)

    @Test
    fun `экспорт → чистая база → импорт даёт идентичные снимки и справочник`() = runTest {
        seed(source)
        val text = backup(source).exportAll()

        val result = backup(target).importAll(text)

        val (games, names) = source.dump()
        assertEquals(report(added = 3, names = names.size), result)
        assertEquals(source.dump(), target.dump())
        assertEquals(
            listOf(GameStatus.RUNNING, GameStatus.HANDED_OVER, GameStatus.CLOSED),
            games.map { it.game.status },
        )
        assertTrue(games[1].handovers.isNotEmpty())
        assertEquals("Влад", names.first { it.name == "Аня" }.mergedInto)
        // Экспорт восстановленной базы — тот же файл (время экспорта то же).
        assertEquals(text, backup(target).exportAll())
    }

    @Test
    fun `повторный импорт — всё Identical, база не меняется`() = runTest {
        seed(source)
        val text = backup(source).exportAll()
        val before = source.dump()

        val result = backup(source).importAll(text)

        assertEquals(report(identical = 3), result)
        assertEquals(before, source.dump())
    }

    @Test
    fun `старая копия при новом локальном журнале — LOCAL_NEWER, локальное не откатывается`() = runTest {
        val running = seed(source)
        val old = backup(source).exportAll()
        running.play { s, st -> Commands.buyIn(s, running.player("Влад"), 500, BuyInPayment.PAID, st) }
        val before = source.dump()

        val result = backup(source).importAll(old)

        assertEquals(
            report(identical = 2, conflicts = listOf(running.snapshot.game.id to ImportConflictReason.LOCAL_NEWER)),
            result,
        )
        assertEquals(before, source.dump())
    }

    @Test
    fun `новая копия дописывает хвост журнала — Extend`() = runTest {
        val running = seed(source)
        backup(target).importAll(backup(source).exportAll())
        running.play { s, st -> Commands.buyIn(s, running.player("Аня"), 500, BuyInPayment.DEBT, st) }

        val result = backup(target).importAll(backup(source).exportAll())

        assertEquals(report(extended = 1, identical = 2), result)
        assertEquals(source.dump().first, target.dump().first)
    }

    @Test
    fun `разошедшиеся журналы — конфликт, локальная игра не тронута`() = runTest {
        val running = seed(source)
        backup(target).importAll(backup(source).exportAll())
        val local = TableScript(target.gameRepository(), clock).resume(running.snapshot)
        local.play { s, st -> Commands.buyIn(s, local.player("Влад"), 700, BuyInPayment.PAID, st) }
        running.play { s, st -> Commands.buyIn(s, running.player("Влад"), 500, BuyInPayment.PAID, st) }
        val before = target.dump()

        val result = backup(target).importAll(backup(source).exportAll())

        assertEquals(
            report(identical = 2, conflicts = listOf(running.snapshot.game.id to ImportConflictReason.DIVERGED_LEDGER)),
            result,
        )
        assertEquals(before, target.dump())
    }

    @Test
    fun `вторая активная игра из копии не импортируется`() = runTest {
        val incoming = seed(source)
        val text = backup(source).exportAll()
        TableScript(target.gameRepository(), clock).runningGame(banker = "Лиза")
        val before = target.dump()

        val result = backup(target).importAll(text)

        assertEquals(2, result.added)
        assertEquals(
            listOf(incoming.snapshot.game.id to ImportConflictReason.SECOND_ACTIVE_GAME),
            result.conflicts,
        )
        val after = target.dump().first
        assertEquals(before.first.single(), after.first { it.game.status.isActive })
        assertEquals(3, after.size)
    }

    @Test
    fun `битый файл — отчёт с ошибкой, база не меняется`() = runTest {
        seed(source)
        val text = backup(source).exportAll()
        val before = source.dump()
        val repository = backup(source)

        assertEquals(DecodeResult.NotFeltbook, repository.importAll("not a backup").failure)
        val tampered = text.replace("Влад", "Вова")
        assertEquals(DecodeResult.ChecksumMismatch, repository.importAll(tampered).failure)
        val template = FeltbookCodec.encode(
            FeltbookPackage.TemplatePackage(Handovers.template(before.first.last())),
            clock.now(),
        )
        assertTrue(repository.importAll(template).failure is DecodeResult.Malformed)
        assertEquals(before, source.dump())
    }
}
