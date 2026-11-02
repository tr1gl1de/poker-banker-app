package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.Game
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.Player
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerStatus

/**
 * Ввод экрана «Новая игра». `title` пустой → движок подставляет переданный `defaultTitle` (дата и время форматирует
 * UI).
 */
data class NewGameInput(
    val title: String,
    val defaultTitle: String,
    val bankerName: String,
    val bankerPlays: Boolean,
    val rules: GameRules,
)

/**
 * Все команды чистые: читают снимок, возвращают изменение; при нарушении правила бросают [DomainRuleViolation]
 * с тем же [DomainError], что показала котировка. `bankerName` новых записей = `game.currentBankerName`,
 * если не сказано иное; `seq` продолжает журнал; одна команда — одна операция (`OperationId`), кроме [cashOut].
 */
@Suppress("TooManyFunctions") // Публичный набор команд задан контрактом (m1-domain.md §4.7).
object Commands {
    /**
     * Старт игры: `Game(status = RUNNING)`, правила — слепок `input.rules`; при `bankerPlays` — игрок-банкир без закупа
     * (`bankerPlayerId`), закупается обычной покупкой. Блокеры: `activeGame != null`, правила с проблемами, пустое
     *   имя банкира.
     */
    fun startGame(input: NewGameInput, activeGame: Game?, stamp: Stamp): GameChange {
        val bankerName = PlayerNames.normalize(input.bankerName)
        val title = input.title.trim().ifEmpty { input.defaultTitle.trim() }
        val problems = input.rules.problems()
        buildList {
            activeGame?.let { add(DomainError.AnotherGameActive(it.id)) }
            if (problems.isNotEmpty()) add(DomainError.InvalidRules(problems))
            addAll(nameBlockers(bankerName))
            if (title.length > Game.MAX_TITLE_LENGTH) add(DomainError.TitleTooLong)
        }.throwFirst()
        val gameId = GameId(stamp.nextUuid())
        val banker = if (input.bankerPlays) Player(PlayerId(stamp.nextUuid()), gameId, bankerName, stamp.at) else null
        val game = Game(
            id = gameId,
            title = title,
            createdAt = stamp.at,
            closedAt = null,
            status = GameStatus.RUNNING,
            currentBankerName = bankerName,
            bankerPlayerId = banker?.id,
            rules = input.rules,
        )
        return GameChange(gameId = gameId, game = game, players = listOfNotNull(banker))
    }

    /**
     * Новый игрок + первый закуп одной операцией: Player + BUY_IN, RAKE?, BANKER_FEE?, PAYMENT?.
     * Если имя совпадает с void-игроком этого стола — возвращает его (без нового Player), а не создаёт дубль.
     */
    fun addPlayer(snapshot: GameSnapshot, name: String, chips: Long, payment: BuyInPayment, stamp: Stamp): GameChange {
        val quote = Pricing.quoteNewPlayer(snapshot, name, chips)
        checkPurchase(snapshot, quote, payment)
        val player = if (quote.playerId == null) {
            Player(PlayerId(stamp.nextUuid()), snapshot.game.id, PlayerNames.normalize(name), stamp.at)
        } else {
            null
        }
        val playerId = player?.id ?: quote.playerId!!
        return GameChange(
            gameId = snapshot.game.id,
            players = listOfNotNull(player),
            entries = purchase(snapshot, playerId, quote, payment, stamp),
        )
    }

    /**
     * Докуп или возврат вышедшего: BUY_IN, RAKE?, BANKER_FEE?, PAYMENT?. `payment` раздельный — только при
     * `splitPayment`.
     */
    fun buyIn(
        snapshot: GameSnapshot,
        playerId: PlayerId,
        chips: Long,
        payment: BuyInPayment,
        stamp: Stamp,
    ): GameChange {
        val quote = Pricing.quoteBuyIn(snapshot, playerId, chips)
        checkPurchase(snapshot, quote, payment)
        return GameChange(gameId = snapshot.game.id, entries = purchase(snapshot, playerId, quote, payment, stamp))
    }

    /**
     * Оплата долга полностью или частично: PAYMENT(amount), `0 < amount ≤ debt`.
     * Статус игры RUNNING, SETTLING или CLOSED (погашение старого долга, ТЗ §3.7 — запись в старую игру, без
     *   editedAfterClose).
     * @param bankerName кто принял деньги; по умолчанию — банкир игры. Для погашения из новой игры —
     *   текущий банкир телефона (`AppSettings.lastBankerName` или банкир активной игры).
     */
    fun pay(
        snapshot: GameSnapshot,
        playerId: PlayerId,
        amount: Long,
        stamp: Stamp,
        bankerName: String = snapshot.game.currentBankerName,
    ): GameChange {
        val state = settleablePlayer(snapshot, playerId, amount)
        if (amount > state.debt) violation(DomainError.PaymentExceedsDebt(state.debt))
        return single(snapshot, stamp, EntryType.PAYMENT, playerId, amount, bankerName)
    }

