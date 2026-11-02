package app.feltbook.feature.history.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.data.deletion.GameDeletionScheduler
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.DomainRuleViolation
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.format.UiText
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.CommitOutcome
import app.feltbook.feature.history.common.messageOrNull
import app.feltbook.feature.history.common.toGameId
import app.feltbook.feature.history.common.toOperationId
import app.feltbook.feature.history.common.toUiText
import app.feltbook.feature.history.common.tryCommit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Аргумент маршрута `GameDetailRoute.gameId`. */
internal const val ARG_GAME_ID = "gameId"

private sealed interface Loaded {
    data object Loading : Loaded

    data class Ready(val snapshot: GameSnapshot?) : Loaded
}

private data class DetailInputs(val dialog: DetailDialog? = null, val busy: Boolean = false)

/**
 * Карточка игры (m3-ui-spec §5.11): только чтение + «Переоткрыть для исправления» (`Commands.reopen`),
 * «Удалить игру…» (отложенно, [GameDeletionScheduler]) и отмена погашений/прощений в закрытой игре
 * (`Commands.reverse`, разрешена движком только для PAYMENT/PAYOUT/DEBT_FORGIVEN).
 */
@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val deletions: GameDeletionScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val gameId: GameId = checkNotNull(savedStateHandle.get<String>(ARG_GAME_ID)) { ARG_GAME_ID }.toGameId()

    private val eventChannel = Channel<GameDetailEvent>(Channel.BUFFERED)
    val events: Flow<GameDetailEvent> = eventChannel.receiveAsFlow()

    private val inputs = MutableStateFlow(DetailInputs())

    private val loaded: StateFlow<Loaded> = gameRepository.observeGame(gameId)
        .map<GameSnapshot?, Loaded> { Loaded.Ready(it) }
        .onStart { emit(Loaded.Loading) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Loaded.Loading)

    val uiState: StateFlow<GameDetailUiState> =
        combine(loaded, inputs) { load, input ->
            when (load) {
                Loaded.Loading -> GameDetailUiState()

                // Отложенное удаление не считается «игры нет»: экран уходит сам по событию Deleted.
                is Loaded.Ready ->
                    load.snapshot
                        ?.let { buildGameDetailUiState(it, clock.now(), input.dialog, input.busy) }
                        ?: GameDetailUiState(loading = false, missing = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GameDetailUiState())

    private val snapshot: GameSnapshot? get() = (loaded.value as? Loaded.Ready)?.snapshot

    fun onAction(action: GameDetailAction) {
        when (action) {
            GameDetailAction.Back -> eventChannel.trySend(GameDetailEvent.Back)

            GameDetailAction.Share -> eventChannel.trySend(GameDetailEvent.Share)

            GameDetailAction.Reopen -> openDialog(DetailDialog.Reopen)

            GameDetailAction.ConfirmReopen -> reopen()

            GameDetailAction.Delete -> openDialog(DetailDialog.Delete)

            GameDetailAction.ConfirmDelete -> delete()

            GameDetailAction.DismissDialog -> inputs.update { it.copy(dialog = null) }

            is GameDetailAction.JournalHold -> journalHold(action.operationId)

            is GameDetailAction.ReverseReason -> inputs.update { input ->
                val dialog = input.dialog as? DetailDialog.Reverse ?: return@update input
                input.copy(dialog = dialog.copy(reason = action.value.take(MAX_REASON_LENGTH)))
            }

            GameDetailAction.ConfirmReverse -> reverse()
        }
    }

    private fun openDialog(dialog: DetailDialog) {
        if (snapshot == null) return
        inputs.update { it.copy(dialog = dialog) }
    }

    private fun reopen() {
        if (inputs.value.busy) return
        inputs.update { it.copy(dialog = null, busy = true) }
        viewModelScope.launch {
            val outcome = gameRepository.tryCommit {
                val fresh = checkNotNull(gameRepository.getGame(gameId))
                listOf(Commands.reopen(fresh, gameRepository.getActiveGame()?.game))
            }
            inputs.update { it.copy(busy = false) }
            when (outcome) {
                CommitOutcome.Ok -> eventChannel.send(GameDetailEvent.OpenTable(gameId.value.toString()))
                else -> outcome.messageOrNull()?.let { eventChannel.send(GameDetailEvent.Message(it)) }
            }
        }
    }

    private fun delete() {
        val current = snapshot ?: return
        inputs.update { it.copy(dialog = null) }
        if (current.game.status.isActive) return
        viewModelScope.launch {
            deletions.schedule(setOf(gameId))
            eventChannel.send(GameDetailEvent.Deleted)
        }
    }

    private fun journalHold(operationId: String) {
        val current = snapshot ?: return
        val row = JournalRows.of(current).firstOrNull { it.id == operationId && !it.handover }
        when {
            current.game.status.isActive ->
                eventChannel.trySend(GameDetailEvent.Message(uiText(R.string.detail_journal_active_game)))

            row == null -> Unit

            row.reversible -> inputs.update { it.copy(dialog = DetailDialog.Reverse(operationId, row.title)) }

            else -> eventChannel.trySend(GameDetailEvent.Message(notReversibleReason(current, operationId)))
        }
    }

    /** Причину отказа даёт сам движок: команда чистая, без записи в базу. */
    private fun notReversibleReason(current: GameSnapshot, operationId: String): UiText = try {
        Commands.reverse(current, operationId.toOperationId(), null, Stamp.now(clock))
        uiText(UiR.string.error_wrong_status)
    } catch (e: DomainRuleViolation) {
        e.error.toUiText()
    }

    private fun reverse() {
        val dialog = inputs.value.dialog as? DetailDialog.Reverse ?: return
        if (inputs.value.busy) return
        inputs.update { it.copy(dialog = null, busy = true) }
        viewModelScope.launch {
            val outcome = gameRepository.tryCommit {
                val fresh = checkNotNull(gameRepository.getGame(gameId))
                listOf(Commands.reverse(fresh, dialog.operationId.toOperationId(), dialog.reason, Stamp.now(clock)))
            }
            inputs.update { it.copy(busy = false) }
            val message = if (outcome == CommitOutcome.Ok) {
                uiText(R.string.detail_journal_reversed_snackbar)
            } else {
                outcome.messageOrNull()
            }
            message?.let { eventChannel.send(GameDetailEvent.Message(it)) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_REASON_LENGTH = 120
    }
}
