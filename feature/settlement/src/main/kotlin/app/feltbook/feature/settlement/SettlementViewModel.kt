package app.feltbook.feature.settlement

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.domain.engine.CloseConfirmation
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.DomainError
import app.feltbook.core.domain.engine.DomainRuleViolation
import app.feltbook.core.domain.engine.GameSettlement
import app.feltbook.core.domain.engine.Settlements
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.repository.CommitRejectedException
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.commit
import app.feltbook.core.ui.component.Suit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Итог игры (m3-ui-spec §5.8): всё — из `Settlements.compute`. */
@Immutable
data class SettlementUiState(
    val loading: Boolean = true,
    val gameId: String = "",
    val title: String = "",
    val status: GameStatus = GameStatus.SETTLING,
    val createdAt: Instant = Instant.fromEpochMilliseconds(0),
    val duration: Duration = Duration.ZERO,
    val settlement: GameSettlement? = null,
    /** Масть по порядку посадки (как на столе). */
    val suits: Map<String, Suit> = emptyMap(),
    val chipsDialog: Boolean = false,
    val busy: Boolean = false,
) {
    val hasDebts: Boolean get() = settlement?.unpaidDebts?.isNotEmpty() == true

    val stillPlaying: List<String>
        get() = settlement?.blockers?.filterIsInstance<DomainError.PlayersStillPlaying>()?.firstOrNull()
            ?.playerIds?.map { it.value.toString() }.orEmpty()

    val canClose: Boolean
        get() = settlement != null && settlement.blockers.isEmpty() && status == GameStatus.SETTLING && !busy
}

sealed interface SettlementAction {
    data object Back : SettlementAction

    /** Hold «Рейк отдан · закрыть игру» / «Закрыть игру с долгом». */
    data object Close : SettlementAction

    data object ConfirmChipsMismatch : SettlementAction

    data object DismissDialog : SettlementAction

    data object Share : SettlementAction

    data class CashOut(val playerId: String) : SettlementAction
}

sealed interface SettlementEvent {
    data object Closed : SettlementEvent

    data class Error(val messageRes: Int) : SettlementEvent
}

@HiltViewModel
class SettlementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val clock: Clock,
) : ViewModel() {
    private val gameId = GameId(Uuid.parse(checkNotNull(savedStateHandle.get<String>(ARG_GAME_ID)) { "gameId" }))
    private val dialog = MutableStateFlow(false)
    private val busy = MutableStateFlow(false)
    private val eventChannel = Channel<SettlementEvent>(Channel.BUFFERED)
    val events: Flow<SettlementEvent> = eventChannel.receiveAsFlow()

    private val snapshot = gameRepository.observeGame(gameId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val ticker = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MILLIS)
        }
    }

    val uiState: StateFlow<SettlementUiState> =
        combine(snapshot, ticker, dialog, busy) { snap, now, dialogShown, isBusy ->
            buildSettlementUiState(snap, now).copy(chipsDialog = dialogShown, busy = isBusy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettlementUiState())

    fun onAction(action: SettlementAction) {
        when (action) {
            SettlementAction.Close -> {
                val snap = snapshot.value ?: return
                val settlement = Settlements.compute(snap)
                if (CloseConfirmation.CHIPS_NOT_RECONCILED in settlement.requiredConfirmations) {
                    dialog.update { true }
                } else {
                    close(snap, settlement)
                }
            }

            SettlementAction.ConfirmChipsMismatch -> {
                dialog.update { false }
                val snap = snapshot.value ?: return
                close(snap, Settlements.compute(snap))
            }

            SettlementAction.DismissDialog -> dialog.update { false }

            else -> Unit
        }
    }

    /** Подтверждения: hold-кнопка с подписью долга/долга банкира + диалог расхождения фишек — это и есть согласие. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Исход — сообщение экрану, не падение.
    private fun close(snap: GameSnapshot, settlement: GameSettlement) {
        if (busy.value) return
        busy.update { true }
        viewModelScope.launch {
            val error = try {
                val change = Commands.close(
                    snapshot = snap,
                    payRake = settlement.rakeDue > 0,
                    acknowledged = settlement.requiredConfirmations,
                    stamp = Stamp.now(clock),
                )
                gameRepository.commit(change)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainRuleViolation) {
                if (e.error is DomainError.WrongGameStatus) {
                    R.string.settlement_wrong_status
                } else {
                    R.string.settlement_error_generic
                }
            } catch (e: CommitRejectedException) {
                if (e.reason == CommitRejectedException.Reason.STALE_SEQ) 0 else R.string.settlement_error_generic
            } catch (e: Exception) {
                R.string.settlement_error_generic
            }
            busy.update { false }
            when (error) {
                null -> eventChannel.send(SettlementEvent.Closed)
                0 -> Unit
                else -> eventChannel.send(SettlementEvent.Error(error))
            }
        }
    }

    companion object {
        const val ARG_GAME_ID = "gameId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val TICK_MILLIS = 60_000L
    }
}

fun buildSettlementUiState(snapshot: GameSnapshot?, now: Instant): SettlementUiState {
    if (snapshot == null) return SettlementUiState()
    val game = snapshot.game
    val suits = snapshot.players.sortedBy { it.joinedAt }
        .mapIndexed { index, player -> player.id.value.toString() to Suit.forSeat(index) }
        .toMap()
    return SettlementUiState(
        loading = false,
        gameId = game.id.value.toString(),
        title = game.title,
        status = game.status,
        createdAt = game.createdAt,
        duration = (game.closedAt ?: now) - game.createdAt,
        settlement = Settlements.compute(snapshot),
        suits = suits,
    )
}