    /** «Выплатить»: PAYOUT(amount), `0 < amount ≤ owedByBanker`. Статусы как у [pay]. */
    fun payOut(
        snapshot: GameSnapshot,
        playerId: PlayerId,
        amount: Long,
        stamp: Stamp,
        bankerName: String = snapshot.game.currentBankerName,
    ): GameChange {
        val state = settleablePlayer(snapshot, playerId, amount)
        if (amount > state.owedByBanker) violation(DomainError.PayoutExceedsOwed(state.owedByBanker))
        return single(snapshot, stamp, EntryType.PAYOUT, playerId, amount, bankerName)
    }

    /**
     * Выход: две операции в одном изменении (каждую можно отменить отдельно):
     * 1) CASH_OUT(F, earlyExit) + TIP | TIP_CHIPS — «Выход»;
     * 2) PAYMENT | PAYOUT по [settlement] — «Расчёт» (нет записи при None или balance = 0).
     * Ранний выход без `earlyExitConfirmed` — EarlyExitNotConfirmed.
     */
    @Suppress("LongParameterList")
    fun cashOut(
        snapshot: GameSnapshot,
        playerId: PlayerId,
        chips: Long,
        tip: Tip?,
        settlement: SettlementChoice,
        earlyExitConfirmed: Boolean,
        stamp: Stamp,
    ): GameChange {
        val quote = CashOuts.quote(snapshot, playerId, chips, tip, stamp.at)
        quote.blockers.throwFirst()
        if (quote.isEarlyExit && !earlyExitConfirmed) {
            violation(DomainError.EarlyExitNotConfirmed(quote.minutesPlayed ?: 0L, snapshot.game.rules.minPlayMinutes))
        }
        val settled = settledAmount(quote.balance, settlement)
        val writer = EntryWriter(snapshot, stamp)
        val exit = writer.operation()
        writer.add(exit, EntryType.CASH_OUT, playerId, chips, earlyExit = quote.isEarlyExit)
        if (tip != null) {
            val type = if (quote.tipAsChips) EntryType.TIP_CHIPS else EntryType.TIP
            writer.add(exit, type, playerId, tip.amount, counterparty = (tip.to as? TipTarget.ToPlayer)?.playerId)
        }
        if (settled > 0) {
            val type = if (quote.balance > 0) EntryType.PAYOUT else EntryType.PAYMENT
            writer.add(writer.operation(), type, playerId, settled)
        }
        return GameChange(gameId = snapshot.game.id, entries = writer.entries)
    }

    /**
     * Отмена операции: REVERSAL на каждую её действующую запись (одна новая операция).
     * Запрещено: операция из REVERSAL, уже отменённая, INCOME_TAKEN, игра HANDED_OVER; в CLOSED — всё, кроме операций
     * PAYMENT/PAYOUT/DEBT_FORGIVEN (ошибка в погашении долга), для остального игру переоткрывают ([reopen]).
     */
    fun reverse(snapshot: GameSnapshot, operationId: OperationId, reason: String?, stamp: Stamp): GameChange {
        reversalBlocker(snapshot, operationId)?.let { violation(it) }
        val reversed = Ledger.reversedIds(snapshot.entries)
        val note = reason?.trim()?.ifEmpty { null }
        val writer = EntryWriter(snapshot, stamp)
        val op = writer.operation()
        snapshot.entries
            .filter { it.operationId == operationId && it.id !in reversed }
            .forEach { target ->
                writer.add(
                    operation = op,
                    type = EntryType.REVERSAL,
                    playerId = target.playerId,
                    amount = target.amount,
                    counterparty = target.counterpartyPlayerId,
                    reverses = target.id,
                    note = note,
                )
            }
        return GameChange(gameId = snapshot.game.id, entries = writer.entries)
    }

    /**
     * «Простить долг»: DEBT_FORGIVEN(amount ?: debt), игрок LEFT_WITH_DEBT, `0 < amount ≤ debt`. Статусы как у [pay].
     */
    fun forgiveDebt(snapshot: GameSnapshot, playerId: PlayerId, amount: Long?, stamp: Stamp): GameChange {
        requireStatus(snapshot, SETTLE_STATUSES)
        val state = GameStates.compute(snapshot).player(playerId)?.takeUnless { it.isVoid }
            ?: violation(DomainError.PlayerNotFound(playerId))
        if (state.status != PlayerStatus.LEFT_WITH_DEBT) violation(DomainError.NoDebt(playerId))
        val forgiven = amount ?: state.debt
        amountBlocker(forgiven)?.let { violation(it) }
        if (forgiven > state.debt) violation(DomainError.ForgiveExceedsDebt(state.debt))
        return single(snapshot, stamp, EntryType.DEBT_FORGIVEN, playerId, forgiven)
    }

