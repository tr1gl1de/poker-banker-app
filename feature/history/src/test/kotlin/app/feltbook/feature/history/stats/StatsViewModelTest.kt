package app.feltbook.feature.history.stats

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import app.feltbook.core.data.deletion.GameDeletionScheduler
import app.feltbook.core.domain.engine.Stats
import app.feltbook.feature.history.common.PreviewHistoryData
import app.feltbook.feature.history.testing.FakeGameRepository
import app.feltbook.feature.history.testing.FakePlayerNameRepository
import app.feltbook.feature.history.testing.MainDispatcherRule
import app.feltbook.feature.history.testing.TestClock
import app.feltbook.feature.history.testing.UtcZone
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val games = PreviewHistoryData.games()
    private val directory = PreviewHistoryData.directory()

    @Test
    fun totalsAreStatsPeriod() {
        val inputs = StatsInputs(period = StatsPeriod.ALL)
        val state = buildStatsUiState(games, directory, inputs, PreviewHistoryData.now, ZoneOffset.UTC)
        val expected = Stats.period(games, directory, from = null, to = null)
        assertEquals(expected.gameCount, state.gameCount)
        assertEquals(expected.turnover, state.turnover)
        assertEquals(expected.rake, state.rake)
        assertEquals(expected.bankerIncome, state.bankerIncome)
        assertEquals(expected.players.map { it.canonicalName }, state.players.map { it.name })
        // График — по возрастанию времени, подписи dd.MM.
        assertEquals(listOf("23.09", "18.10", "25.10", "30.10"), state.chart.map { it.label })
        assertEquals(expected.incomeByGame.map { it.bankerIncome }, state.chart.map { it.value })
    }

    @Test
    fun monthPeriodIsRollingWindow() {
        val inputs = StatsInputs(period = StatsPeriod.MONTH)
        val state = buildStatsUiState(games, directory, inputs, PreviewHistoryData.now, ZoneOffset.UTC)
        val from = StatsPeriod.MONTH.from(PreviewHistoryData.now, ZoneOffset.UTC)
        assertEquals(Stats.period(games, directory, from, null).gameCount, state.gameCount)
        assertEquals(3, state.gameCount) // 18, 25 и 30 октября; 23 сентября — раньше 2 октября
        assertTrue(checkNotNull(from) <= PreviewHistoryData.now - 30.days)
    }

    @Test
    fun playerSheetShowsAllTimeStatsAndOpenDebtWithDate() {
        val sheet = checkNotNull(
            buildStatsUiState(
                games,
                directory,
                StatsInputs(period = StatsPeriod.MONTH, sheetName = "Борис"),
                PreviewHistoryData.now,
                ZoneOffset.UTC,
            ).sheet,
        )
        val stats = checkNotNull(Stats.player("Борис", games, directory))
        assertEquals(stats.gamesPlayed, sheet.gamesPlayed)
        assertEquals(stats.totalResult, sheet.totalResult)
        assertEquals(stats.averageBuyInPerGame(), sheet.averageBuyIn)
        assertEquals(600L, sheet.openDebt)
        assertEquals(PreviewHistoryData.thursdayPartial().game.createdAt + 90.minutes, sheet.debtSince)
        assertTrue("Боря" in sheet.mergeCandidates)
    }

    @Test
    fun mergeThenUndo() = runTest {
        val repository = FakeGameRepository(games)
        val names = FakePlayerNameRepository(directory)
        val viewModel = StatsViewModel(
            repository,
            names,
            GameDeletionScheduler(repository, backgroundScope),
            TestClock(PreviewHistoryData.now),
            UtcZone,
        )
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            viewModel.events.test {
                viewModel.onAction(StatsAction.PlayerClick("Боря"))
                viewModel.onAction(StatsAction.StartMerge)
                viewModel.onAction(StatsAction.PickMergeTarget("Борис"))
                viewModel.onAction(StatsAction.ConfirmMerge)
                val event = awaitItem() as StatsEvent.Undoable
                assertEquals(StatsUndo.Merge("Боря"), event.token)
                assertEquals("Борис", names.names.value.first { it.name == "Боря" }.mergedInto)

                viewModel.onAction(StatsAction.Undo(event.token))
                assertNull(names.names.value.first { it.name == "Боря" }.mergedInto)
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }
}
