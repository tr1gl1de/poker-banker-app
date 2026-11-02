package app.feltbook.feature.history.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.feature.history.common.GameDeletionScheduler
import app.feltbook.feature.history.common.HistoryZone
import app.feltbook.feature.history.common.toGameId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ввод экрана, которого нет в базе: фильтр, выбор, диалог. */
private data class HistoryInputs(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val selection: Set<String> = emptySet(),
    val deleteDialog: Boolean = false,
)

/**
 * История (m3-ui-spec §5.11): игры по месяцам, фильтр, мультивыбор и удаление с «Отменить» 5 с через
 * [GameDeletionScheduler] (до истечения окна игры только скрыты).
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    gameRepository: GameRepository,
    private val deletions: GameDeletionScheduler,
    private val clock: Clock,
    private val zone: HistoryZone,
) : ViewModel() {
    private val inputs = MutableStateFlow(HistoryInputs())

    private val eventChannel = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<HistoryUiState> =
        combine(gameRepository.observeAllGames(), deletions.pending, inputs) { games, pending, input ->
            val hidden = pending?.ids.orEmpty()
            val months = buildHistoryMonths(games, hidden, input.filter, clock.now(), zone.zone())
            val visible = months.flatMap { month -> month.rows.map { it.gameId } }.toSet()
            val selection = input.selection.filter { it in visible }.toSet()
            HistoryUiState(
                loading = false,
                filter = input.filter,
                months = months,
                selection = selection,
                deleteDialog = input.deleteDialog && selection.isNotEmpty(),
                pendingDeletion = pending?.let { PendingDeletionUi(it.token, it.ids.size) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryUiState())

    @Suppress("CyclomaticComplexMethod") // Плоский разбор действий экрана.
    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.SetFilter -> inputs.update { it.copy(filter = action.filter) }
            is HistoryAction.RowClick -> rowClick(action.gameId)
            is HistoryAction.RowLongClick -> inputs.update { it.copy(selection = it.selection + action.gameId) }
            HistoryAction.ClearSelection -> inputs.update { it.copy(selection = emptySet(), deleteDialog = false) }
            HistoryAction.DeleteSelected -> inputs.update { it.copy(deleteDialog = it.selection.isNotEmpty()) }
            HistoryAction.ConfirmDelete -> confirmDelete()
            HistoryAction.DismissDialog -> inputs.update { it.copy(deleteDialog = false) }
            HistoryAction.OpenStats -> eventChannel.trySend(HistoryEvent.OpenStats)
            is HistoryAction.UndoDelete -> viewModelScope.launch { deletions.undo(action.token) }
            is HistoryAction.UndoWindow -> viewModelScope.launch { deletions.extend(action.token, action.millis) }
            is HistoryAction.SnackbarDismissed -> viewModelScope.launch { deletions.commitNow(action.token) }
        }
    }

    private fun rowClick(gameId: String) {
        val current = inputs.value
        if (current.selection.isEmpty()) {
            eventChannel.trySend(HistoryEvent.OpenGame(gameId))
        } else {
            val next = if (gameId in current.selection) current.selection - gameId else current.selection + gameId
            inputs.update { it.copy(selection = next) }
        }
    }

    private fun confirmDelete() {
        val ids = inputs.value.selection
        inputs.update { it.copy(selection = emptySet(), deleteDialog = false) }
        if (ids.isEmpty()) return
        viewModelScope.launch { deletions.schedule(ids.map { it.toGameId() }.toSet()) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
