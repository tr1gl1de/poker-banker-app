package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.Game
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.Player
import app.feltbook.core.domain.model.PlayerId

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

    /** «Завершить игру»: RUNNING → SETTLING. */
    fun finish(snapshot: GameSnapshot): GameChange = transition(snapshot, GameStatus.RUNNING, GameStatus.SETTLING)

    /** Передумали завершать: SETTLING → RUNNING. */
    fun resume(snapshot: GameSnapshot): GameChange = transition(snapshot, GameStatus.SETTLING, GameStatus.RUNNING)

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