    /** «Рейк отдан заведению»: RAKE_PAID(rakeDue), `rakeDue > 0`, статус RUNNING или SETTLING. */
    fun payRake(snapshot: GameSnapshot, stamp: Stamp): GameChange {
        requireStatus(snapshot, CashOuts.CASH_OUT_STATUSES)
        val due = GameStates.compute(snapshot).table.rakeDue
        if (due <= 0) violation(DomainError.NoRakeDue)
        return single(snapshot, stamp, EntryType.RAKE_PAID, null, due)
    }

    /** «Завершить игру»: RUNNING → SETTLING. */
    fun finish(snapshot: GameSnapshot): GameChange = transition(snapshot, GameStatus.RUNNING, GameStatus.SETTLING)

    /** Передумали завершать: SETTLING → RUNNING. */
    fun resume(snapshot: GameSnapshot): GameChange = transition(snapshot, GameStatus.SETTLING, GameStatus.RUNNING)

    /**
     * «Закрыть игру» (hold): SETTLING → CLOSED, `closedAt = stamp.at`; при `payRake` и `rakeDue > 0` — та же транзакция
     * пишет RAKE_PAID (кнопка «Рейк отдан · закрыть игру»). Блокеры: PlayersStillPlaying; NotConfirmed, если
     * `Settlements.compute(...).requiredConfirmations ⊄ acknowledged`.
     */
    fun close(
        snapshot: GameSnapshot,
        payRake: Boolean,
        acknowledged: Set<CloseConfirmation>,
        stamp: Stamp,
    ): GameChange {
        requireStatus(snapshot, setOf(GameStatus.SETTLING))
        val settlement = Settlements.compute(snapshot)
        settlement.blockers.throwFirst()
        val missing = settlement.requiredConfirmations - acknowledged
        if (missing.isNotEmpty()) violation(DomainError.NotConfirmed(missing))
        val writer = EntryWriter(snapshot, stamp)
        if (payRake &&
            settlement.rakeDue > 0
        ) {
            writer.add(writer.operation(), EntryType.RAKE_PAID, null, settlement.rakeDue)
        }
        return GameChange(
            gameId = snapshot.game.id,
            game = snapshot.game.copy(status = GameStatus.CLOSED, closedAt = stamp.at),
            entries = writer.entries,
        )
    }

    /**
     * «Переоткрыть для исправления»: CLOSED → RUNNING, `closedAt = null`, `editedAfterClose = true`. Блокер: другая
     * активная игра.
     */
    fun reopen(snapshot: GameSnapshot, activeGame: Game?): GameChange {
        requireStatus(snapshot, setOf(GameStatus.CLOSED))
        if (activeGame != null &&
            activeGame.id != snapshot.game.id
        ) {
            violation(DomainError.AnotherGameActive(activeGame.id))
        }
        val game = snapshot.game.copy(status = GameStatus.RUNNING, closedAt = null, editedAfterClose = true)
        return GameChange(gameId = snapshot.game.id, game = game)
    }

    /** «Переименовать»: upsert Player с новым именем; блокеры имени как у новой игры. Журнал не меняется. */
    fun renamePlayer(snapshot: GameSnapshot, playerId: PlayerId, newName: String): GameChange {
        requireStatus(snapshot, EDITABLE_STATUSES)
        val player = snapshot.player(playerId) ?: violation(DomainError.PlayerNotFound(playerId))
        val normalized = PlayerNames.normalize(newName)
        nameBlockers(normalized).throwFirst()
        snapshot.players
            .firstOrNull { it.id != playerId && PlayerNames.normalize(it.name) == normalized }
            ?.let { violation(DomainError.NameTaken(it.id)) }
        return GameChange(gameId = snapshot.game.id, players = listOf(player.copy(name = normalized)))
    }

    private fun checkPurchase(snapshot: GameSnapshot, quote: BuyInQuote, payment: BuyInPayment) {
        quote.blockers.throwFirst()
        if (!payment.isSingleStatus && !snapshot.game.rules.splitPayment) violation(DomainError.SplitPaymentDisabled)
    }

