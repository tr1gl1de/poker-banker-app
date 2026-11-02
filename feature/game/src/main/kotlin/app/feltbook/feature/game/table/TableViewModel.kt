package app.feltbook.feature.game.table

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.Debts
import app.feltbook.core.domain.engine.DomainRuleViolation
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.engine.SettlementChoice
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.apply
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.PlayerNameRepository
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.UiText
import app.feltbook.core.ui.format.pluralQuantity
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.game.R
import app.feltbook.feature.game.common.CommitOutcome
import app.feltbook.feature.game.common.key
import app.feltbook.feature.game.common.messageOrNull
import app.feltbook.feature.game.common.minuteTicker
import app.feltbook.feature.game.common.toGameId
import app.feltbook.feature.game.common.toOperationId
import app.feltbook.feature.game.common.toPlayerId
import app.feltbook.feature.game.common.toUiText
import app.feltbook.feature.game.common.tryCommit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Стол и его шторки (m3-ui-spec §5.3–§5.7). ADR 0006: снимок → `Commands.x` → `repository.commit`.
 * Защита от повторного нажатия: `busy` на время записи + `baseSeq` снимка, по которому нажали (второе нажатие
 * по тому же снимку даёт `STALE_SEQ` и молча игнорируется).
 */
// Один экран — один ViewModel со всеми шторками (m3-ui-spec §8.1).
@Suppress("TooManyFunctions", "LargeClass", "ReturnCount")
@HiltViewModel
class TableViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    playerNameRepository: PlayerNameRepository,
    private val clock: Clock,
) : ViewModel() {
    private val gameId: GameId = checkNotNull(savedStateHandle.get<String>(ARG_GAME_ID)) { "gameId" }.toGameId()

    private val eventChannel = Channel<TableEvent>(Channel.BUFFERED)
    val events: Flow<TableEvent> = eventChannel.receiveAsFlow()

    private val inputs = MutableStateFlow(TableInputs())

    private val snapshot: StateFlow<GameSnapshot?> = gameRepository.observeGame(gameId)
        .onEach { onSnapshot(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val games = gameRepository.observeAllGames().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val directory = playerNameRepository.observeAll().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList(),
    )

    val uiState: StateFlow<TableUiState> =
        combine(snapshot, minuteTicker(clock), inputs, games, directory) { snap, now, input, all, names ->
            buildTableUiState(snap, now, input, all, names)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TableUiState())

    private fun currentUi(): TableUiState =
        buildTableUiState(snapshot.value, clock.now(), inputs.value, games.value, directory.value)

    /** Состояние «прямо сейчас» без подписки на [uiState] (тесты). */
    @VisibleForTesting
    internal fun currentUiState(): TableUiState = currentUi()

    private suspend fun onSnapshot(snap: GameSnapshot?) {
        if (snap == null) {
            eventChannel.send(TableEvent.GameGone)
            return
        }
        val pending = savedStateHandle.get<String>(ARG_CASH_OUT)
        if (pending != null && savedStateHandle.get<Boolean>(KEY_CASH_OUT_HANDLED) != true) {
            savedStateHandle[KEY_CASH_OUT_HANDLED] = true
            inputs.update { it.copy(sheet = SheetInput.CashOut(pending.toPlayerId())) }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod") // Плоский разбор действий экрана.
    fun onAction(action: TableAction) {
        when (action) {
            is TableAction.RowClick -> openSheet(SheetInput.Card(action.playerId.toPlayerId()))

            is TableAction.SelectPlayer -> inputs.update { it.copy(selected = action.playerId.toPlayerId()) }

            is TableAction.SwipeBuyIn -> if (row(action.playerId)?.canBuyIn == true) openBuyIn(action.playerId)

            is TableAction.SwipeCashOut -> if (row(action.playerId)?.canCashOut == true) openCashOut(action.playerId)

            TableAction.AddPlayer -> if (currentUi().canAddPlayer) openSheet(SheetInput.NewPlayer())

            is TableAction.OpenBreakdown -> openSheet(SheetInput.Breakdown(action.kind))

            TableAction.Finish -> commitThen({ listOf(Commands.finish(it)) }) { send(TableEvent.OpenSettlement) }

            TableAction.Resume -> commitThen({ listOf(Commands.resume(it)) }) {}

            TableAction.ToSettlement -> send(TableEvent.OpenSettlement)

            TableAction.Handover -> send(TableEvent.OpenHandover)

            TableAction.DismissSheet -> inputs.update { it.copy(sheet = null) }

            is TableAction.EntryChange -> setEntry(action.entry)

            is TableAction.ChipSelect -> setEntry(AmountEntry.preset(action.amount))

            is TableAction.PaymentChange -> updateAnySheet { sheet ->
                when (sheet) {
                    is SheetInput.BuyIn -> sheet.copy(payment = action.payment)
                    is SheetInput.NewPlayer -> sheet.copy(payment = action.payment, paymentTouched = true)
                    else -> sheet
                }
            }

            TableAction.ConfirmBuyIn -> when (inputs.value.sheet) {
                is SheetInput.BuyIn -> confirmBuyIn()
                is SheetInput.NewPlayer -> confirmNewPlayer()
                else -> Unit
            }

            is TableAction.NameChange -> updateSheet<SheetInput.NewPlayer> {
                it.copy(name = action.name.take(MAX_NAME_INPUT), debtCollapsed = false)
            }

            is TableAction.SuggestionPick -> updateSheet<SheetInput.NewPlayer> {
                it.copy(name = action.name, amountFocused = true)
            }

            TableAction.FocusAmount -> updateSheet<SheetInput.NewPlayer> { it.copy(amountFocused = true) }

            TableAction.FocusName -> updateSheet<SheetInput.NewPlayer> { it.copy(amountFocused = false) }

            is TableAction.OpenExisting -> openSheet(SheetInput.Card(action.playerId.toPlayerId()))

            is TableAction.ReturnExisting -> openBuyIn(action.playerId)

            TableAction.DebtPayNow -> {
                val total = (currentUi().sheet as? NewPlayerUi)?.debt?.total ?: return
                updateSheet<SheetInput.NewPlayer> { it.copy(debtPay = AmountEntry.preset(total)) }
            }

            TableAction.DebtSeatWithDebt -> updateSheet<SheetInput.NewPlayer> { it.copy(debtCollapsed = true) }

            TableAction.DebtPayCancel -> updateSheet<SheetInput.NewPlayer> { it.copy(debtPay = null) }

            TableAction.DebtPayConfirm -> confirmDebtPay()

            is TableAction.CardBuyIn -> openBuyIn(action.playerId)

            is TableAction.CardPay -> openPay(action.playerId, payout = false)

            is TableAction.CardPayout -> openPay(action.playerId, payout = true)

            is TableAction.CardCashOut -> openCashOut(action.playerId)

            is TableAction.CardEarlyExit -> openSheet(
                SheetInput.CashOut(action.playerId.toPlayerId(), earlyConfirmed = true),
            )

            is TableAction.CardRename -> {
                val name = snapshot.value?.player(action.playerId.toPlayerId())?.name ?: return
                inputs.update { it.copy(dialog = DialogInput.Rename(action.playerId.toPlayerId(), name)) }
            }

            is TableAction.CardForgive ->
                inputs.update { it.copy(dialog = DialogInput.Forgive(action.playerId.toPlayerId())) }

            is TableAction.JournalHold -> onJournalHold(action.playerId, action.operationId)

            TableAction.PayAll -> presetPay(half = false)

            TableAction.PayHalf -> presetPay(half = true)

            TableAction.ConfirmPay -> confirmPay()

            TableAction.FocusChips -> updateSheet<SheetInput.CashOut> { it.copy(focus = CashOutFocus.CHIPS) }

            TableAction.KeypadDone -> updateSheet<SheetInput.CashOut> { it.copy(focus = CashOutFocus.NONE) }

            is TableAction.TipSelect -> updateSheet<SheetInput.CashOut> {
                it.copy(
                    tip = action.choice,
                    focus = if (action.choice == TipChoice.CUSTOM) CashOutFocus.TIP else CashOutFocus.NONE,
                    tipsExpanded = true,
                )
            }

            TableAction.ExpandTips -> updateSheet<SheetInput.CashOut> { it.copy(tipsExpanded = true) }

            TableAction.TipToBanker -> updateSheet<SheetInput.CashOut> { it.copy(tipTo = null) }

            TableAction.PickTipRecipient -> updateSheet<SheetInput.CashOut> { it.copy(picking = true) }

            is TableAction.TipRecipientPicked -> updateSheet<SheetInput.CashOut> {
                it.copy(tipTo = action.playerId.toPlayerId(), picking = false)
            }

            TableAction.CancelPicker -> updateSheet<SheetInput.CashOut> { it.copy(picking = false) }

            TableAction.SettleFull -> cashOut(SettlementChoice.Full)

            TableAction.OpenPartial -> updateSheet<SheetInput.CashOut> {
                it.copy(partial = AmountEntry(), focus = CashOutFocus.NONE)
            }

            TableAction.ClosePartial -> updateSheet<SheetInput.CashOut> { it.copy(partial = null) }

            TableAction.SettlePartial -> {
                val partial = (inputs.value.sheet as? SheetInput.CashOut)?.partial?.value ?: return
                cashOut(SettlementChoice.Partial(partial))
            }

            TableAction.SettleNone -> cashOut(SettlementChoice.None)

            is TableAction.ReasonChange -> inputs.update { s ->
                val dialog = s.dialog as? DialogInput.Reverse ?: return@update s
                s.copy(dialog = dialog.copy(reason = action.reason.take(MAX_REASON)))
            }

            is TableAction.RenameChange -> inputs.update { s ->
                val dialog = s.dialog as? DialogInput.Rename ?: return@update s
                s.copy(dialog = dialog.copy(value = action.value.take(MAX_NAME_INPUT), error = null))
            }

            TableAction.DialogConfirm -> confirmDialog()

            TableAction.DialogDismiss -> inputs.update { it.copy(dialog = null) }

            is TableAction.Undo -> undo(action.token)
        }
    }

    // ---------- Открытие шторок ----------

    private fun row(id: String): PlayerRowUi? = currentUi().rows.firstOrNull { it.id == id }

    private fun openSheet(sheet: SheetInput) = inputs.update { it.copy(sheet = sheet, dialog = null) }

    private fun openBuyIn(id: String) {
        if (!currentUi().canAddPlayer) return
        openSheet(SheetInput.BuyIn(id.toPlayerId()))
    }

    private fun openCashOut(id: String) = openSheet(SheetInput.CashOut(id.toPlayerId()))

    private fun openPay(id: String, payout: Boolean) {
        val card = currentUi().let { ui -> (ui.sheet as? PlayerCardUi)?.takeIf { it.playerId == id } ?: ui.detail }
        val amount = if (payout) card?.owedByBanker ?: 0L else card?.debt ?: 0L
        openSheet(SheetInput.Pay(id.toPlayerId(), payout, AmountEntry.preset(amount)))
    }

    private fun presetPay(half: Boolean) {
        when (val sheet = currentUi().sheet) {
            is PayUi -> {
                val amount = if (half) (sheet.current + 1) / 2 else sheet.current
                updateSheet<SheetInput.Pay> { it.copy(entry = AmountEntry.preset(amount)) }
            }

            is NewPlayerUi -> {
                val total = sheet.debt?.total ?: return
                val amount = if (half) (total + 1) / 2 else total
                updateSheet<SheetInput.NewPlayer> { it.copy(debtPay = AmountEntry.preset(amount)) }
            }

            else -> Unit
        }
    }

    private fun setEntry(entry: AmountEntry) = updateAnySheet { sheet ->
        when (sheet) {
            is SheetInput.BuyIn -> sheet.copy(entry = entry)

            is SheetInput.NewPlayer ->
                if (sheet.debtPay !=
                    null
                ) {
                    sheet.copy(debtPay = entry)
                } else {
                    sheet.copy(entry = entry, amountFocused = true)
                }

            is SheetInput.Pay -> sheet.copy(entry = entry)

            is SheetInput.CashOut -> when {
                sheet.partial != null -> sheet.copy(partial = entry)
                sheet.focus == CashOutFocus.TIP -> sheet.copy(customTip = entry)
                else -> sheet.copy(chips = entry, focus = CashOutFocus.CHIPS)
            }

            else -> sheet
        }
    }

    private fun updateAnySheet(transform: (SheetInput) -> SheetInput?) = inputs.update { s ->
        val sheet = s.sheet ?: return@update s
        s.copy(sheet = transform(sheet))
    }

    private inline fun <reified T : SheetInput> updateSheet(crossinline transform: (T) -> SheetInput) =
        inputs.update { s ->
            val sheet = s.sheet as? T ?: return@update s
            s.copy(sheet = transform(sheet))
        }

    // ---------- Записи ----------

    private fun stamp(): Stamp = Stamp.now(clock)

    private fun send(event: TableEvent) {
        viewModelScope.launch { eventChannel.send(event) }
    }

    /**
     * Строит изменения по снимку, по которому нажали, и пишет их. `STALE_SEQ` (повтор нажатия) — молча;
     * блокер движка — сообщением.
     */
    private fun commitThen(
        build: (GameSnapshot) -> List<GameChange>,
        onRejected: (suspend (CommitOutcome) -> Boolean)? = null,
        onOk: suspend (List<GameChange>) -> Unit,
    ) {
        if (inputs.value.busy) return
        val snap = snapshot.value ?: return
        inputs.update { it.copy(busy = true) }
        viewModelScope.launch {
            var built: List<GameChange> = emptyList()
            val outcome = gameRepository.tryCommit { build(snap).also { built = it } }
            inputs.update { it.copy(busy = false) }
            when (outcome) {
                CommitOutcome.Ok -> onOk(built)

                CommitOutcome.Stale -> Unit

                else -> if (onRejected?.invoke(outcome) != true) {
                    outcome.messageOrNull()?.let { eventChannel.send(TableEvent.Message(it)) }
                }
            }
        }
    }

    private fun operations(changes: List<GameChange>): List<Pair<String, String>> = changes.flatMap { change ->
        change.entries.map { it.operationId }.distinct().map { change.gameId.key() to it.key() }
    }

    private fun closeSheet() = inputs.update { it.copy(sheet = null, dialog = null) }

    private fun confirmBuyIn() {
        val input = inputs.value.sheet as? SheetInput.BuyIn ?: return
        val ui = currentUi().sheet as? BuyInUi ?: return
        if (!ui.canConfirm) return
        val chips = input.entry.value
        val payment = input.payment.normalized(ui.split)
        commitThen({ listOf(Commands.buyIn(it, input.playerId, chips, payment, stamp())) }) { changes ->
            closeSheet()
            eventChannel.send(TableEvent.ConfirmHaptic)
            val text = if (ui.isFirst) R.string.buy_in_first_done_snackbar else R.string.buy_in_done_snackbar
            eventChannel.send(
                TableEvent.Undoable(
                    uiText(text, MoneyFormat.number(chips), ui.name),
                    UndoToken.Reverse(operations(changes)),
                ),
            )
        }
    }

    private fun confirmNewPlayer() {
        val input = inputs.value.sheet as? SheetInput.NewPlayer ?: return
        val ui = currentUi().sheet as? NewPlayerUi ?: return
        if (!ui.canConfirm) return
        val chips = input.entry.value
        val payment = ui.payment.normalized(ui.split)
        val name = PlayerNames.normalize(input.name)
        commitThen({ listOf(Commands.addPlayer(it, name, chips, payment, stamp())) }) { changes ->
            closeSheet()
            eventChannel.send(TableEvent.ConfirmHaptic)
            val chipsText = UiText.Plural(
                UiR.plurals.chips_count,
                pluralQuantity(chips),
                listOf(MoneyFormat.number(chips)),
            )
            eventChannel.send(
                TableEvent.Undoable(
                    uiText(R.string.new_player_done_snackbar, name, chipsText),
                    UndoToken.Reverse(operations(changes)),
                ),
            )
        }
    }

    private fun confirmDebtPay() {
        val input = inputs.value.sheet as? SheetInput.NewPlayer ?: return
        val amount = input.debtPay?.value ?: return
        val current = snapshot.value ?: return
        if (amount <= 0 || inputs.value.busy) return
        val name = PlayerNames.normalize(input.name)
        val debts = Debts.forName(name, games.value, directory.value, excludeGameId = gameId).sortedBy { it.since }
        if (amount > debts.sumOf { it.amount }) return
        inputs.update { it.copy(busy = true) }
        viewModelScope.launch {
            val snaps = debts.map { it.gameId }.distinct()
                .mapNotNull { id -> gameRepository.getGame(id)?.let { id to it } }
                .toMap().toMutableMap()
            val outcome = gameRepository.tryCommit {
                val stamp = stamp()
                var left = amount
                debts.mapNotNull { debt ->
                    val part = minOf(left, debt.amount)
                    val snap = snaps[debt.gameId]
                    if (part <= 0 || snap == null) return@mapNotNull null
                    left -= part
                    Commands.pay(snap, debt.playerId, part, stamp, bankerName = current.game.currentBankerName)
                        .also { snaps[debt.gameId] = snap.apply(it) }
                }.also { built -> debtPayChanges = built }
            }
            inputs.update { it.copy(busy = false) }
            if (outcome == CommitOutcome.Ok) {
                updateSheet<SheetInput.NewPlayer> { it.copy(debtPay = null) }
                eventChannel.send(TableEvent.ConfirmHaptic)
                eventChannel.send(
                    TableEvent.Undoable(
                        uiText(R.string.debt_paid_snackbar, name, MoneyFormat.rub(amount)),
                        UndoToken.Reverse(operations(debtPayChanges)),
                    ),
                )
            } else {
                outcome.messageOrNull()?.let { eventChannel.send(TableEvent.Message(it)) }
            }
        }
    }

    private var debtPayChanges: List<GameChange> = emptyList()

    private fun confirmPay() {
        val input = inputs.value.sheet as? SheetInput.Pay ?: return
        val ui = currentUi().sheet as? PayUi ?: return
        if (!ui.canConfirm) return
        val amount = input.entry.value
        commitThen({
            val change = if (input.payout) {
                Commands.payOut(it, input.playerId, amount, stamp())
            } else {
                Commands.pay(it, input.playerId, amount, stamp())
            }
            listOf(change)
        }) { changes ->
            closeSheet()
            eventChannel.send(TableEvent.ConfirmHaptic)
            val text = if (input.payout) R.string.payout_done_snackbar else R.string.pay_done_snackbar
            eventChannel.send(
                TableEvent.Undoable(
                    uiText(text, ui.name, MoneyFormat.rub(amount)),
                    UndoToken.Reverse(operations(changes)),
                ),
            )
        }
    }

    private fun cashOut(choice: SettlementChoice, earlyConfirmedNow: Boolean = false) {
        val input = inputs.value.sheet as? SheetInput.CashOut ?: return
        val snap = snapshot.value ?: return
        val quote = input.quote(snap, clock.now())
        if (!quote.canConfirm || inputs.value.busy) return
        if (quote.isEarlyExit && !input.earlyConfirmed && !earlyConfirmedNow) {
            inputs.update { it.copy(dialog = DialogInput.EarlyExit(input.playerId, choice)) }
            return
        }
        val name = snap.player(input.playerId)?.name.orEmpty()
        val tip = input.tip()
        commitThen({
            listOf(
                Commands.cashOut(
                    snapshot = it,
                    playerId = input.playerId,
                    chips = input.chips.value,
                    tip = tip,
                    settlement = choice,
                    earlyExitConfirmed = true,
                    stamp = stamp(),
                ),
            )
        }) { changes ->
            closeSheet()
            val message = cashOutMessage(name, quote.balance, choice)
            // Отмена: сначала расчёт, затем сам выход (m3-ui-spec §5.6).
            eventChannel.send(TableEvent.Undoable(message, UndoToken.Reverse(operations(changes).reversed())))
        }
    }

    private fun cashOutMessage(name: String, balance: Long, choice: SettlementChoice): UiText {
        val settled = when (choice) {
            SettlementChoice.Full -> abs(balance)
            SettlementChoice.None -> 0L
            is SettlementChoice.Partial -> choice.amount
        }
        return when {
            balance == 0L -> uiText(R.string.cash_out_done_even, name)
            settled == 0L && balance < 0 -> uiText(R.string.cash_out_done_debt, name, MoneyFormat.rub(-balance))
            settled == 0L -> uiText(R.string.cash_out_done_banker_owes, name, MoneyFormat.rub(balance))
            balance > 0 -> uiText(R.string.cash_out_done_paid, name, MoneyFormat.rub(settled))
            else -> uiText(R.string.cash_out_done_received, name, MoneyFormat.rub(settled))
        }
    }

    private fun onJournalHold(playerId: String, operationId: String) {
        val snap = snapshot.value ?: return
        val ui = currentUi()
        val card = (ui.sheet as? PlayerCardUi)?.takeIf { it.playerId == playerId } ?: ui.detail ?: return
        val row = card.journal.firstOrNull { it.operationId == operationId } ?: return
        val blocker = try {
            Commands.reverse(snap, operationId.toOperationId(), null, stamp())
            null
        } catch (e: DomainRuleViolation) {
            e.error
        }
        if (blocker != null) {
            send(TableEvent.Message(blocker.toUiText()))
            return
        }
        inputs.update {
            it.copy(dialog = DialogInput.Reverse(playerId.toPlayerId(), operationId.toOperationId(), row.title))
        }
    }

    @Suppress("LongMethod")
    private fun confirmDialog() {
        when (val dialog = inputs.value.dialog) {
            is DialogInput.Reverse -> commitThen({
                listOf(Commands.reverse(it, dialog.operationId, dialog.reason, stamp()))
            }) {
                inputs.update { it.copy(dialog = null) }
                eventChannel.send(TableEvent.ConfirmHaptic)
                eventChannel.send(TableEvent.Message(uiText(R.string.journal_reversed_snackbar)))
            }

            is DialogInput.Rename -> {
                val old = snapshot.value?.player(dialog.playerId)?.name ?: return
                val new = PlayerNames.normalize(dialog.value)
                if (new == old) {
                    inputs.update { it.copy(dialog = null) }
                    return
                }
                commitThen(
                    build = { listOf(Commands.renamePlayer(it, dialog.playerId, dialog.value)) },
                    onRejected = { outcome ->
                        val error = (outcome as? CommitOutcome.Rejected)?.error ?: return@commitThen false
                        inputs.update { s ->
                            val d = s.dialog as? DialogInput.Rename ?: return@update s
                            s.copy(dialog = d.copy(error = error.toUiText()))
                        }
                        true
                    },
                ) {
                    inputs.update { it.copy(dialog = null) }
                    eventChannel.send(
                        TableEvent.Undoable(
                            uiText(R.string.rename_done_snackbar, old, new),
                            UndoToken.Rename(dialog.playerId.key(), old),
                        ),
                    )
                }
            }

            is DialogInput.EarlyExit -> {
                inputs.update { it.copy(dialog = null) }
                cashOut(dialog.choice, earlyConfirmedNow = true)
            }

            is DialogInput.Forgive -> commitThen({
                listOf(Commands.forgiveDebt(it, dialog.playerId, amount = null, stamp = stamp()))
            }) { changes ->
                inputs.update { it.copy(dialog = null) }
                eventChannel.send(
                    TableEvent.Undoable(uiText(R.string.forgive_done_snackbar), UndoToken.Reverse(operations(changes))),
                )
            }

            null -> Unit
        }
    }

    private fun undo(token: UndoToken) {
        when (token) {
            is UndoToken.Rename -> commitThen({
                listOf(Commands.renamePlayer(it, token.playerId.toPlayerId(), token.name))
            }) {}

            is UndoToken.Reverse -> viewModelScope.launch {
                val ids = token.operations.map { it.first.toGameId() }.distinct()
                val snaps = ids.mapNotNull { id -> gameRepository.getGame(id)?.let { id to it } }.toMap().toMutableMap()
                val outcome = gameRepository.tryCommit {
                    val stamp = stamp()
                    token.operations.mapNotNull { (game, op) ->
                        val id = game.toGameId()
                        val snap = snaps[id] ?: return@mapNotNull null
                        Commands.reverse(snap, op.toOperationId(), null, stamp).also { snaps[id] = snap.apply(it) }
                    }
                }
                outcome.messageOrNull()?.let { eventChannel.send(TableEvent.Message(it)) }
            }
        }
    }

    companion object {
        /** Имена аргументов маршрута `TableRoute` (SavedStateHandle типобезопасной навигации). */
        const val ARG_GAME_ID = "gameId"
        const val ARG_CASH_OUT = "cashOutPlayerId"
        private const val KEY_CASH_OUT_HANDLED = "cashOutHandled"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val MAX_NAME_INPUT = 60
        private const val MAX_REASON = 200
    }
}

/** Одним статусом, если раздельная оплата выключена (иначе движок отклонит покупку). */
private fun BuyInPayment.normalized(split: Boolean): BuyInPayment = when {
    split || isSingleStatus -> this
    else -> BuyInPayment(chipsPaid = chipsPaid, feesPaid = chipsPaid)
}
