package app.feltbook.feature.history.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.Debts
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.PlayerNameRepository
import app.feltbook.core.domain.repository.SettingsRepository
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.UiText
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.CommitOutcome
import app.feltbook.feature.history.common.GameDeletionScheduler
import app.feltbook.feature.history.common.key
import app.feltbook.feature.history.common.messageOrNull
import app.feltbook.feature.history.common.toGameId
import app.feltbook.feature.history.common.toOperationId
import app.feltbook.feature.history.common.toPlayerId
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class DebtsInputs(
    val paySheet: DebtPaySheetUi? = null,
    val forgiveKey: String? = null,
    val busy: Boolean = false,
)

/**
 * Открытые долги (m3-ui-spec §5.9, ТЗ §3.7): всё — `Debts.open` по всем играм.
 * «Погасить» — PAYMENT в ту игру, где возник долг (`Commands.pay` на её снимке; `bankerName` — банкир активной игры
 * или `lastBankerName`), «Простить…» — `Commands.forgiveDebt` (весь долг). Обе — обратимые: snackbar «Отменить» 5 с
 * пишет `Commands.reverse` (в закрытой игре движок разрешает отмену именно этих операций).
 */
@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    names: PlayerNameRepository,
    private val settings: SettingsRepository,
    deletions: GameDeletionScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val inputs = MutableStateFlow(DebtsInputs())

    private val eventChannel = Channel<DebtsEvent>(Channel.BUFFERED)
    val events: Flow<DebtsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<DebtsUiState> =
        combine(gameRepository.observeAllGames(), names.observeAll(), deletions.pending, inputs) {
                games,
                directory,
                pending,
                input,
            ->
            val hidden = pending?.ids.orEmpty()
            val debts = Debts.open(games.filter { it.game.id !in hidden }, directory)
            buildDebtsUiState(debts, clock.now(), input.paySheet, input.forgiveKey, input.busy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DebtsUiState())

    @Suppress("CyclomaticComplexMethod") // Плоский разбор действий экрана.
    fun onAction(action: DebtsAction) {
        when (action) {
            DebtsAction.Back -> eventChannel.trySend(DebtsEvent.Back)

            is DebtsAction.OpenGame -> eventChannel.trySend(DebtsEvent.OpenGame(action.gameId))

            is DebtsAction.Pay -> card(action.key)?.let { card ->
                inputs.update { it.copy(paySheet = DebtPaySheetUi(card, AmountEntry.preset(card.amount))) }
            }

            is DebtsAction.PayEntry -> updateSheet { it.copy(entry = action.entry) }

            DebtsAction.PayAll -> updateSheet { it.copy(entry = AmountEntry.preset(it.card.amount)) }

            DebtsAction.PayHalf -> updateSheet { it.copy(entry = AmountEntry.preset((it.card.amount + 1) / 2)) }

            DebtsAction.ConfirmPay -> pay()

            DebtsAction.DismissSheet -> inputs.update { it.copy(paySheet = null) }

            is DebtsAction.Forgive -> inputs.update { it.copy(forgiveKey = card(action.key)?.key) }

            DebtsAction.ConfirmForgive -> forgive()

            DebtsAction.DismissDialog -> inputs.update { it.copy(forgiveKey = null) }

            is DebtsAction.Undo -> undo(action.token)
        }
    }

    private fun card(key: String): DebtCardUi? = uiState.value.cards.firstOrNull { it.key == key }

    private fun updateSheet(transform: (DebtPaySheetUi) -> DebtPaySheetUi) {
        inputs.update { input -> input.copy(paySheet = input.paySheet?.let(transform)) }
    }

    private fun pay() {
        val sheet = inputs.value.paySheet ?: return
        if (!sheet.canConfirm || inputs.value.busy) return
        val card = sheet.card
        val amount = sheet.amount
        write(
            card = card,
            build = { snapshot ->
                Commands.pay(snapshot, card.playerId.toPlayerId(), amount, Stamp.now(clock), bankerName(snapshot))
            },
            message = uiText(R.string.debts_paid_snackbar, card.playerName, MoneyFormat.rub(amount)),
            after = { it.copy(paySheet = null) },
        )
    }

    private fun forgive() {
        val card = inputs.value.forgiveKey?.let(::card) ?: return
        if (inputs.value.busy) return
        inputs.update { it.copy(forgiveKey = null) }
        write(
            card = card,
            build = { snapshot -> Commands.forgiveDebt(snapshot, card.playerId.toPlayerId(), null, Stamp.now(clock)) },
            message = uiText(R.string.debts_forgiven_snackbar),
            after = { it },
        )
    }

    /** Команда по свежему снимку старой игры → одна транзакция → snackbar с отменой этой операции. */
    private fun write(
        card: DebtCardUi,
        build: suspend (GameSnapshot) -> GameChange,
        message: UiText,
        after: (DebtsInputs) -> DebtsInputs,
    ) {
        inputs.update { it.copy(busy = true) }
        viewModelScope.launch {
            var change: GameChange? = null
            val outcome = gameRepository.tryCommit {
                val snapshot = checkNotNull(gameRepository.getGame(card.gameId.toGameId()))
                listOf(build(snapshot).also { change = it })
            }
            inputs.update { after(it).copy(busy = false) }
            val operation = change?.entries?.firstOrNull()?.operationId
            if (outcome == CommitOutcome.Ok && operation != null) {
                eventChannel.send(DebtsEvent.Undoable(message, DebtsUndo(card.gameId, operation.key())))
            } else {
                outcome.messageOrNull()?.let { eventChannel.send(DebtsEvent.Message(it)) }
            }
        }
    }

    private fun undo(token: DebtsUndo) {
        viewModelScope.launch {
            val outcome = gameRepository.tryCommit {
                val snapshot = checkNotNull(gameRepository.getGame(token.gameId.toGameId()))
                listOf(Commands.reverse(snapshot, token.operationId.toOperationId(), null, Stamp.now(clock)))
            }
            outcome.messageOrNull()?.let { eventChannel.send(DebtsEvent.Message(it)) }
        }
    }

    /** Кто принял деньги: банкир активной игры, иначе имя банкира телефона, иначе банкир той игры. */
    private suspend fun bankerName(snapshot: GameSnapshot): String {
        val active = gameRepository.getActiveGame()?.game?.currentBankerName?.takeIf { it.isNotBlank() }
        val last = settings.settings.first().lastBankerName.takeIf { it.isNotBlank() }
        return active ?: last ?: snapshot.game.currentBankerName
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
