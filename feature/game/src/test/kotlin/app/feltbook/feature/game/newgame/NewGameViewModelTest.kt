package app.feltbook.feature.game.newgame

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import app.feltbook.core.data.handover.ReceivedTemplateStore
import app.feltbook.core.domain.engine.GameTemplate
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.feature.game.common.toGameId
import app.feltbook.feature.game.table.PreviewTableData
import app.feltbook.feature.game.testing.FakeGameRepository
import app.feltbook.feature.game.testing.FakeSettingsRepository
import app.feltbook.feature.game.testing.MainDispatcherRule
import app.feltbook.feature.game.testing.TestClock
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NewGameViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeGameRepository()
    private val rules = GameRules.DEFAULT.copy(rakeValue = 5)
    private val settings = FakeSettingsRepository(AppSettings(rulesForNewGame = rules, lastBankerName = "Саша"))
    private val templates = ReceivedTemplateStore()
    private val viewModel by lazy { NewGameViewModel(repository, settings, TestClock(), templates) }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun showsReceivedTemplateNamesUntilGameStarts() = runTest {
        templates.put(GameTemplate("Андрей", rules, listOf("Борис", "Дима")))
        val state = viewModel.uiState.value
        assertEquals("Андрей", state.templateFrom)
        assertEquals(listOf("Борис", "Дима"), state.templateNames)
        viewModel.onAction(NewGameAction.Start("Игра"))
        assertEquals(null, templates.template.value)
    }

    @Test
    fun prefillsRulesAndBankerFromSettings() {
        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(rules, state.rules)
        assertEquals("Саша", state.bankerName)
        assertTrue(state.bankerPlays)
        // Пример: закуп 1 000 → рейк 5 % = 50, сбор разово 100 → 1 150.
        assertEquals(RulesExample(chips = 1_000, rake = 50, fee = 100), state.example)
    }

    @Test
    fun exampleFollowsRules() {
        viewModel.onAction(NewGameAction.RakePercent(percent = false))
        assertEquals(RakeMode.FIXED, viewModel.uiState.value.rules.rakeMode)
        assertEquals(100L, viewModel.uiState.value.example.rake)

        viewModel.onAction(NewGameAction.FeeSegmentClick(FeeSegment.EACH))
        viewModel.onAction(NewGameAction.FeeEachPercent(percent = true))
        assertEquals(BankerFeeMode.EACH_PERCENT, viewModel.uiState.value.rules.bankerFeeMode)
        assertEquals(10L, viewModel.uiState.value.rules.bankerFeeValue)
        assertEquals(100L, viewModel.uiState.value.example.fee)
    }

    @Test
    fun editingInvalidValueShowsProblemAndDisablesStart() {
        viewModel.onAction(NewGameAction.EditRule(RuleField.RAKE))
        viewModel.onAction(NewGameAction.EditorEntry(AmountEntry(150)))
        viewModel.onAction(NewGameAction.EditorDone)
        val state = viewModel.uiState.value
        assertTrue(state.problems.isNotEmpty())
        assertFalse(state.canStart)
    }

    @Test
    fun blankBankerIsAnError() {
        viewModel.onAction(NewGameAction.BankerChange("  "))
        viewModel.onAction(NewGameAction.Start(defaultTitle = "2 ноя, 20:05"))
        assertNotNull(viewModel.uiState.value.bankerError)
        assertEquals(0, repository.commitCount)
    }

    @Test
    fun startWritesRunningGameAndRemembersRules() = runTest {
        viewModel.onAction(NewGameAction.RakePercent(percent = false))
        viewModel.events.test {
            viewModel.onAction(NewGameAction.Start(defaultTitle = "2 ноя, 20:05"))
            val event = awaitItem() as NewGameEvent.Started
            val game = repository.snapshot(event.gameId.toGameId()).game
            assertEquals(GameStatus.RUNNING, game.status)
            assertEquals("2 ноя, 20:05", game.title)
            assertEquals("Саша", game.currentBankerName)
            assertTrue(game.bankerIsPlayer)
            assertEquals(RakeMode.FIXED, game.rules.rakeMode)
            assertEquals(RakeMode.FIXED, settings.state.value.rulesForNewGame.rakeMode)
        }
    }

    @Test
    fun secondActiveGameShowsDialogInsteadOfStarting() = runTest {
        val active = PreviewTableData.running()
        repository.games.value = mapOf(active.game.id to active)
        viewModel.onAction(NewGameAction.Start(defaultTitle = "2 ноя, 20:05"))
        assertEquals(active.game.title, viewModel.uiState.value.anotherGameTitle)
        assertEquals(0, repository.commitCount)
    }
}
