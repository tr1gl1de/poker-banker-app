package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerStatus
import kotlin.time.Instant

sealed interface TipTarget {
    data object Banker : TipTarget

    data class ToPlayer(val playerId: PlayerId) : TipTarget
}

/** Чаевые (необязательны, по умолчанию нет — `null` в API). */
data class Tip(val amount: Long, val to: TipTarget)

/**
 * Итог выхода до подтверждения (ТЗ §3.3, макеты CashOut, DebtCashOut).
 * @property tipAsChips получатель за столом и уже покупал фишки → запишется `TIP_CHIPS`; вышедший → `TIP`.
 * @property withdrawal W этого выхода = F − T.
 * @property outstandingBefore `outstanding` игрока до выхода («Долг игрока −3 400 ₽»).
 * @property balance `outstandingBefore + withdrawal`: > 0 «Банкир выплачивает N ₽», < 0 «Игрок доплачивает N ₽», 0 —
 *   квиты.
 * @property resultThisGame «Результат за игру: +753 фишки» = (W всех выходов + этот W) − chipsBought.
 * @property minutesPlayed от первого закупа; null — закупов нет.
 */
data class CashOutQuote(
    val playerId: PlayerId,
    val chips: Long,
    val tip: Tip?,
    val tipAsChips: Boolean,
    val withdrawal: Long,
    val sessionCharged: Long,
    val sessionPaid: Long,
    val carriedOver: Long,
    val outstandingBefore: Long,
    val balance: Long,
    val resultThisGame: Long,
    val minutesPlayed: Long?,
    val isEarlyExit: Boolean,
    val blockers: List<DomainError>,
    val warnings: List<DomainWarning>,
) {
    val canConfirm: Boolean get() = blockers.isEmpty()
}

/** Как закрыт расчёт при выходе (ТЗ §3.7). */
sealed interface SettlementChoice {
    /** «Расчёт произведён» / «Получил N ₽»: `PAYOUT(balance)` или `PAYMENT(−balance)`; при balance = 0 — ничего. */
    data object Full : SettlementChoice

    /** «Отдал часть…»: 0 < amount < |balance|, в направлении знака balance. Остаток — долг (или долг банкира). */
    data class Partial(val amount: Long) : SettlementChoice

    /** «Не заплатил» (или банкир не выплатил): денежных записей нет, игрок → LEFT_WITH_DEBT при balance < 0. */
    data object None : SettlementChoice
}

object CashOuts {
    /**
     * Котировка выхода. Блокеры: игрок не PLAYING, статус игры не RUNNING/SETTLING, F < 0 или > MAX_AMOUNT,
     * чаевые ≤ 0, > F, себе, получателю void/не из этой игры. Предупреждение ChipsExceedInPlay (`F > chipsInPlay`).
     * Ранний выход — не блокер котировки, а флаг `isEarlyExit` (команда потребует подтверждения).
     */
    fun quote(snapshot: GameSnapshot, playerId: PlayerId, chips: Long, tip: Tip?, now: Instant): CashOutQuote {
        val game = GameStates.compute(snapshot)
        val state = game.player(playerId)?.takeUnless { it.isVoid }
        val recipient = (tip?.to as? TipTarget.ToPlayer)?.let { game.player(it.playerId)?.takeUnless { r -> r.isVoid } }
        val tipAmount = tip?.amount ?: 0L
        val blockers = blockers(snapshot, playerId, state, chips) +
            (tip?.let { tipBlockers(it, playerId, chips, recipient) } ?: emptyList())
        val session = state?.session
        val outstandingBefore = state?.outstanding ?: 0L
        val withdrawal = chips - tipAmount
        val chipsInPlay = game.table.chipsInPlay
        return CashOutQuote(
            playerId = playerId,
            chips = chips,
            tip = tip,
            tipAsChips = recipient?.isSeated == true,
            withdrawal = withdrawal,
            sessionCharged = session?.charged ?: 0L,
            sessionPaid = session?.paid ?: 0L,
            carriedOver = session?.carriedOver ?: outstandingBefore,
            outstandingBefore = outstandingBefore,
            balance = outstandingBefore + withdrawal,
            resultThisGame = (state?.withdrawal ?: 0L) + withdrawal - (state?.chipsBought ?: 0L),
            minutesPlayed = state?.playedFor(now)?.inWholeMinutes,
            isEarlyExit = state?.isEarlyExit(snapshot.game.rules, now) == true,
            blockers = blockers,
            warnings = if (chips >
                chipsInPlay
            ) {
                listOf(DomainWarning.ChipsExceedInPlay(chips, chipsInPlay))
            } else {
                emptyList()
            },
        )
    }

    /** Быстрое округление «до 100»/«до 500»: `chips % step` (3 753 → 53 и 253). `step > 0`. */
    fun roundingTip(chips: Long, step: Long): Long {
        require(step > 0) { "step $step <= 0" }
        require(chips >= 0) { "chips $chips < 0" }
        return chips % step
    }

    private fun blockers(
        snapshot: GameSnapshot,
        playerId: PlayerId,
        state: PlayerState?,
        chips: Long,
    ): List<DomainError> = buildList {
        if (snapshot.game.status !in CASH_OUT_STATUSES) {
            add(DomainError.WrongGameStatus(snapshot.game.status, CASH_OUT_STATUSES))
        }
        when {
            state == null -> add(DomainError.PlayerNotFound(playerId))
            state.status != PlayerStatus.PLAYING -> add(DomainError.PlayerNotPlaying(playerId, state.status))
        }
        when {
            chips < 0 -> add(DomainError.AmountNotPositive)
            chips > Money.MAX_AMOUNT -> add(DomainError.AmountTooLarge)
        }
    }

    private fun tipBlockers(tip: Tip, giver: PlayerId, chips: Long, recipient: PlayerState?): List<DomainError> =
        buildList {
            when {
                tip.amount <= 0 -> add(DomainError.TipNotPositive)
                tip.amount > chips -> add(DomainError.TipExceedsChips(chips))
            }
            val to = tip.to
            if (to is TipTarget.ToPlayer) {
                when {
                    to.playerId == giver -> add(DomainError.TipToSelf)

                    recipient == null -> add(DomainError.TipRecipientInvalid(to.playerId))

                    recipient.status == PlayerStatus.PLAYING && !recipient.isSeated ->
                        add(DomainError.TipRecipientNotSeated(to.playerId))
                }
            }
        }

    internal val CASH_OUT_STATUSES = setOf(GameStatus.RUNNING, GameStatus.SETTLING)
}
