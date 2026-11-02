package app.feltbook.core.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "settings.preferences_pb")

    private fun TestScope.store(scope: CoroutineScope = backgroundScope) =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private val changed = AppSettings(
        rulesForNewGame = GameRules(
            bankerFeeMode = BankerFeeMode.EACH_PERCENT,
            bankerFeeValue = 5,
            rakeMode = RakeMode.FIXED,
            rakeValue = 200,
            minBuyIn = 1_000,
            splitPayment = true,
            minPlayMinutes = 90,
        ),
        lastBankerName = "Олег",
        themeMode = ThemeMode.SYSTEM,
        dynamicColor = true,
        keepScreenOn = false,
        haptics = false,
        oddsCalculatorVisible = true,
    )

    @Test
    fun `пустое хранилище — значения по умолчанию AppSettings()`() = runTest {
        assertEquals(AppSettings(), DataStoreSettingsRepository(store()).settings.first())
    }

    @Test
    fun `все поля сохраняются и читаются заново из файла`() = runTest {
        val firstRun = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        DataStoreSettingsRepository(store(firstRun)).update { changed }
        firstRun.cancel()
        advanceUntilIdle()

        // Новый экземпляр DataStore поверх того же файла — как перезапуск приложения.
        assertEquals(changed, DataStoreSettingsRepository(store()).settings.first())
    }

    @Test
    fun `update получает текущие значения, поток отдаёт каждое изменение`() = runTest {
        val repository = DataStoreSettingsRepository(store())
        repository.settings.test {
            assertEquals(AppSettings(), awaitItem())
            repository.update { it.copy(lastBankerName = "Влад") }
            assertEquals("Влад", awaitItem().lastBankerName)
            repository.update { it.copy(haptics = !it.haptics) }
            assertEquals(AppSettings(lastBankerName = "Влад", haptics = false), awaitItem())
            repository.update { it }
            expectNoEvents()
        }
    }

    @Test
    fun `нечитаемое значение перечисления — значение по умолчанию, остальное сохраняется`() = runTest {
        val dataStore = store()
        DataStoreSettingsRepository(dataStore).update { changed }
        dataStore.edit {
            it[stringPreferencesKey("themeMode")] = "NEON"
            it[stringPreferencesKey("rules_rakeMode")] = "SOMETHING"
        }

        val read = DataStoreSettingsRepository(dataStore).settings.first()

        assertEquals(AppSettings().themeMode, read.themeMode)
        assertEquals(GameRules.DEFAULT.rakeMode, read.rulesForNewGame.rakeMode)
        assertEquals(changed.rulesForNewGame.rakeValue, read.rulesForNewGame.rakeValue)
        assertEquals(changed.lastBankerName, read.lastBankerName)
    }
}
