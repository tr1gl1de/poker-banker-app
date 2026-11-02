package app.feltbook.feature.game.table

import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.CashOutQuote
import app.feltbook.core.domain.engine.CashOuts
import app.feltbook.core.domain.engine.Debts
import app.feltbook.core.domain.engine.DomainError
import app.feltbook.core.domain.engine.GameState
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.engine.Journal
import app.feltbook.core.domain.engine.JournalItem
import app.feltbook.core.domain.engine.OperationKind
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.engine.PlayerState
import app.feltbook.core.domain.engine.Pricing
import app.feltbook.core.domain.engine.Tip
import app.feltbook.core.domain.engine.TipTarget
import app.feltbook.core.domain.engine.isSeated
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.Suit
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.UiText
import app.feltbook.core.ui.format.pluralQuantity
import app.feltbook.core.ui.format.plus
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.game.R
import app.feltbook.feature.game.common.key
import app.feltbook.feature.game.common.toUiText
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Масть по порядку посадки: ♠ ♥ ♣ ♦ (m3-ui-spec §4.1). */
internal fun seatSuits(snapshot: GameSnapshot): Map<PlayerId, Suit> =
    snapshot.players.sortedBy { it.joinedAt }.mapIndexed { index, player -> player.id to Suit.forSeat(index) }.toMap()

private const val ROUND_100 = 100L
private const val ROUND_500 = 500L

/** Чаевые по вводу шторки выхода; 0 → без чаевых. */
internal fun SheetInput.CashOut.tip(): Tip? {
    val chips = this.chips.value
    val amount = when (tip) {
        TipChoice.NONE -> 0L
        TipChoice.ROUND_100 -> CashOuts.roundingTip(chips, ROUND_100)
        TipChoice.ROUND_500 -> CashOuts.roundingTip(chips, ROUND_500)
        TipChoice.CUSTOM -> customTip.value
    }
    if (amount <= 0) return null
    return Tip(amount, tipTo?.let { TipTarget.ToPlayer(it) } ?: TipTarget.Banker)
}

internal fun SheetInput.CashOut.quote(snapshot: GameSnapshot, now: Instant): CashOutQuote =
    CashOuts.quote(snapshot, playerId, chips.value, tip(), now)

/** Чистая сборка состояния стола из снимка и ввода — её же используют превью и тесты. */
@Suppress("LongParameterList", "LongMethod")
fun buildTableUiState(
    snapshot: GameSnapshot?,
    now: Instant,
    inputs: TableInputs,
    games: List<GameSnapshot> = emptyList(),
    directory: List<PlayerName> = emptyList(),
): TableUiState {
    if (snapshot == null) return TableUiState(loading = true, busy = inputs.busy)
    val state = GameStates.compute(snapshot)
    val table = state.table
    val suits = seatSuits(snapshot)
    val rules = snapshot.game.rules
    val visible = state.players.filterNot { it.isVoid }
    val ordered = visible.filter { it.status == PlayerStatus.PLAYING }.sortedBy { it.player.joinedAt } +
        visible.filter { it.status != PlayerStatus.PLAYING }.sortedBy { it.lastCashOutAt }
    val running = snapshot.game.status == GameStatus.RUNNING
    val cashOutAllowed = snapshot.game.status == GameStatus.RUNNING || snapshot.game.status == GameStatus.SETTLING
    val rows = ordered.map { player ->
        PlayerRowUi(
            id = player.player.id.key(),
            name = player.player.name,
            suit = suits[player.player.id] ?: Suit.Spades,
            status = player.status,
            isBanker = player.isBanker,
            rebuyCount = player.rebuyCount,
            chipsBought = player.session?.chipsBought ?: player.chipsBought,
            bought = player.buyInCount > 0,
            playedFor = player.playedFor(now),
            earlyMinutes = if (player.status == PlayerStatus.PLAYING && player.isEarlyExit(rules, now)) {
                player.playedFor(now)?.inWholeMinutes
            } else {
                null
            },
            minPlayMinutes = rules.minPlayMinutes,
            outstanding = player.outstanding,
            balanceAtExit = player.balanceAtExit,
            canBuyIn = running,
            canCashOut = cashOutAllowed && player.isSeated,
        )
    }
    val context = MapperContext(snapshot, state, suits, now, inputs.busy)
    return TableUiState(
        loading = false,
        gameId = snapshot.game.id.key(),
        title = snapshot.game.title,
        status = snapshot.game.status,
        bankerName = snapshot.game.currentBankerName,
        now = now,
        elapsed = now - snapshot.game.createdAt,
        board = BoardUi(
            chipsInPlay = table.chipsInPlay,
            chipsIssued = table.chipsIssued,
            chipsReturned = table.chipsReturned,
            rebuyCount = table.rebuyCount,
            cashOnHand = table.cashOnHand,
            playersOwe = table.playersOwe,
            bankerOwes = table.bankerOwes,
            rakeDue = table.rakeDue,
            bankerIncome = table.bankerIncome,
        ),
        playersAtTable = table.playersAtTable,
        playersLeft = table.playersLeft,
        rows = rows,
        sheet = inputs.sheet?.let { context.sheet(it, games, directory) },
        dialog = inputs.dialog?.let { context.dialog(it) },
        busy = inputs.busy,
        selectedPlayerId = inputs.selected?.key(),
        detail = inputs.selected?.let { context.card(it) },
    )
}

