package app.feltbook.core.data.repository

import app.cash.turbine.test
import app.feltbook.core.data.T0
import app.feltbook.core.data.assertThrowsSuspend
import app.feltbook.core.data.db.PlayerNameEntity
import app.feltbook.core.data.inMemoryDatabase
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.model.PlayerName
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerNameRepositoryTest {
    private val db = inMemoryDatabase()
    private val repository = RoomPlayerNameRepository(db)

    @Before
    fun seed() = kotlinx.coroutines.runBlocking {
        listOf("Влад", "Владик", "Дима").forEachIndexed { index, name ->
            db.playerNames().insertIfAbsent(PlayerNameEntity(name, T0 + index.minutes, null))
        }
    }

    @After
    fun close() = db.close()

    @Test
    fun `объединение и отмена объединения видны в потоке`() = runTest {
        repository.observeAll().test {
            assertEquals(listOf("Влад", "Владик", "Дима"), awaitItem().map { it.name })

            repository.merge(" Владик ", "Влад")
            val merged = awaitItem()
            assertEquals(PlayerName("Владик", T0 + 1.minutes, mergedInto = "Влад"), merged[1])
            assertEquals("Влад", PlayerNames.canonical("Владик", merged))

            repository.unmerge("Владик")
            assertEquals(null, awaitItem()[1].mergedInto)
        }
    }

    @Test
    fun `объединение с циклом или неизвестным именем отклоняется и ничего не меняет`() = runTest {
        repository.merge("Владик", "Влад")

        assertThrowsSuspend<IllegalArgumentException> { repository.merge("Влад", "Владик") }
        assertThrowsSuspend<IllegalArgumentException> { repository.merge("Костя", "Влад") }
        assertThrowsSuspend<IllegalArgumentException> { repository.merge("Дима", "Дима") }

        assertEquals(
            mapOf("Влад" to null, "Владик" to "Влад", "Дима" to null),
            db.playerNames().all().associate { it.name to it.mergedInto },
        )
    }
}
