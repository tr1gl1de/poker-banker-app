package app.feltbook.feature.handover.receive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.data.handover.ReceivedTemplateStore
import app.feltbook.core.domain.engine.DomainError
import app.feltbook.core.domain.engine.DomainRuleViolation
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.engine.GameTemplate
import app.feltbook.core.domain.engine.Handovers
import app.feltbook.core.domain.engine.ImportConflictReason
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.repository.CommitRejectedException
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.SettingsRepository
import app.feltbook.core.domain.repository.commit
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import app.feltbook.core.transfer.FailureReason
import app.feltbook.core.transfer.TransferEnvironment
import app.feltbook.core.transfer.TransferSession
import app.feltbook.core.transfer.TransferState
import app.feltbook.core.transfer.file.IncomingPackageStore
import app.feltbook.feature.handover.HandoverRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * «Принять игру» (ТЗ §6 шаги 1–5): реклама под своим именем, код, пакет, проверка (`FeltbookCodec.decode`), сводка,
 * «Я играю как…», удержание «Принять» → `Handovers.accept` → запись → ACK отправителю. Тот же экран открывает файл
 * (`HandoverRoute.fromFile`, пакет — в [IncomingPackageStore]); тогда ACK нет. Журнал после приёма показывает акт как
 * «Игра принята от <имя>» (строка `Handover`, отдельной записи не нужно).
 */
