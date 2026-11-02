package app.feltbook.core.data.db

import android.database.sqlite.SQLiteConstraintException
import app.feltbook.core.data.T0
import app.feltbook.core.data.assertThrowsSuspend
import app.feltbook.core.data.inMemoryDatabase
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.RakeMode
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DaoTest {
    private val db = inMemoryDatabase()

    @After
    fun close() = db.close()

    private val gameId = Uuid.fromLongs(1, 1)
    private val playerId = Uuid.fromLongs(2, 1)

    private fun game(id: Uuid = gameId, status: GameStatus = GameStatus.RUNNING, title: String = "Пятница") =
        GameEntity(
            id = id,
            title = title,
            createdAt = T0,
            closedAt = null,
            status = status,
            currentBankerName = "Олег",
            bankerPlayerId = null,
            rules = GameRules.DEFAULT.toEntity(),
            editedAfterClose = false,
            schemaVersion = 1,
        )

    private fun entry(seq: Long, id: Uuid = Uuid.fromLongs(3, seq), game: Uuid = gameId) = LedgerEntryEntity(
        id = id,
        gameId = game,
        seq = seq,
        operationId = Uuid.fromLongs(4, seq),
        type = EntryType.BUY_IN,
        playerId = playerId,
        amount = 100 * seq,
        counterpartyPlayerId = null,
        bankerName = "Олег",
        createdAt = T0 + seq.minutes,
        reversesEntryId = null,
        note = null,
        earlyExit = false,
    )

    private suspend fun seedGame() {
        db.games().upsert(game())
        db.players().upsert(listOf(PlayerEntity(playerId, gameId, "Влад", T0)))
    }

    @Test
    fun `записи журнала читаются по возрастанию seq независимо от порядка вставки`() = runTest {
        seedGame()
        db.ledger().insert(listOf(entry(3)))
        db.ledger().insert(listOf(entry(1), entry(2)))

        assertEquals(listOf(1L, 2L, 3L), db.ledger().byGame(gameId).map { it.seq })
        assertEquals(3L, db.ledger().lastSeq(gameId))
        assertEquals(entry(2), db.ledger().byGame(gameId)[1])
    }

    @Test
    fun `дубль seq в игре отклоняется уникальным индексом`() = runTest {
        seedGame()
        db.ledger().insert(listOf(entry(1)))

        assertThrowsSuspend<SQLiteConstraintException> {
            db.ledger().insert(listOf(entry(1, id = Uuid.fromLongs(9, 9))))
        }
        assertEquals(1, db.ledger().all().size)
    }

    @Test
    fun `дубль id записи отклоняется, а не заменяет запись`() = runTest {
        seedGame()
        db.ledger().insert(listOf(entry(1)))

        assertThrowsSuspend<SQLiteConstraintException> { db.ledger().insert(listOf(entry(2, id = entry(1).id))) }
        assertEquals(listOf(entry(1)), db.ledger().all())
    }

    @Test
    fun `один и тот же seq в разных играх допустим`() = runTest {
        seedGame()
        val other = Uuid.fromLongs(1, 2)
        db.games().upsert(game(id = other))
        db.ledger().insert(listOf(entry(1), entry(1, id = Uuid.fromLongs(3, 101), game = other)))

        assertEquals(1, db.ledger().byGame(other).size)
    }

    @Test
    fun `запись без игры отклоняется внешним ключом`() = runTest {
        assertThrowsSuspend<SQLiteConstraintException> { db.ledger().insert(listOf(entry(1))) }
    }

    @Test
    fun `удаление игры каскадом удаляет игроков, журнал и акты, но не справочник имён`() = runTest {
        seedGame()
        db.ledger().insert(listOf(entry(1), entry(2)))
        db.handovers().insert(
            listOf(HandoverEntity(Uuid.fromLongs(5, 1), gameId, "Олег", "Влад", 1_000, 0, 2, T0 + 5.minutes)),
        )
        db.playerNames().insertIfAbsent(PlayerNameEntity("Влад", T0, null))

        assertEquals(1, db.games().delete(listOf(gameId)))

        assertNull(db.games().byId(gameId))
        assertTrue(db.players().all().isEmpty())
        assertTrue(db.ledger().all().isEmpty())
        assertTrue(db.handovers().all().isEmpty())
        assertEquals(1, db.playerNames().count())
    }

    @Test
    fun `upsert строки игры не трогает журнал (не REPLACE с каскадом)`() = runTest {
        seedGame()
        db.ledger().insert(listOf(entry(1)))

        db.games().upsert(game(status = GameStatus.SETTLING, title = "Пятница, поздно"))
        db.players().upsert(listOf(PlayerEntity(playerId, gameId, "Владислав", T0)))

        assertEquals(GameStatus.SETTLING, db.games().byId(gameId)?.status)
        assertEquals("Владислав", db.players().byGame(gameId).single().name)
        assertEquals(listOf(entry(1)), db.ledger().all())
    }

    @Test
    fun `игроки читаются в порядке посадки, переименование не меняет порядок`() = runTest {
        db.games().upsert(game())
        val ids = (1L..3L).map { Uuid.fromLongs(2, 10 + it) }
        db.players().upsert(listOf(PlayerEntity(ids[2], gameId, "Я", T0), PlayerEntity(ids[0], gameId, "Б", T0)))
        db.players().upsert(listOf(PlayerEntity(ids[1], gameId, "А", T0)))
        db.players().upsert(listOf(PlayerEntity(ids[2], gameId, "Яна", T0)))

        assertEquals(listOf("Яна", "Б", "А"), db.players().byGame(gameId).map { it.name })
    }

    @Test
    fun `справочник имён — вставка без замены и сдвиг firstSeenAt только назад`() = runTest {
        val dao = db.playerNames()
        assertTrue(dao.insertIfAbsent(PlayerNameEntity("Влад", T0, null)) != -1L)
        assertEquals(-1L, dao.insertIfAbsent(PlayerNameEntity("Влад", T0 - 5.minutes, "Дима")))
        dao.lowerFirstSeen("Влад", T0 + 5.minutes)
        assertEquals(PlayerNameEntity("Влад", T0, null), dao.all().single())
        dao.lowerFirstSeen("Влад", T0 - 5.minutes)
        assertEquals(T0 - 5.minutes, dao.all().single().firstSeenAt)
    }

    @Test
    fun `конвертеры — UUID, время и перечисления сохраняются без потерь`() = runTest {
        val rules = GameRules(
            bankerFeeMode = BankerFeeMode.EACH_PERCENT,
            bankerFeeValue = 5,
            rakeMode = RakeMode.FIXED,
            rakeValue = 50,
            minBuyIn = 1_000,
            splitPayment = true,
            minPlayMinutes = 90,
        )
        val closed = game(status = GameStatus.CLOSED).copy(
            closedAt = T0 + 300.minutes,
            bankerPlayerId = playerId,
            rules = rules.toEntity(),
            editedAfterClose = true,
        )
        db.games().upsert(closed)

        val read = db.games().byId(gameId)!!
        assertEquals(closed, read)
        assertEquals(rules, read.rules.toDomain())
        assertEquals(listOf(gameId), db.games().idsWithStatus(listOf(GameStatus.CLOSED)))
    }
}
