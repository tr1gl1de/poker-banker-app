package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.RakeMode

/**
 * Статус оплаты покупки (ТЗ §3.2). Одним статусом — [DEBT] или [PAID]; раздельно — любые сочетания,
 * только если `rules.splitPayment`. «Ни то ни другое» не бывает: неоплаченное = долг (ТЗ §3.7).
 */
data class BuyInPayment(val chipsPaid: Boolean, val feesPaid: Boolean) {
    val isSingleStatus: Boolean get() = chipsPaid == feesPaid

    companion object {
        val DEBT = BuyInPayment(chipsPaid = false, feesPaid = false)
        val PAID = BuyInPayment(chipsPaid = true, feesPaid = true)
    }
}

/**
 * Итог покупки до подтверждения: «Фишки 2 000 + рейк 200 + сбор 100 = 2 300 ₽».
 * @property playerId null — новый игрок; id void-игрока с тем же именем — он будет «возвращён» (см.
 *   [Pricing.quoteNewPlayer]).
 * @property isFirstBuyIn у игрока нет действующих BUY_IN в этой игре (строка «Закуп», а не «Докуп»).
 * @property isReturn игрок вышел и возвращается — это докуп (ТЗ §4).
 * @property feeWaivedForBanker сбор не взят, потому что игрок — банкир (строка «без сбора»).
 * @property feeAlreadyTaken разовый режим, сбор уже взят («Сбор банкира — уже взят»).
 */
data class BuyInQuote(
    val playerId: PlayerId?,
    val chips: Long,
    val rake: Long,
    val bankerFee: Long,
    val isFirstBuyIn: Boolean,
    val isReturn: Boolean,
    val feeWaivedForBanker: Boolean,
    val feeAlreadyTaken: Boolean,
    val blockers: List<DomainError>,
    val warnings: List<DomainWarning>,
) {
    /** К оплате = D + Рейк(D) + Сбор(D). */
    val total: Long get() = chips + rake + bankerFee
    val fees: Long get() = rake + bankerFee

    /** Сумма `PAYMENT`, которую запишет покупка при данном статусе. */
    fun paidNow(payment: BuyInPayment): Long =
        (if (payment.chipsPaid) chips else 0L) + (if (payment.feesPaid) fees else 0L)

    /** Сумма, уходящая в долг: «Итого в долг 2 200 ₽». */
    fun toDebt(payment: BuyInPayment): Long = total - paidNow(payment)
    val canConfirm: Boolean get() = blockers.isEmpty()
}

object Pricing {
    /** Рейк(D): `percentOf(D, rakeValue)` или `rakeValue`. */
    fun rake(chips: Long, rules: GameRules): Long = when (rules.rakeMode) {
        RakeMode.PERCENT -> Money.percentOf(chips, rules.rakeValue)
        RakeMode.FIXED -> rules.rakeValue
    }

    /**
     * Сбор(D), ТЗ §3.2: 0 для банкира; в `ONCE_*` — только при `isFirstBuyIn`; в `EACH_*` — всегда;
     * `*_PERCENT` — `percentOf(D, bankerFeeValue)`, `*_FIXED` — `bankerFeeValue`.
     */
    fun bankerFee(chips: Long, rules: GameRules, isFirstBuyIn: Boolean, isBanker: Boolean): Long = when {
        isBanker -> 0L
        rules.bankerFeeMode.isOnce && !isFirstBuyIn -> 0L
        rules.bankerFeeMode.isPercent -> Money.percentOf(chips, rules.bankerFeeValue)
        else -> rules.bankerFeeValue
    }

    /** Средняя покупка игры (по действующим BUY_IN, округление половины вверх); null — покупок нет. */
    fun typicalPurchase(snapshot: GameSnapshot): Long? {
        val buyIns = Ledger.effective(snapshot.entries).filter { it.type == EntryType.BUY_IN }
        return if (buyIns.isEmpty()) null else Money.divRoundHalfUp(buyIns.sumOf { it.amount }, buyIns.size.toLong())
    }

    /**
     * Котировка покупки существующего игрока (докуп или возврат). Блокеры: статус игры ≠ RUNNING, игрок не найден/void,
     * сумма ≤ 0, > MAX_AMOUNT, < minBuyIn. Предупреждение SuspiciousBuyIn.
     */
    fun quoteBuyIn(snapshot: GameSnapshot, playerId: PlayerId, chips: Long): BuyInQuote {
        val state = GameStates.compute(snapshot).player(playerId)?.takeUnless { it.isVoid }
        val blockers = buildList {
            addAll(statusBlockers(snapshot))
            if (state == null) add(DomainError.PlayerNotFound(playerId))
            addAll(amountBlockers(chips, snapshot.game.rules))
        }
        return quote(
            snapshot = snapshot,
            playerId = playerId,
            chips = chips,
            isFirstBuyIn = (state?.buyInCount ?: 0) == 0,
            isReturn = state != null && state.status != PlayerStatus.PLAYING,
            isBanker = state?.isBanker == true,
            blockers = blockers,
        )
    }

