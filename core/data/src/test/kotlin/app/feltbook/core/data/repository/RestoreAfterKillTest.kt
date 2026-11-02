package app.feltbook.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import app.feltbook.core.data.TableScript
import app.feltbook.core.data.TestClock
import app.feltbook.core.data.assertThrowsSuspend
import app.feltbook.core.data.db.FeltbookDatabase
import app.feltbook.core.data.db.Migrations
import app.feltbook.core.data.gameRepository
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** M2.6: база в файле, «закуп → процесс убит → перезапуск» — состояние восстановлено, половины операции нет. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RestoreAfterKillTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = TestClock()

    private fun open(): FeltbookDatabase = Room
        .databaseBuilder(context, FeltbookDatabase::class.java, NAME)
        .addMigrations(*Migrations.ALL)
        .allowMainThreadQueries()
        .build()

    @After
    fun deleteFile() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun `закуп записан, процесс убит до следующего действия — после перезапуска всё на месте`() = runTest {
        val first = open()
        val table = TableScript(first.gameRepository(), clock).runningGame()
        table.play { s, st -> Commands.buyIn(s, table.player("Влад"), 500, BuyInPayment.DEBT, st) }
        first.close()

        val reopened = open()
        try {
            assertEquals(table.snapshot, reopened.gameRepository().getActiveGame())
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `процесс убит посреди коммита закупа — после перезапуска закупа нет, его можно повторить`() = runTest {
        val first = open()
        val table = TableScript(first.gameRepository(), clock).runningGame()
        val buyIn = Commands.addPlayer(table.snapshot, "Костя", 1_000, BuyInPayment.DEBT, clock.stamp())
        assertThrowsSuspend<IllegalStateException> {
            first.withTransaction {
                GameStore(first).write(listOf(buyIn))
                error("процесс убит")
            }
        }
        first.close()

        val reopened = open()
        try {
            val repository = reopened.gameRepository()
            assertEquals(table.snapshot, repository.getActiveGame())
            // seq не «сгорел»: то же действие после перезапуска записывается.
            repository.commit(listOf(buyIn))
            assertEquals(table.snapshot.entries + buyIn.entries, repository.getActiveGame()?.entries)
        } finally {
            reopened.close()
        }
    }

    private companion object {
        const val NAME = "restore-after-kill.db"
    }
}
