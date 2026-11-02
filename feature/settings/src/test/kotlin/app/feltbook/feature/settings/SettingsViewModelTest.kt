package app.feltbook.feature.settings

import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.feltbook.core.data.deletion.GameDeletionScheduler
import app.feltbook.core.domain.engine.ImportConflictReason
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.model.ThemeMode
import app.feltbook.core.domain.repository.ImportFailure
import app.feltbook.core.domain.repository.ImportReport
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.format.UiText
import app.feltbook.feature.settings.main.ImportResultUi
import app.feltbook.feature.settings.main.RuleField
import app.feltbook.feature.settings.main.SettingsAction
import app.feltbook.feature.settings.main.SettingsEvent
import app.feltbook.feature.settings.main.SettingsUiState
import app.feltbook.feature.settings.main.SettingsUndo
import app.feltbook.feature.settings.main.SettingsViewModel
import kotlin.uuid.Uuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val games = FakeGameRepository()
    private val backup = FakeBackupRepository()
    private val files = FakeBackupFiles()

    private fun TestScope.viewModel() = SettingsViewModel(
        settings,
        games,
        backup,
        files,
        GameDeletionScheduler(games, backgroundScope),
        TestClock(),
        UtcZone,
    )

    private suspend fun ReceiveTurbine<SettingsUiState>.loaded(): SettingsUiState {
        var state = awaitItem()
        while (state.loading) state = awaitItem()
        return state
    }

    @Test
    fun feeEditorSavesAllFourModesAndUndoRestores() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            loaded()
            viewModel.events.test {
                viewModel.onAction(SettingsAction.EditRule(RuleField.BANKER_FEE))
                viewModel.onAction(SettingsAction.EditorFeeMode(BankerFeeMode.EACH_PERCENT))
                viewModel.onAction(SettingsAction.EditorEntry(AmountEntry(5)))
                viewModel.onAction(SettingsAction.SaveRules)
                val event = awaitItem() as SettingsEvent.Undoable
                val rules = settings.state.value.rulesForNewGame
                assertEquals(BankerFeeMode.EACH_PERCENT, rules.bankerFeeMode)
                assertEquals(5L, rules.bankerFeeValue)
                assertEquals(SettingsUndo.Rules(GameRules.DEFAULT), event.token)

                viewModel.onAction(SettingsAction.Undo(event.token))
                assertEquals(GameRules.DEFAULT, settings.state.value.rulesForNewGame)
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun rulesWithProblemsCannotBeSaved() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            loaded()
            viewModel.onAction(SettingsAction.EditRule(RuleField.MIN_BUY_IN))
            awaitItem()
            viewModel.onAction(SettingsAction.EditorEntry(AmountEntry(0)))
            val editor = checkNotNull(awaitItem().editor)
            assertFalse(editor.canSave)
            assertTrue(editor.problems.isNotEmpty())
            viewModel.onAction(SettingsAction.SaveRules)
            assertEquals(GameRules.DEFAULT, settings.state.value.rulesForNewGame)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun rakeFixedModeAndKeypadLimits() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            loaded()
            viewModel.onAction(SettingsAction.EditRule(RuleField.RAKE))
            assertEquals(GameRules.MAX_PERCENT, checkNotNull(awaitItem().editor).maxValue)
            viewModel.onAction(SettingsAction.EditorRakeMode(RakeMode.FIXED))
            val editor = checkNotNull(awaitItem().editor)
            assertEquals(RakeMode.FIXED, editor.draft.rakeMode)
            assertTrue(editor.maxValue > GameRules.MAX_PERCENT)
            viewModel.onAction(SettingsAction.SaveRules)
            assertNull(awaitItem().editor)
            assertEquals(RakeMode.FIXED, settings.state.value.rulesForNewGame.rakeMode)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun themeAndSwitches() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.SetTheme(ThemeMode.LIGHT))
        viewModel.onAction(SettingsAction.SetDynamicColor(true))
        viewModel.onAction(SettingsAction.SetKeepScreenOn(false))
        viewModel.onAction(SettingsAction.SetHaptics(false))
        assertEquals(
            AppSettings(themeMode = ThemeMode.LIGHT, dynamicColor = true, keepScreenOn = false, haptics = false),
            settings.state.value,
        )
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun exportAsksForFeltbookFileThenWritesBackup() = runTest {
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onAction(SettingsAction.Export)
            assertEquals(SettingsEvent.CreateBackupDocument("feltbook-2026-11-02.feltbook"), awaitItem())
            viewModel.onAction(SettingsAction.ExportTo("content://doc/1"))
            val done = awaitItem() as SettingsEvent.Message
            assertTrue(done.text is UiText.Res)
            assertEquals(backup.exportText, files.written["content://doc/1"])
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun cancelledPickerDoesNothing() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.ExportTo(null))
        viewModel.onAction(SettingsAction.ImportFrom(null))
        assertTrue(files.written.isEmpty())
        assertTrue(backup.imported.isEmpty())
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun importShowsReportWithLocalNewerSeparately() = runTest {
        val id = GameId(Uuid.random())
        backup.report = ImportReport(
            failure = null,
            added = 3,
            extended = 1,
            identical = 5,
            conflicts = listOf(id to ImportConflictReason.LOCAL_NEWER, id to ImportConflictReason.DIVERGED_LEDGER),
            playerNamesAdded = 2,
        )
        files.readable["content://doc/2"] = "backup"
        val viewModel = viewModel()
        viewModel.uiState.test {
            loaded()
            viewModel.onAction(SettingsAction.ImportFrom("content://doc/2"))
            var state = awaitItem()
            while (state.importResult == null) state = awaitItem()
            assertEquals(
                ImportResultUi.Done(
                    added = 3,
                    extended = 1,
                    identical = 5,
                    localNewer = 1,
                    conflicts = 1,
                    namesAdded = 2,
                ),
                state.importResult,
            )
            assertEquals(listOf("backup"), backup.imported)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun handoverFileIsRejectedAsWrongKind() = runTest {
        backup.report = FakeBackupRepository.EMPTY_REPORT.copy(
            failure = ImportFailure.Unreadable(DecodeResult.WrongKind(expected = "BACKUP", found = "HANDOVER")),
        )
        files.readable["content://doc/3"] = "handover"
        val viewModel = viewModel()
        viewModel.uiState.test {
            loaded()
            viewModel.onAction(SettingsAction.ImportFrom("content://doc/3"))
            var state = awaitItem()
            while (state.importResult == null) state = awaitItem()
            val failed = state.importResult as ImportResultUi.Failed
            assertEquals(UiText.Res(R.string.settings_restore_wrong_kind), failed.message)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun deleteAllRequiresTypedWord() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            loaded()
            viewModel.onAction(SettingsAction.DeleteAll)
            awaitItem()
            viewModel.onAction(SettingsAction.DeleteAllInput("удалит"))
            awaitItem()
            viewModel.onAction(SettingsAction.ConfirmDeleteAll)
            assertEquals(0, games.deleteAllCalls)
            viewModel.onAction(SettingsAction.DeleteAllInput("уДАЛИТЬ "))
            awaitItem()
            viewModel.onAction(SettingsAction.ConfirmDeleteAll)
            assertEquals(1, games.deleteAllCalls)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun hideOddsWithUndo() = runTest {
        settings.state.value = AppSettings(oddsCalculatorVisible = true)
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onAction(SettingsAction.HideOdds)
            val event = awaitItem() as SettingsEvent.Undoable
            assertFalse(settings.state.value.oddsCalculatorVisible)
            viewModel.onAction(SettingsAction.Undo(event.token))
            assertTrue(settings.state.value.oddsCalculatorVisible)
            viewModel.onAction(SettingsAction.OpenOdds)
            assertEquals(SettingsEvent.OpenOdds, awaitItem())
        }
        viewModel.viewModelScope.cancel()
    }
}