    private fun purchase(
        snapshot: GameSnapshot,
        playerId: PlayerId,
        quote: BuyInQuote,
        payment: BuyInPayment,
        stamp: Stamp,
    ): List<LedgerEntry> {
        val writer = EntryWriter(snapshot, stamp)
        val op = writer.operation()
        writer.add(op, EntryType.BUY_IN, playerId, quote.chips)
        if (quote.rake > 0) writer.add(op, EntryType.RAKE, playerId, quote.rake)
        if (quote.bankerFee > 0) writer.add(op, EntryType.BANKER_FEE, playerId, quote.bankerFee)
        val paid = quote.paidNow(payment)
        if (paid > 0) writer.add(op, EntryType.PAYMENT, playerId, paid)
        return writer.entries
    }

    /** Игрок для оплаты/выплаты/прощения: статус игры, игрок есть и не void, сумма в 1..MAX_AMOUNT. */
    private fun settleablePlayer(snapshot: GameSnapshot, playerId: PlayerId, amount: Long): PlayerState {
        requireStatus(snapshot, SETTLE_STATUSES)
        val state = GameStates.compute(snapshot).player(playerId)?.takeUnless { it.isVoid }
            ?: violation(DomainError.PlayerNotFound(playerId))
        amountBlocker(amount)?.let { violation(it) }
        return state
    }

    @Suppress("LongParameterList")
    private fun single(
        snapshot: GameSnapshot,
        stamp: Stamp,
        type: EntryType,
        playerId: PlayerId?,
        amount: Long,
        bankerName: String = snapshot.game.currentBankerName,
    ): GameChange {
        val writer = EntryWriter(snapshot, stamp)
        writer.add(writer.operation(), type, playerId, amount, bankerName = bankerName)
        return GameChange(gameId = snapshot.game.id, entries = writer.entries)
    }

    private fun transition(snapshot: GameSnapshot, from: GameStatus, to: GameStatus): GameChange {
        requireStatus(snapshot, setOf(from))
        return GameChange(gameId = snapshot.game.id, game = snapshot.game.copy(status = to))
    }

    private val SETTLE_STATUSES = setOf(GameStatus.RUNNING, GameStatus.SETTLING, GameStatus.CLOSED)
    private val EDITABLE_STATUSES =
        setOf(GameStatus.CREATED, GameStatus.RUNNING, GameStatus.SETTLING, GameStatus.CLOSED)
}

/** Сумма денежной записи расчёта при выходе (0 — записи нет). */
private fun settledAmount(balance: Long, settlement: SettlementChoice): Long {
    val full = if (balance < 0) -balance else balance
    return when (settlement) {
        SettlementChoice.Full -> full

        SettlementChoice.None -> 0L

        is SettlementChoice.Partial -> {
            if (full == 0L) violation(DomainError.NothingToSettle)
            if (settlement.amount !in 1 until full) violation(DomainError.PartialOutOfRange(full - 1))
            settlement.amount
        }
    }
}

/** Почему операцию нельзя отменить; null — можно (`Commands.reverse`, `JournalItem.Operation.reversible`). */
internal fun reversalBlocker(snapshot: GameSnapshot, operationId: OperationId): DomainError? {
    val operation = snapshot.entries.filter { it.operationId == operationId }
    if (operation.isEmpty()) return DomainError.OperationNotFound(operationId)
    val reversed = Ledger.reversedIds(snapshot.entries)
    val status = snapshot.game.status
    val reason = when {
        status == GameStatus.HANDED_OVER -> NotReversibleReason.GAME_READ_ONLY

        operation.any { it.type == EntryType.REVERSAL } -> NotReversibleReason.IS_REVERSAL

        operation.any { it.type == EntryType.INCOME_TAKEN } -> NotReversibleReason.INCOME_TAKEN

        operation.all { it.id in reversed } -> NotReversibleReason.ALREADY_REVERSED

        status == GameStatus.CLOSED && operation.any { it.type !in reversibleAfterClose } ->
            NotReversibleReason.NOT_ALLOWED_AFTER_CLOSE

        else -> null
    }
    return reason?.let { DomainError.OperationNotReversible(it) }
}

private val reversibleAfterClose = setOf(EntryType.PAYMENT, EntryType.PAYOUT, EntryType.DEBT_FORGIVEN)

internal fun requireStatus(snapshot: GameSnapshot, allowed: Set<GameStatus>) {
    val actual = snapshot.game.status
    if (actual !in allowed) violation(DomainError.WrongGameStatus(actual, allowed))
}

/** Денежная сумма команды: 1..MAX_AMOUNT. */
internal fun amountBlocker(amount: Long): DomainError? = when {
    amount <= 0 -> DomainError.AmountNotPositive
    amount > Money.MAX_AMOUNT -> DomainError.AmountTooLarge
    else -> null
}
