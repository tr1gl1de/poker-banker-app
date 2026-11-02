package app.feltbook.feature.handover.send

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.domain.engine.DomainRuleViolation
import app.feltbook.core.domain.engine.Handovers
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.engine.PreparedHandover
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.repository.CommitRejectedException
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.commit
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import app.feltbook.core.transfer.FailureReason
import app.feltbook.core.transfer.TransferEnvironment
import app.feltbook.core.transfer.TransferSession
import app.feltbook.core.transfer.TransferState
import app.feltbook.core.transfer.file.PackageFileStore
import app.feltbook.feature.handover.HandoverRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * «Отдать игру» (ТЗ §6, m3-ui-spec §5.10). Отправитель ищет, сверяет код, удержанием «Код совпадает» подтверждает и
 * отправляет пакет; записывает передачу (`senderChange`: INCOME_TAKEN? + акт + HANDED_OVER) только после ACK
 * получателя, явного «Файл отправлен» или «Новый банкир принял игру» (ADR 0007). Повторная отправка той же игры
 * тому же банкиру переиспользует подготовленную передачу — те же id, без расхождения журналов.
 */
// По обработчику на действие экрана; ранние выходы — действие не к месту.
@Suppress("TooManyFunctions", "ReturnCount", "ComplexCondition", "CyclomaticComplexMethod")
@HiltViewModel
class SendHandoverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val session: TransferSession,
    private val environment: TransferEnvironment,
    private val fileStore: PackageFileStore,
    private val clock: Clock,
) : ViewModel() {
    private val gameId: GameId? =
        savedStateHandle.get<String>(HandoverRoute.ARG_GAME_ID)?.let { GameId(Uuid.parse(it)) }

    private val mutableState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<SendEvent>(Channel.BUFFERED)
    val events: Flow<SendEvent> = eventChannel.receiveAsFlow()

    private var snapshot: GameSnapshot? = null
    private var prepared: PreparedHandover? = null
    private var active = false

    /** Передача записывается или записана здесь — HANDED_OVER из базы не показывать как «уже передана». */
    private var handingOver = false
    private val autoConnected = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val games = if (gameId != null) {
                gameRepository.observeGame(gameId)
            } else {
                // Без id — активная игра; её id фиксируется, чтобы после передачи экран видел HANDED_OVER.
                val id = gameRepository.getActiveGame()?.game?.id
                if (id == null) flowOf(null) else gameRepository.observeGame(id)
            }
            games.collect(::onSnapshot)
        }
        viewModelScope.launch { session.state.collect(::onTransfer) }
    }

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: SendAction) {
        when (action) {
            SendAction.Activate -> activate()

            SendAction.Deactivate -> deactivate()

            SendAction.AllowPermissions -> {
                val missing = environment.missingPermissions()
                if (missing.isEmpty()) start() else eventChannel.trySend(SendEvent.RequestPermissions(missing))
            }

            is SendAction.PermissionsResult -> if (action.allGranted) {
                start()
            } else {
                setPhase(SendPhase.Failed(SendFailure.PERMISSION_DENIED))
            }

            SendAction.Retry -> start()

            is SendAction.SelectDevice -> session.connect(action.endpointId)

            is SendAction.KeepIncome ->
                mutableState.update { it.copy(keepIncome = action.keep && it.keepIncomeEnabled) }

            SendAction.ConfirmAndSend -> session.confirmCode()

            SendAction.RejectCode -> session.rejectCode()

            SendAction.SendFile -> sendFile()

            is SendAction.FileRecipientChanged -> mutableState.update { state ->
                val dialog = state.dialog as? SendDialog.FileRecipient ?: return@update state
                state.copy(dialog = dialog.copy(name = action.name, error = false))
            }

            SendAction.FileRecipientConfirmed -> confirmFileRecipient()

            SendAction.FileSentConfirmed -> commitHandover(fromFile = true)

            SendAction.OpenAckLost -> mutableState.update { it.copy(dialog = SendDialog.AckLost) }

            SendAction.AckLostConfirmed -> commitHandover(fromFile = false)

            SendAction.DismissDialog -> mutableState.update { it.copy(dialog = null) }

            SendAction.Finish -> eventChannel.trySend(SendEvent.Finished)
        }
    }

    private fun onSnapshot(snap: GameSnapshot?) {
        snapshot = snap
        val phase = mutableState.value.phase
        val handedOverHere = handingOver || phase is SendPhase.Done
        mutableState.update { state ->
            when {
                snap == null -> state.copy(phase = SendPhase.NoGame)

                snap.game.status == GameStatus.HANDED_OVER && !handedOverHere ->
                    state.copy(phase = SendPhase.AlreadyHandedOver, gameTitle = snap.game.title)

                snap.game.status == GameStatus.CLOSED -> {
                    val template = Handovers.template(snap)
                    state.copy(
                        kind = SendKind.TEMPLATE,
                        gameTitle = snap.game.title,
                        rules = template.rules,
                        templateNames = template.playerNames.size,
                        act = null,
                        phase = if (phase == SendPhase.Loading) SendPhase.Searching else phase,
                    )
                }

                snap.game.status.isActive -> {
                    val act = Handovers.act(snap)
                    state.copy(
                        kind = SendKind.HANDOVER,
                        gameTitle = snap.game.title,
                        act = act,
                        rules = snap.game.rules,
                        keepIncome = state.keepIncome && act.incomeTakeable > 0,
                        phase = if (phase == SendPhase.Loading) SendPhase.Searching else phase,
                    )
                }

                handedOverHere -> state

                else -> state.copy(phase = SendPhase.NoGame)
            }
        }
        if (active && phase == SendPhase.Loading && canTransfer()) start()
    }

    private fun canTransfer(): Boolean {
        val phase = mutableState.value.phase
        return snapshot != null && phase != SendPhase.NoGame && phase != SendPhase.AlreadyHandedOver &&
            phase !is SendPhase.Done
    }

    private fun activate() {
        active = true
        if (mutableState.value.phase == SendPhase.Loading) return
        if (canTransfer()) start()
    }

    private fun deactivate() {
        active = false
        session.cancel()
    }

    /** Проверки по порядку: Play Services → разрешения → геолокация (Android ≤ 11) → поиск. */
    private fun start() {
        if (!canTransfer()) return
        val snap = snapshot ?: return
        val phase = when {
            !environment.isNearbyAvailable() -> SendPhase.Failed(SendFailure.NO_GMS)
            environment.missingPermissions().isNotEmpty() -> SendPhase.NeedsPermission
            environment.isLocationServiceRequiredButOff() -> SendPhase.Failed(SendFailure.LOCATION_OFF)
            else -> null
        }
        if (phase != null) {
            setPhase(phase)
            return
        }
        autoConnected.clear()
        setPhase(SendPhase.Searching)
        session.discover(snap.game.currentBankerName)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun onTransfer(transfer: TransferState) {
        if (!active && transfer !is TransferState.Done) return
        when (transfer) {
            TransferState.Idle, is TransferState.Advertising, is TransferState.Receiving,
            is TransferState.AwaitingAccept,
            -> Unit

            TransferState.Discovering -> setPhase(SendPhase.Searching)

            is TransferState.Found -> {
                setPhase(SendPhase.Choose(transfer.endpoints))
                val only = transfer.endpoints.singleOrNull()
                if (only != null && autoConnected.add(only.id)) session.connect(only.id)
            }

            is TransferState.Connecting -> setPhase(SendPhase.Connecting(transfer.endpoint.name))

            is TransferState.AwaitingCodeConfirm ->
                setPhase(SendPhase.Code(transfer.endpoint.name, transfer.digits, transfer.confirmedLocally))

            is TransferState.Connected -> sendPackage(transfer.endpoint.name)

            is TransferState.Sending ->
                setPhase(SendPhase.Sending(transfer.endpoint.name, transfer.progress.fraction))

            is TransferState.AwaitingAck -> setPhase(SendPhase.AwaitingAccept(transfer.endpoint.name))

            is TransferState.Done -> when (mutableState.value.kind) {
                SendKind.HANDOVER -> commitHandover(fromFile = false)
                SendKind.TEMPLATE -> setPhase(SendPhase.Done(transfer.endpoint.name))
            }

            is TransferState.Failed -> setPhase(
                when (transfer.reason) {
                    FailureReason.NEARBY_UNAVAILABLE -> SendPhase.Failed(SendFailure.NO_GMS)

                    FailureReason.PERMISSION_DENIED -> SendPhase.Failed(SendFailure.PERMISSION_DENIED)

                    FailureReason.DECLINED -> SendPhase.Failed(SendFailure.DECLINED)

                    else -> SendPhase.Failed(
                        SendFailure.CONNECTION,
                        ackLost = transfer.packageSent && mutableState.value.kind == SendKind.HANDOVER,
                    )
                },
            )
        }
    }

    private fun sendPackage(peerName: String) {
        val text = try {
            encodePackage(peerName)
        } catch (_: DomainRuleViolation) {
            session.cancel()
            setPhase(SendPhase.Failed(SendFailure.BAD_NAME))
            return
        } ?: return
        session.send(text)
    }

    /** Текст пакета; для передачи посреди игры — с подготовленной (ещё не записанной) передачей. */
    private fun encodePackage(toBanker: String): String? {
        val snap = snapshot ?: return null
        val pkg = when (mutableState.value.kind) {
            SendKind.HANDOVER -> {
                val handover = prepare(snap, toBanker)
                FeltbookPackage.HandoverPackage(handover.outgoing, handover.handover)
            }

            SendKind.TEMPLATE -> FeltbookPackage.TemplatePackage(Handovers.template(snap))
        }
        return FeltbookCodec.encode(pkg, clock.now())
    }

    private fun prepare(snap: GameSnapshot, toBanker: String): PreparedHandover {
        val keep = mutableState.value.let { it.keepIncome && it.keepIncomeEnabled }
        val existing = prepared
        val name = PlayerNames.normalize(toBanker)
        if (existing != null && existing.handover.toBanker == name && existing.senderChange.baseSeq == snap.lastSeq &&
            (existing.handover.incomeTaken > 0) == keep
        ) {
            return existing
        }
        return Handovers.prepare(snap, toBanker, keep, Stamp.now(clock)).also { prepared = it }
    }

    private fun sendFile() {
        val state = mutableState.value
        if (state.busy || snapshot == null) return
        when (state.kind) {
            SendKind.HANDOVER -> {
                val known = prepared?.handover?.toBanker.orEmpty()
                mutableState.update { it.copy(dialog = SendDialog.FileRecipient(known)) }
            }

            SendKind.TEMPLATE -> exportFile(toBanker = "")
        }
    }

    private fun confirmFileRecipient() {
        val dialog = mutableState.value.dialog as? SendDialog.FileRecipient ?: return
        val name = PlayerNames.normalize(dialog.name)
        if (name.isEmpty() || name.length > PlayerNames.MAX_LENGTH) {
            mutableState.update { it.copy(dialog = dialog.copy(error = true)) }
            return
        }
        exportFile(name)
    }

    private fun exportFile(toBanker: String) {
        session.cancel()
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val text = try {
                encodePackage(toBanker)
            } catch (_: DomainRuleViolation) {
                mutableState.update { state ->
                    val dialog = state.dialog as? SendDialog.FileRecipient
                    state.copy(busy = false, dialog = dialog?.copy(error = true))
                }
                return@launch
            }
            if (text == null) {
                mutableState.update { it.copy(busy = false) }
                return@launch
            }
            val file = try {
                fileStore.writeForShare(text, mutableState.value.gameTitle)
            } catch (_: IOException) {
                mutableState.update { it.copy(busy = false, dialog = null) }
                setPhase(SendPhase.Failed(SendFailure.CONNECTION))
                return@launch
            }
            val kind = mutableState.value.kind
            mutableState.update {
                it.copy(busy = false, dialog = if (kind == SendKind.HANDOVER) SendDialog.FileSent else null)
            }
            eventChannel.trySend(SendEvent.Share(file))
        }
    }

    /** Запись передачи у отправителя: после ACK, «Файл отправлен» или «Новый банкир принял игру». */
    private fun commitHandover(fromFile: Boolean) {
        val handover = prepared ?: return
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, dialog = null) }
        if (fromFile) session.cancel()
        handingOver = true
        viewModelScope.launch {
            val phase = try {
                gameRepository.commit(handover.senderChange)
                prepared = null
                SendPhase.Done(handover.handover.toBanker)
            } catch (_: CommitRejectedException) {
                handingOver = false
                SendPhase.Failed(SendFailure.STALE)
            }
            mutableState.update { it.copy(busy = false, phase = phase) }
        }
    }

    private fun setPhase(phase: SendPhase) {
        mutableState.update { it.copy(phase = phase) }
    }

    override fun onCleared() {
        session.close()
    }
}