    /**
     * Котировка первого закупа нового игрока. Дополнительно блокеры имени: пустое, длиннее `PlayerNames.MAX_LENGTH`,
     * уже есть за этим столом (`NameTaken`, сравнение после `PlayerNames.normalize`). Совпадение с void-игроком
     * не блокер: котировка относится к нему (`playerId` = его id), `Commands.addPlayer` его «возвращает».
     * Сбора нет, если игрок — банкир: void-банкир возвращается банкиром, а имя, совпадающее с банкиром игры, который
     * «не играет» (`bankerPlayerId == null`), садит банкира за стол — `Commands.addPlayer` ставит `bankerPlayerId`.
     */
    fun quoteNewPlayer(snapshot: GameSnapshot, name: String, chips: Long): BuyInQuote {
        val state = GameStates.compute(snapshot)
        val normalized = PlayerNames.normalize(name)
        val same = state.players.firstOrNull { PlayerNames.normalize(it.player.name) == normalized }
        val blockers = buildList {
            addAll(statusBlockers(snapshot))
            addAll(nameBlockers(normalized))
            if (same != null && !same.isVoid) add(DomainError.NameTaken(same.player.id))
            addAll(amountBlockers(chips, snapshot.game.rules))
        }
        return quote(
            snapshot = snapshot,
            playerId = same?.takeIf { it.isVoid }?.player?.id,
            chips = chips,
            isFirstBuyIn = true,
            isReturn = false,
            isBanker = same?.isBanker == true || bindsBanker(snapshot, normalized),
            blockers = blockers,
        )
    }

    @Suppress("LongParameterList")
    private fun quote(
        snapshot: GameSnapshot,
        playerId: PlayerId?,
        chips: Long,
        isFirstBuyIn: Boolean,
        isReturn: Boolean,
        isBanker: Boolean,
        blockers: List<DomainError>,
    ): BuyInQuote {
        val rules = snapshot.game.rules
        val valid = chips in 0..Money.MAX_AMOUNT
        val rake = if (valid) rake(chips, rules) else 0L
        val fee = if (valid) bankerFee(chips, rules, isFirstBuyIn, isBanker) else 0L
        val feeIfNotBanker = if (valid) bankerFee(chips, rules, isFirstBuyIn, isBanker = false) else 0L
        return BuyInQuote(
            playerId = playerId,
            chips = chips,
            rake = rake,
            bankerFee = fee,
            isFirstBuyIn = isFirstBuyIn,
            isReturn = isReturn,
            feeWaivedForBanker = isBanker && feeIfNotBanker > 0,
            feeAlreadyTaken = !isBanker && rules.bankerFeeMode.isOnce && !isFirstBuyIn,
            blockers = blockers,
            warnings = if (chips > 0) suspicion(chips, typicalPurchase(snapshot)) else emptyList(),
        )
    }

    private fun suspicion(chips: Long, typical: Long?): List<DomainWarning> {
        val suspicious =
            chips >= Suspicion.ABSOLUTE_LIMIT || (typical != null && chips > typical * Suspicion.AVERAGE_MULTIPLIER)
        return if (suspicious) listOf(DomainWarning.SuspiciousBuyIn(chips, typical)) else emptyList()
    }

    private fun statusBlockers(snapshot: GameSnapshot): List<DomainError> {
        val status = snapshot.game.status
        return if (status ==
            GameStatus.RUNNING
        ) {
            emptyList()
        } else {
            listOf(DomainError.WrongGameStatus(status, setOf(GameStatus.RUNNING)))
        }
    }

    private fun amountBlockers(chips: Long, rules: GameRules): List<DomainError> = when {
        chips <= 0 -> listOf(DomainError.AmountNotPositive)
        chips > Money.MAX_AMOUNT -> listOf(DomainError.AmountTooLarge)
        chips < rules.minBuyIn -> listOf(DomainError.BelowMinBuyIn(rules.minBuyIn))
        else -> emptyList()
    }
}

/** Новый игрок с именем банкира, который «не играет», становится банкиром-игроком (`Game.bankerPlayerId`). */
internal fun bindsBanker(snapshot: GameSnapshot, normalized: String): Boolean =
    snapshot.game.bankerPlayerId == null && normalized == PlayerNames.normalize(snapshot.game.currentBankerName)

/** Блокеры уже нормализованного имени: пустое или длиннее [PlayerNames.MAX_LENGTH]. */
internal fun nameBlockers(normalized: String): List<DomainError> = when {
    normalized.isEmpty() -> listOf(DomainError.NameBlank)
    normalized.length > PlayerNames.MAX_LENGTH -> listOf(DomainError.NameTooLong)
    else -> emptyList()
}
