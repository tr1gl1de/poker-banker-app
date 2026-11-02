package app.feltbook.feature.history.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.data.deletion.GameDeletionScheduler
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.PlayerNameRepository
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.HistoryZone
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Статистика (m3-ui-spec §5.12): итоги периода, график дохода банкира, игроки; шторка игрока со статистикой
 * за все игры и «Объединить с…» (`PlayerNameRepository.merge`, отмена — `unmerge`, snackbar 5 с).
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    gameRepository: GameRepository,
    private val names: PlayerNameRepository,
    deletions: GameDeletionScheduler,
    private val clock: Clock,
    private val zone: HistoryZone,
) : ViewModel() {
    private val inputs = MutableStateFlow(StatsInputs())

    private val eventChannel = Channel<StatsEvent>(Channel.BUFFERED)
    val events: Flow<StatsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<StatsUiState> =
        combine(gameRepository.observeAllGames(), names.observeAll(), deletions.pending, inputs) {
                games,
                directory,
                pending,
                input,
            ->
            val hidden = pending?.ids.orEmpty()
            buildStatsUiState(games.filter { it.game.id !in hidden }, directory, input, clock.now(), zone.zone())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StatsUiState())

    @Suppress("CyclomaticComplexMethod") // Плоский разбор действий экрана.
    fun onAction(action: StatsAction) {
        when (action) {
            StatsAction.Back -> eventChannel.trySend(StatsEvent.Back)

            is StatsAction.PeriodMenu -> inputs.update { it.copy(periodMenu = action.open) }

            is StatsAction.SetPeriod -> inputs.update { it.copy(period = action.period, periodMenu = false) }

            is StatsAction.PlayerClick -> inputs.update { it.copy(sheetName = action.name, picking = false) }

            StatsAction.DismissSheet -> inputs.update { it.copy(sheetName = null, picking = false, mergeDialog = null) }

            StatsAction.StartMerge -> inputs.update { it.copy(picking = it.sheetName != null) }

            StatsAction.CancelMerge -> inputs.update { it.copy(picking = false) }

            is StatsAction.PickMergeTarget -> pickTarget(action.into)

            StatsAction.ConfirmMerge -> merge()

            StatsAction.DismissDialog -> inputs.update { it.copy(mergeDialog = null) }

            is StatsAction.Unmerge -> unmerge(action.alias)

            is StatsAction.Undo -> undo(action.token)

            StatsAction.OpenDebts -> {
                inputs.update { it.copy(sheetName = null, picking = false) }
                eventChannel.trySend(StatsEvent.OpenDebts)
            }
        }
    }

    private fun pickTarget(into: String) {
        val from = uiState.value.sheet?.name ?: inputs.value.sheetName ?: return
        inputs.update { it.copy(mergeDialog = MergeDialogUi(from = from, into = into)) }
    }

    private fun merge() {
        val dialog = inputs.value.mergeDialog ?: return
        inputs.update { it.copy(mergeDialog = null, picking = false) }
        viewModelScope.launch {
            val directory = names.observeAll().first()
            if (!PlayerNames.canMerge(dialog.from, dialog.into, directory)) {
                eventChannel.send(StatsEvent.Message(uiText(UiR.string.error_generic)))
                return@launch
            }
            if (!safely { names.merge(dialog.from, dialog.into) }) return@launch
            // Шторка закрывается: snackbar «Отменить» под шторкой не виден; список уже считает имена вместе.
            inputs.update { it.copy(sheetName = null) }
            eventChannel.send(
                StatsEvent.Undoable(
                    message = uiText(R.string.stats_merged_snackbar, dialog.from, dialog.into),
                    token = StatsUndo.Merge(dialog.from),
                ),
            )
        }
    }

    private fun unmerge(alias: String) {
        viewModelScope.launch {
            val into = names.observeAll().first().firstOrNull { it.name == alias }?.mergedInto ?: return@launch
            if (!safely { names.unmerge(alias) }) return@launch
            inputs.update { it.copy(sheetName = null, picking = false) }
            eventChannel.send(
                StatsEvent.Undoable(
                    message = uiText(R.string.stats_unmerged_snackbar, alias, into),
                    token = StatsUndo.Unmerge(alias, into),
                ),
            )
        }
    }

    private fun undo(token: StatsUndo) {
        viewModelScope.launch {
            when (token) {
                is StatsUndo.Merge -> safely { names.unmerge(token.from) }

                is StatsUndo.Unmerge -> {
                    val directory = names.observeAll().first()
                    if (PlayerNames.canMerge(token.alias, token.into, directory)) {
                        safely { names.merge(token.alias, token.into) }
                    }
                }
            }
        }
    }

    /** Сбой базы — сообщение «Не получилось сохранить», не падение. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun safely(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        eventChannel.send(StatsEvent.Message(uiText(UiR.string.error_generic)))
        false
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