@Suppress("TooManyFunctions", "ReturnCount", "CyclomaticComplexMethod") // По обработчику на действие экрана.
@HiltViewModel
class ReceiveHandoverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository,
    private val session: TransferSession,
    private val environment: TransferEnvironment,
    private val incomingStore: IncomingPackageStore,
    private val templateStore: ReceivedTemplateStore,
) : ViewModel() {
    private val fromFile: Boolean = savedStateHandle.get<Boolean>(HandoverRoute.ARG_FROM_FILE) ?: false

    private val mutableState = MutableStateFlow(ReceiveUiState(fromFile = fromFile))
    val uiState: StateFlow<ReceiveUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<ReceiveEvent>(Channel.BUFFERED)
    val events: Flow<ReceiveEvent> = eventChannel.receiveAsFlow()

    private var incoming: FeltbookPackage? = null
    private var active = false
    private var localName: String? = null

    init {
        if (fromFile) {
            val file = incomingStore.pending.value
            if (file == null) {
                setPhase(ReceivePhase.Failed(ReceiveFailure.NOT_FELTBOOK))
            } else {
                onDecoded(file.result, file.fileName)
            }
        } else {
            viewModelScope.launch {
                localName = settingsRepository.settings.first().lastBankerName.let(PlayerNames::normalize)
                if (active) start()
            }
            viewModelScope.launch { session.state.collect(::onTransfer) }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: ReceiveAction) {
        when (action) {
            ReceiveAction.Activate -> {
                active = true
                if (!fromFile && localName != null && mutableState.value.phase == ReceivePhase.Loading) start()
            }

            ReceiveAction.Deactivate -> {
                active = false
                if (!fromFile && mutableState.value.phase != ReceivePhase.Review) {
                    session.cancel()
                    setPhase(ReceivePhase.Loading)
                }
            }

            is ReceiveAction.NameChanged -> mutableState.update { state ->
                val phase = state.phase as? ReceivePhase.NeedsName ?: return@update state
                state.copy(phase = phase.copy(name = action.name, error = false))
            }

            ReceiveAction.NameConfirmed -> confirmName()

            ReceiveAction.AllowPermissions -> {
                val missing = environment.missingPermissions()
                if (missing.isEmpty()) start() else eventChannel.trySend(ReceiveEvent.RequestPermissions(missing))
            }

            is ReceiveAction.PermissionsResult -> if (action.allGranted) {
                start()
            } else {
                setPhase(ReceivePhase.Failed(ReceiveFailure.PERMISSION_DENIED))
            }

            ReceiveAction.Retry -> start()

            ReceiveAction.ConfirmCode -> session.confirmCode()

            ReceiveAction.RejectCode -> session.rejectCode()

            is ReceiveAction.PlayAs -> mutableState.update { it.copy(playAs = action.playerId, error = null) }

            ReceiveAction.Accept -> accept()

            ReceiveAction.Decline -> decline()
        }
    }

    private fun confirmName() {
        val phase = mutableState.value.phase as? ReceivePhase.NeedsName ?: return
        val name = PlayerNames.normalize(phase.name)
        if (name.isEmpty() || name.length > PlayerNames.MAX_LENGTH) {
            setPhase(phase.copy(error = true))
            return
        }
        localName = name
        viewModelScope.launch { settingsRepository.update { it.copy(lastBankerName = name) } }
        start()
    }

    /** Имя → Play Services → разрешения → геолокация (Android ≤ 11) → реклама. */
    private fun start() {
        if (fromFile) return
        val name = localName ?: return
        incoming = null
        mutableState.update { it.copy(summary = null, error = null, playAs = null) }
        val phase = when {
            name.isEmpty() -> ReceivePhase.NeedsName("")
            !environment.isNearbyAvailable() -> ReceivePhase.Failed(ReceiveFailure.NO_GMS)
            environment.missingPermissions().isNotEmpty() -> ReceivePhase.NeedsPermission
            environment.isLocationServiceRequiredButOff() -> ReceivePhase.Failed(ReceiveFailure.LOCATION_OFF)
            else -> null
        }
        if (phase != null) {
            setPhase(phase)
            return
        }
        setPhase(ReceivePhase.Waiting(name))
        session.advertise(name)
    }

    private fun onTransfer(transfer: TransferState) {
        if (!active) return
        if (mutableState.value.phase == ReceivePhase.Review || mutableState.value.phase == ReceivePhase.Done) return
        when (transfer) {
            is TransferState.Advertising -> setPhase(ReceivePhase.Waiting(transfer.localName))

            is TransferState.AwaitingCodeConfirm ->
                setPhase(ReceivePhase.Code(transfer.endpoint.name, transfer.digits, transfer.confirmedLocally))

            is TransferState.Connected -> setPhase(ReceivePhase.Receiving(transfer.endpoint.name, null))

            is TransferState.Receiving ->
                setPhase(ReceivePhase.Receiving(transfer.endpoint.name, transfer.progress.fraction))

            is TransferState.AwaitingAccept -> onDecoded(FeltbookCodec.decode(transfer.packageText), fileName = null)

            is TransferState.Failed -> setPhase(
                ReceivePhase.Failed(
                    when (transfer.reason) {
                        FailureReason.NEARBY_UNAVAILABLE -> ReceiveFailure.NO_GMS
                        FailureReason.PERMISSION_DENIED -> ReceiveFailure.PERMISSION_DENIED
                        FailureReason.CORRUPTED -> ReceiveFailure.CORRUPTED
                        else -> ReceiveFailure.CONNECTION
                    },
                ),
            )

            else -> Unit
        }
    }

    private fun onDecoded(result: DecodeResult, fileName: String?) {
        val failure = when (result) {
            is DecodeResult.Ok -> null

            DecodeResult.NotFeltbook -> ReceiveFailure.NOT_FELTBOOK

            is DecodeResult.UnsupportedVersion -> ReceiveFailure.NEWER_VERSION

            DecodeResult.ChecksumMismatch, is DecodeResult.Malformed, is DecodeResult.InvalidLedger ->
                ReceiveFailure.CORRUPTED

            is DecodeResult.WrongKind -> ReceiveFailure.NOT_FELTBOOK
        }
        if (failure != null || result !is DecodeResult.Ok) {
            if (!fromFile) session.decline()
            setPhase(ReceivePhase.Failed(failure ?: ReceiveFailure.NOT_FELTBOOK))
            return
        }
        val summary = when (val pkg = result.pkg) {
            is FeltbookPackage.HandoverPackage -> {
                val state = GameStates.compute(pkg.game)
                IncomingSummary(
                    isTemplate = false,
                    title = pkg.game.game.title,
                    fromBanker = pkg.handover.fromBanker,
                    playersAtTable = state.table.playersAtTable,
                    chipsInPlay = state.table.chipsInPlay,
                    cash = pkg.handover.cashHandedOver,
                    players = state.players.filterNot { it.isVoid }
                        .map { PlayerOption(it.player.id.value.toString(), it.player.name) },
                    rules = pkg.game.game.rules,
                    fileName = fileName,
                )
            }

            is FeltbookPackage.TemplatePackage -> templateSummary(pkg.template, fileName)

            is FeltbookPackage.BackupPackage -> null
        }
        if (summary == null) {
            if (!fromFile) session.decline()
            setPhase(ReceivePhase.Failed(ReceiveFailure.BACKUP))
            return
        }
        incoming = result.pkg
        mutableState.update { it.copy(phase = ReceivePhase.Review, summary = summary, playAs = null, error = null) }
    }

    private fun templateSummary(template: GameTemplate, fileName: String?) = IncomingSummary(
        isTemplate = true,
        title = "",
        fromBanker = template.fromBanker,
        rules = template.rules,
        templateNames = template.playerNames.size,
        fileName = fileName,
    )

    private fun accept() {
        val pkg = incoming ?: return
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val error = when (pkg) {
                is FeltbookPackage.HandoverPackage -> acceptGame(pkg)
                is FeltbookPackage.TemplatePackage -> acceptTemplate(pkg.template)
                is FeltbookPackage.BackupPackage -> AcceptError.INVALID
            }
            if (error != null) {
                mutableState.update { it.copy(busy = false, error = error) }
                return@launch
            }
            if (!fromFile) session.acknowledge()
            incomingStore.clear()
            mutableState.update { it.copy(busy = false, phase = ReceivePhase.Done) }
            eventChannel.trySend(
                when (pkg) {
                    is FeltbookPackage.HandoverPackage -> ReceiveEvent.GameAccepted(pkg.game.game.id.value.toString())
                    else -> ReceiveEvent.TemplateAccepted
                },
            )
        }
    }

    private suspend fun acceptGame(pkg: FeltbookPackage.HandoverPackage): AcceptError? {
        val gameId = pkg.game.game.id
        val playAs = mutableState.value.playAs?.let { PlayerId(Uuid.parse(it)) }
        return try {
            val change = Handovers.accept(
                incoming = pkg.game,
                handover = pkg.handover,
                local = gameRepository.getGame(gameId),
                activeGame = gameRepository.getActiveGame()?.game,
                receiverPlayerId = playAs,
            )
            if (!change.isEmpty()) gameRepository.commit(change)
            settingsRepository.update { settings ->
                if (settings.lastBankerName.isBlank()) {
                    settings.copy(lastBankerName = pkg.handover.toBanker)
                } else {
                    settings
                }
            }
            null
        } catch (violation: DomainRuleViolation) {
            when (val error = violation.error) {
                is DomainError.AnotherGameActive -> AcceptError.ANOTHER_GAME_ACTIVE

                is DomainError.ImportConflict -> when (error.reason) {
                    ImportConflictReason.SECOND_ACTIVE_GAME -> AcceptError.ANOTHER_GAME_ACTIVE
                    ImportConflictReason.INVALID_LEDGER -> AcceptError.INVALID
                    else -> AcceptError.CONFLICT
                }

                else -> AcceptError.INVALID
            }
        } catch (rejected: CommitRejectedException) {
            when (rejected.reason) {
                CommitRejectedException.Reason.SECOND_ACTIVE_GAME -> AcceptError.ANOTHER_GAME_ACTIVE
                CommitRejectedException.Reason.STALE_SEQ -> AcceptError.CONFLICT
                else -> AcceptError.STORAGE
            }
        }
    }

    /** Сценарий 2: правила — в «Новую игру» (`rulesForNewGame`), имена — подсказки через [ReceivedTemplateStore]. */
    private suspend fun acceptTemplate(template: GameTemplate): AcceptError? {
        if (template.rules.problems().isNotEmpty()) return AcceptError.INVALID
        settingsRepository.update { it.copy(rulesForNewGame = template.rules) }
        templateStore.put(template)
        return null
    }

    private fun decline() {
        if (fromFile) {
            incomingStore.clear()
            eventChannel.trySend(ReceiveEvent.Closed)
            return
        }
        session.decline()
        incoming = null
        mutableState.update { it.copy(summary = null, error = null) }
        start()
    }

    private fun setPhase(phase: ReceivePhase) {
        mutableState.update { it.copy(phase = phase) }
    }

    override fun onCleared() {
        session.close()
    }
}

private fun GameChange.isEmpty(): Boolean =
    game == null && players.isEmpty() && entries.isEmpty() && handovers.isEmpty()