private class MapperContext(
    val snapshot: GameSnapshot,
    val state: GameState,
    val suits: Map<PlayerId, Suit>,
    val now: Instant,
    val busy: Boolean,
) {
    val rules = snapshot.game.rules
    val rakePercent: Long? = if (rules.rakeMode == RakeMode.PERCENT) rules.rakeValue else null

    fun suit(id: PlayerId): Suit = suits[id] ?: Suit.Spades

    fun sheet(input: SheetInput, games: List<GameSnapshot>, directory: List<PlayerName>): SheetUi? = when (input) {
        is SheetInput.Card -> card(input.playerId)
        is SheetInput.BuyIn -> buyIn(input)
        is SheetInput.NewPlayer -> newPlayer(input, games, directory)
        is SheetInput.Pay -> pay(input)
        is SheetInput.CashOut -> cashOut(input)
        is SheetInput.Breakdown -> breakdown(input.kind)
    }

    fun dialog(input: DialogInput): DialogUi? = when (input) {
        is DialogInput.Reverse -> DialogUi.Reverse(input.operationId.key(), input.title, input.reason)

        is DialogInput.Rename -> snapshot.player(input.playerId)?.let {
            DialogUi.Rename(input.playerId.key(), it.name, input.value, input.error)
        }

        is DialogInput.EarlyExit -> state.player(input.playerId)?.let {
            DialogUi.EarlyExit(it.player.name, it.playedFor(now)?.inWholeMinutes ?: 0L, rules.minPlayMinutes)
        }

        is DialogInput.Forgive -> state.player(input.playerId)?.let {
            DialogUi.Forgive(input.playerId.key(), it.debt, snapshot.game.title)
        }
    }

    fun card(id: PlayerId): PlayerCardUi? {
        val player = state.player(id)?.takeUnless { it.isVoid } ?: return null
        val status = snapshot.game.status
        val playing = player.status == PlayerStatus.PLAYING
        val settleStatuses = setOf(GameStatus.RUNNING, GameStatus.SETTLING, GameStatus.CLOSED)
        return PlayerCardUi(
            playerId = id.key(),
            name = player.player.name,
            suit = suit(id),
            status = player.status,
            isBanker = player.isBanker,
            rebuyCount = player.rebuyCount,
            playedFor = player.playedFor(if (playing) now else player.lastCashOutAt ?: now),
            firstBuyInAt = player.firstBuyInAt,
            lastCashOutAt = player.lastCashOutAt,
            earlyExit = player.earlyExit,
            chipsBought = player.chipsBought,
            charged = player.charged,
            paid = player.paid,
            debt = player.debt,
            owedByBanker = player.owedByBanker,
            canBuyIn = status == GameStatus.RUNNING,
            canPay = player.debt > 0 && status in settleStatuses,
            canPayout = player.owedByBanker > 0 && status in settleStatuses,
            canCashOut = player.isSeated && (status == GameStatus.RUNNING || status == GameStatus.SETTLING),
            canEarlyExit = player.isSeated && player.isEarlyExit(rules, now) && status == GameStatus.RUNNING,
            canForgive = player.status == PlayerStatus.LEFT_WITH_DEBT && status in settleStatuses,
            canRename = status != GameStatus.HANDED_OVER,
            journal = journal(id),
        )
    }

    private fun journal(id: PlayerId): List<JournalRowUi> {
        val operations = Journal.forPlayer(snapshot, id)
        val severalBankers = Journal.items(snapshot)
            .filterIsInstance<JournalItem.Operation>()
            .map { it.bankerName }
            .distinct().size > 1
        return operations.map { op -> journalRow(op, id, severalBankers) }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth") // Таблица видов операций m3-ui-spec §5.5.
    private fun journalRow(op: JournalItem.Operation, id: PlayerId, severalBankers: Boolean): JournalRowUi {
        val entries = op.entries
        fun sum(type: EntryType) = entries.filter { it.type == type && it.playerId == id }.sumOf { it.amount }
        val subs = mutableListOf<JournalSubUi>()
        var amount: Long? = null
        val title: UiText = when (op.kind) {
            OperationKind.FIRST_BUY_IN, OperationKind.REBUY -> {
                val chips = sum(EntryType.BUY_IN)
                val rake = sum(EntryType.RAKE)
                val fee = sum(EntryType.BANKER_FEE)
                val paid = sum(EntryType.PAYMENT)
                amount = chips
                if (rake > 0) {
                    subs += JournalSubUi(
                        rakePercent?.let { uiText(R.string.journal_sub_rake_percent, MoneyFormat.percent(it.toInt())) }
                            ?: uiText(R.string.journal_sub_rake),
                        rake,
                    )
                }
                if (fee > 0) subs += JournalSubUi(uiText(R.string.journal_sub_fee), fee)
                if (paid > 0) subs += JournalSubUi(uiText(R.string.journal_sub_payment), paid)
                val head = if (op.kind == OperationKind.FIRST_BUY_IN) {
                    R.string.journal_buy_in_first
                } else {
                    R.string.journal_buy_in_rebuy
                }
                val statusText = when (paid) {
                    0L -> R.string.journal_status_debt
                    chips + rake + fee -> R.string.journal_status_paid
                    else -> R.string.journal_status_partial
                }
                uiText(head, MoneyFormat.number(chips)) + uiText(statusText)
            }

            OperationKind.PAYMENT -> {
                amount = sum(EntryType.PAYMENT)
                uiText(R.string.journal_payment)
            }

            OperationKind.PAYOUT -> {
                amount = sum(EntryType.PAYOUT)
                uiText(R.string.journal_payout)
            }

            OperationKind.SETTLEMENT -> {
                val payment = sum(EntryType.PAYMENT)
                if (payment > 0) {
                    amount = payment
                    uiText(R.string.journal_settlement_received)
                } else {
                    amount = sum(EntryType.PAYOUT)
                    uiText(R.string.journal_settlement_paid_out)
                }
            }

            OperationKind.CASH_OUT -> {
                val own = entries.any { it.type == EntryType.CASH_OUT && it.playerId == id }
                if (own) {
                    val exit = entries.first { it.type == EntryType.CASH_OUT }
                    amount = exit.amount
                    entries.filter { it.type == EntryType.TIP || it.type == EntryType.TIP_CHIPS }.forEach { tip ->
                        val to = tip.counterpartyPlayerId
                        val text = if (to == null) {
                            uiText(R.string.journal_sub_tip_banker)
                        } else {
                            uiText(R.string.journal_sub_tip_player, snapshot.player(to)?.name.orEmpty())
                        }
                        subs += JournalSubUi(text, tip.amount)
                    }
                    val head = uiText(
                        R.string.journal_cash_out,
                        UiText.Plural(
                            UiR.plurals.chips_count,
                            pluralQuantity(exit.amount),
                            listOf(MoneyFormat.number(exit.amount)),
                        ),
                    )
                    if (exit.earlyExit) head + uiText(R.string.journal_cash_out_early) else head
                } else {
                    val tip = entries.firstOrNull { it.counterpartyPlayerId == id }
                    amount = tip?.amount
                    val giver = tip?.playerId?.let { snapshot.player(it)?.name }.orEmpty()
                    uiText(R.string.journal_tip_received, giver)
                }
            }

            OperationKind.DEBT_FORGIVEN -> {
                amount = sum(EntryType.DEBT_FORGIVEN)
                uiText(R.string.journal_forgiven)
            }

            OperationKind.RAKE_PAID, OperationKind.INCOME_TAKEN -> {
                amount = entries.sumOf { it.amount }
                uiText(R.string.journal_payment)
            }
        }
        val fullTitle = if (severalBankers) title + uiText(R.string.journal_banker_suffix, op.bankerName) else title
        return JournalRowUi(
            operationId = op.operationId.key(),
            at = op.at,
            title = fullTitle,
            amount = amount,
            subs = subs,
            reversed = op.reversal != null,
            reversible = op.reversible,
        )
    }

    private fun buyIn(input: SheetInput.BuyIn): BuyInUi? {
        val player = state.player(input.playerId)?.takeUnless { it.isVoid } ?: return null
        val quote = Pricing.quoteBuyIn(snapshot, input.playerId, input.entry.value)
        return BuyInUi(
            playerId = input.playerId.key(),
            name = player.player.name,
            suit = suit(input.playerId),
            isFirst = quote.isFirstBuyIn,
            isReturn = quote.isReturn,
            chipsBought = player.chipsBought,
            debt = player.debt,
            owedByBanker = player.owedByBanker,
            entry = input.entry,
            minBuyIn = rules.minBuyIn,
            rakePercent = rakePercent,
            split = rules.splitPayment,
            payment = input.payment,
            quote = quote,
            lastAmount = lastPurchase(input.playerId),
            busy = busy,
        )
    }

    /** «как прошлый»: BUY_IN последней действующей покупки игрока. */
    private fun lastPurchase(id: PlayerId): Long? {
        val reversed = snapshot.entries.filter {
            it.type == EntryType.REVERSAL
        }.mapNotNull { it.reversesEntryId }.toSet()
        return snapshot.entries.lastOrNull { it.type == EntryType.BUY_IN && it.playerId == id && it.id !in reversed }
            ?.amount
    }

    private fun newPlayer(
        input: SheetInput.NewPlayer,
        games: List<GameSnapshot>,
        directory: List<PlayerName>,
    ): NewPlayerUi {
        val normalized = PlayerNames.normalize(input.name)
        val quote = Pricing.quoteNewPlayer(snapshot, input.name, input.entry.value)
        val atTable = state.players.filterNot { it.isVoid }.map { it.player.name }.toSet()
        val problem: NameProblem? = quote.blockers.firstNotNullOfOrNull { error ->
            when (error) {
                DomainError.NameTooLong -> NameProblem.TooLong

                is DomainError.NameTaken -> state.player(error.existing)?.let {
                    NameProblem.Taken(error.existing.key(), it.player.name, seated = it.status == PlayerStatus.PLAYING)
                }

                else -> null
            }
        }
        val debts = if (normalized.isEmpty() || problem != null) {
            emptyList()
        } else {
            Debts.forName(normalized, games, directory, excludeGameId = snapshot.game.id)
        }
        val warning = debts.takeIf { it.isNotEmpty() }?.let { list ->
            val oldest = list.minBy { it.since }
            DebtWarningUi(
                name = normalized,
                total = list.sumOf { it.amount },
                since = oldest.since,
                oldestGameTitle = oldest.gameTitle,
                days = ((now - oldest.since) / 1.days).toLong(),
                games = list.map { it.gameId }.distinct().size,
            )
        }
        val payment = if (warning != null && !input.paymentTouched) {
            BuyInPayment.PAID
        } else {
            input.payment
        }
        return NewPlayerUi(
            name = input.name,
            suggestions = if (input.amountFocused) {
                emptyList()
            } else {
                PlayerNames.suggestions(input.name, directory, exclude = atTable)
                    .filter { it != normalized }
            },
            nameProblem = problem,
            debt = warning,
            debtCollapsed = input.debtCollapsed,
            amountFocused = input.amountFocused,
            entry = input.entry,
            minBuyIn = rules.minBuyIn,
            rakePercent = rakePercent,
            split = rules.splitPayment,
            payment = payment,
            quote = quote,
            debtPay = input.debtPay,
            busy = busy,
        )
    }

    private fun pay(input: SheetInput.Pay): PayUi? {
        val player = state.player(input.playerId)?.takeUnless { it.isVoid } ?: return null
        val current = if (input.payout) player.owedByBanker else player.debt
        val error = when {
            input.entry.value > current && input.payout -> uiText(
                R.string.payout_exceeds_owed,
                MoneyFormat.rub(current),
            )

            input.entry.value > current -> uiText(R.string.pay_exceeds_debt, MoneyFormat.rub(current))

            else -> null
        }
        return PayUi(
            playerId = input.playerId.key(),
            name = player.player.name,
            suit = suit(input.playerId),
            payout = input.payout,
            current = current,
            entry = input.entry,
            error = error,
            busy = busy,
        )
    }

    private fun cashOut(input: SheetInput.CashOut): CashOutUi? {
        val player = state.player(input.playerId)?.takeUnless { it.isVoid } ?: return null
        val quote = input.quote(snapshot, now)
        val recipient = input.tipTo?.let { id ->
            state.player(id)?.let { TipRecipientUi(id.key(), it.player.name, suit(id), it.isSeated) }
        }
        val blocker = quote.blockers.firstOrNull { it.isTipBlocker() || it is DomainError.PlayerNotSeated }
            ?.toUiText()
        val recipients = if (input.picking) {
            val others = state.players.filter { !it.isVoid && it.player.id != input.playerId && it.buyInCount > 0 }
            (others.filter { it.isSeated }.sortedBy { it.player.joinedAt } + others.filterNot { it.isSeated })
                .map { TipRecipientUi(it.player.id.key(), it.player.name, suit(it.player.id), it.isSeated) }
        } else {
            null
        }
        return CashOutUi(
            playerId = input.playerId.key(),
            name = player.player.name,
            suit = suit(input.playerId),
            playedFor = player.playedFor(now),
            chipsBought = player.session?.chipsBought ?: player.chipsBought,
            debtBefore = player.debt,
            chips = input.chips,
            focus = input.focus,
            tipChoice = input.tip,
            customTip = input.customTip,
            round100 = CashOuts.roundingTip(input.chips.value, ROUND_100),
            round500 = CashOuts.roundingTip(input.chips.value, ROUND_500),
            tipRecipient = recipient,
            tipsExpanded = input.tipsExpanded || input.tip != TipChoice.NONE || quote.balance >= 0,
            quote = quote,
            minPlayMinutes = rules.minPlayMinutes,
            earlyConfirmed = input.earlyConfirmed,
            partial = input.partial,
            recipients = recipients,
            blocker = blocker,
            busy = busy,
        )
    }

    @Suppress("LongMethod")
    fun breakdown(kind: BreakdownKind): BreakdownUi {
        val players = state.players.filterNot { it.isVoid }
        val table = state.table
        fun line(p: PlayerState, amount: Long, tone: Tone = Tone.INK, currency: Boolean = true) =
            BreakdownLine(UiText.Raw(p.player.name), amount, tone, currency)
        return when (kind) {
            BreakdownKind.CHIPS -> BreakdownUi(
                kind = kind,
                total = table.chipsInPlay,
                lines = players.filter {
                    it.isSeated
                }.map { line(it, it.chipsBought - it.chipsCashedOut, currency = false) },
            )

            BreakdownKind.CASH -> BreakdownUi(
                kind = kind,
                total = table.cashOnHand,
                lines = buildList {
                    players.filter { it.paid != 0L || it.paidOut != 0L }.forEach { add(line(it, it.paid - it.paidOut)) }
                    if (table.rakePaid > 0) add(BreakdownLine(uiText(R.string.breakdown_rake_paid), -table.rakePaid))
                    if (table.incomeTaken > 0) {
                        add(BreakdownLine(uiText(R.string.breakdown_income_taken), -table.incomeTaken))
                    }
                },
            )

            BreakdownKind.DEBTS -> BreakdownUi(
                kind = kind,
                total = table.playersOwe,
                lines = players.filter { it.debt > 0 }.map { line(it, it.debt, Tone.OWE) },
                secondaryTitle = uiText(R.string.breakdown_banker_owes_section).takeIf { table.bankerOwes > 0 },
                secondary = players.filter { it.owedByBanker > 0 }.map { line(it, it.owedByBanker, Tone.PAY) },
            )

            BreakdownKind.RAKE -> BreakdownUi(
                kind = kind,
                total = table.rakeDue,
                lines = buildList {
                    players.filter { it.rake > 0 }.forEach { add(line(it, it.rake)) }
                    if (table.rakePaid > 0) add(BreakdownLine(uiText(R.string.breakdown_rake_paid), -table.rakePaid))
                },
            )

            BreakdownKind.BANKER -> BreakdownUi(
                kind = kind,
                total = table.bankerIncome,
                lines = players.filter { it.bankerFee + it.tipsToBanker > 0 }
                    .map { line(it, it.bankerFee + it.tipsToBanker, Tone.BRASS) },
                secondaryTitle = uiText(R.string.breakdown_by_bankers).takeIf { table.incomeByBanker.size > 1 },
                secondary = if (table.incomeByBanker.size > 1) {
                    table.incomeByBanker.map { BreakdownLine(UiText.Raw(it.bankerName), it.net, Tone.BRASS) }
                } else {
                    emptyList()
                },
            )
        }
    }
}

private fun DomainError.isTipBlocker(): Boolean = this is DomainError.TipExceedsChips ||
    this == DomainError.TipToSelf ||
    this == DomainError.TipNotPositive ||
    this is DomainError.TipRecipientInvalid ||
    this is DomainError.TipRecipientNotSeated
