package app.feltbook.core.data.handover

import app.feltbook.core.data.TableScript
import app.feltbook.core.data.TestClock
import app.feltbook.core.data.gameRepository
import app.feltbook.core.data.inMemoryDatabase
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.Handovers
import app.feltbook.core.domain.engine.PreparedHandover
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Неподтверждённая передача переживает перезапуск и записывается той же (те же id), пока журнал не ушёл вперёд. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PendingHandoverStoreTest {
    private val db = inMemoryDatabase()
    private val repository = db.gameRepository()
    private val clock = TestClock()
    private val file = File.createTempFile("pending", ".json").apply { delete() }

    @After
    fun close() {
        db.close()
        file.delete()
    }

    private fun store() = PendingHandoverStore(file, Dispatchers.Unconfined)

    private fun pending(prepared: PreparedHandover) = PendingHandover(
        gameId = prepared.handover.gameId.value.toString(),
        handoverId = prepared.handover.id.value.toString(),
        toBanker = prepared.handover.toBanker,
        cashHandedOver = prepared.handover.cashHandedOver,
        incomeTaken = prepared.handover.incomeTaken,
        baseSeq = prepared.senderChange.baseSeq,
        packageText = FeltbookCodec.encode(
            FeltbookPackage.HandoverPackage(prepared.outgoing, prepared.handover),
            createdAt = clock.now,
        ),
    )

    @Test
    fun `запись переживает перезапуск, снимается по игре`() = runTest {
        val table = TableScript(repository, clock).runningGame()
        val prepared = Handovers.prepare(table.snapshot, "Влад", keepIncome = false, stamp = clock.stamp())
        store().save(pending(prepared))

        val reopened = store()
        assertEquals(pending(prepared).handoverId, reopened.get(table.snapshot.game.id)?.handoverId)
        reopened.remove(table.snapshot.game.id)
        assertNull(store().get(table.snapshot.game.id))
    }

    @Test
    fun `«Да, принял» — та же передача, игра «Передана»`() = runTest {
        val table = TableScript(repository, clock).runningGame()
        val prepared = Handovers.prepare(table.snapshot, "Влад", keepIncome = true, stamp = clock.stamp())
        val store = store()
        store.save(pending(prepared))

        val outcome = PendingHandovers(store, repository).confirm(table.snapshot.game.id)

        assertEquals(ConfirmOutcome.COMMITTED, outcome)
        val after = repository.getGame(table.snapshot.game.id)!!
        assertEquals(GameStatus.HANDED_OVER, after.game.status)
        assertEquals(listOf(prepared.handover), after.handovers)
        assertEquals(prepared.outgoing.entries, after.entries)
        assertNull(store.get(table.snapshot.game.id))
    }

    @Test
    fun `журнал ушёл вперёд — записать нельзя, запись снята, игра активна`() = runTest {
        val table = TableScript(repository, clock).runningGame()
        val prepared = Handovers.prepare(table.snapshot, "Влад", keepIncome = false, stamp = clock.stamp())
        val store = store()
        store.save(pending(prepared))
        table.play { s, st -> Commands.buyIn(s, table.player("Влад"), 500, BuyInPayment.PAID, st) }

        val outcome = PendingHandovers(store, repository).confirm(table.snapshot.game.id)

        assertEquals(ConfirmOutcome.STALE, outcome)
        assertTrue(repository.getGame(table.snapshot.game.id)!!.game.status.isActive)
        assertNull(store.get(table.snapshot.game.id))
    }

    @Test
    fun `«Нет, игра у меня» — запись снята, игра не меняется, битый файл — пусто`() = runTest {
        val table = TableScript(repository, clock).runningGame()
        val prepared = Handovers.prepare(table.snapshot, "Влад", keepIncome = false, stamp = clock.stamp())
        val store = store()
        store.save(pending(prepared))
        PendingHandovers(store, repository).discard(table.snapshot.game.id)
        assertNull(store.get(table.snapshot.game.id))
        assertEquals(table.snapshot, repository.getGame(table.snapshot.game.id))

        file.writeText("{не json")
        assertTrue(store().load().isEmpty())
    }
}
