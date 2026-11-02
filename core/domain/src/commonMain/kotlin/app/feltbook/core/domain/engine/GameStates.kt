package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.Game
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.Player
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Производные по игроку (§3.3). Знаки: `outstanding > 0` — банкир должен игроку, `< 0` — игрок должен.
 *
 * @property isBanker игрок — текущий банкир (`game.bankerPlayerId`).
 * @property isVoid у игрока есть записи и все они отменены (ошибочное добавление) — стол его не показывает,
 *   журнал показывает.
 * @property firstBuyInAt первый действующий `BUY_IN` в игре — от него таймер «в игре 47 мин» и ранний выход
 *   (ТЗ §3.4, §4).
 * @property lastCashOutAt последний действующий `CASH_OUT` («settledAt» ТЗ §8), null — ещё не выходил.
 * @property buyInCount число действующих операций покупки; `rebuyCount = max(0, buyInCount − 1)` — значок «докуп ×N».
 * @property tipsToBanker сколько этот игрок дал чаевых банкиру (расшифровка «Банкиру» по игрокам).
 * @property chipTipsReceived фишечные чаевые, полученные за столом (справочно: уже внутри его F).
 * @property withdrawal W по всем выходам этой игры.
 * @property balanceAtExit «Баланс» ТЗ §3.3 на момент последнего выхода (до расчёта); null — не выходил.
 * @property result `W − chipsBought`; null, пока игрок за столом.
 * @property session текущая сессия (только у PLAYING).
 * @property earlyExit хотя бы один действующий выход помечен «Исключительный случай».
 */
data class PlayerState(
    val player: Player,
    val isBanker: Boolean,
    val status: PlayerStatus,
    val isVoid: Boolean,
    val firstBuyInAt: Instant?,
    val lastCashOutAt: Instant?,
    val buyInCount: Int,
    val rebuyCount: Int,
    val chipsBought: Long,
    val rake: Long,
    val bankerFee: Long,
    val charged: Long,
    val paid: Long,
    val paidOut: Long,
    val forgiven: Long,
    val chipsCashedOut: Long,
    val tipsGiven: Long,
    val tipsToBanker: Long,
    val tipsReceived: Long,
    val chipTipsReceived: Long,
    val withdrawal: Long,
    val outstanding: Long,
    val balanceAtExit: Long?,
    val result: Long?,
    val session: SessionFigures?,
    val earlyExit: Boolean,
) {
    /** Сколько игрок должен банкиру сейчас (≥ 0). */
    val debt: Long get() = maxOf(0L, -outstanding)

    /** Сколько банкир должен игроку сейчас (≥ 0). */
    val owedByBanker: Long get() = maxOf(0L, outstanding)

    /** Время от первого закупа; null — закупов нет. */
    fun playedFor(now: Instant): Duration? = firstBuyInAt?.let { now - it }

    /** Выход сейчас — ранний (`now < firstBuyInAt + minPlayMinutes`). Без закупов — false. */
    fun isEarlyExit(rules: GameRules, now: Instant): Boolean =
        firstBuyInAt != null && now < firstBuyInAt + rules.minPlayMinutes.minutes
}

/** Текущая сессия игрока (после возврата — только новые покупки и оплаты, ТЗ §4). */
data class SessionFigures(
    val startedAt: Instant,
    val chipsBought: Long,
    val charged: Long,
    val paid: Long,
    /**
     * Долг (< 0) или переплата (> 0) прошлых выходов этой игры, переходящие в этот выход:
     * `outstanding − (paid − charged)` — всё, что не входит в покупки и оплаты сессии.
     */
    val carriedOver: Long,
)

/** Доход одного банкира в игре (ТЗ §6 «Доход разных банкиров»). `net = fees + tips − forgiven`. */
data class BankerIncome(val bankerName: String, val fees: Long, val tips: Long, val forgiven: Long, val taken: Long) {
    val net: Long get() = fees + tips - forgiven
}

/** Табло стола (ТЗ §4 «Расчёт и сводка», п. 1), §3.4 этого документа. */
data class TableSummary(
    val chipsIssued: Long,
    val chipsReturned: Long,
    val chipsInPlay: Long,
    val rebuyCount: Int,
    val playersAtTable: Int,
    val playersLeft: Int,
    val playersOwe: Long,
    val bankerOwes: Long,
    val cashOnHand: Long,
    val rakeAccrued: Long,
    val rakePaid: Long,
    val rakeDue: Long,
    val bankerIncome: Long,
    val incomeTaken: Long,
    /** По `bankerName` в порядке первого появления. Один банкир — один элемент. */
    val incomeByBanker: List<BankerIncome>,
    val expectedCash: Long,
)

/** Состояние игры: игроки (без `isVoid` — отдельным списком не нужны, фильтрует UI) и табло. */
data class GameState(val snapshot: GameSnapshot, val players: List<PlayerState>, val table: TableSummary) {
    val game: Game get() = snapshot.game

    fun player(id: PlayerId): PlayerState? = players.firstOrNull { it.player.id == id }

    /** «Игра с долгом»: есть игрок LEFT_WITH_DEBT. */
    val hasOpenDebt: Boolean get() = players.any { it.status == PlayerStatus.LEFT_WITH_DEBT }
}

object GameStates {
    fun compute(snapshot: GameSnapshot): GameState {
        val effective = Ledger.effective(snapshot.entries)
        val players = snapshot.players.map { playerState(snapshot, it, effective) }
        return GameState(snapshot, players, table(snapshot.game, effective, players))
    }

    private fun playerState(snapshot: GameSnapshot, player: Player, effective: List<LedgerEntry>): PlayerState {
        val id = player.id
        val own = effective.filter { it.playerId == id }
        val sums = TypeSums(own)
        val buyIns = own.filter { it.type == EntryType.BUY_IN }
        val lastCashOut = own.lastOrNull { it.type == EntryType.CASH_OUT }
        val received = effective.filter { it.counterpartyPlayerId == id }
        val tipsReceived = TypeSums(received)[EntryType.TIP]
        val tipsGiven = sums[EntryType.TIP] + sums[EntryType.TIP_CHIPS]
        val chipsBought = sums[EntryType.BUY_IN]
        val charged = chipsBought + sums[EntryType.RAKE] + sums[EntryType.BANKER_FEE]
        val withdrawal = sums[EntryType.CASH_OUT] - tipsGiven + tipsReceived
        val outstanding = outstandingOf(id, effective)
        val sessionStart = buyIns.firstOrNull { lastCashOut == null || it.seq > lastCashOut.seq }
        val status = when {
            lastCashOut == null || sessionStart != null -> PlayerStatus.PLAYING
            outstanding >= 0 -> PlayerStatus.CASHED_OUT
            else -> PlayerStatus.LEFT_WITH_DEBT
        }
        return PlayerState(
            player = player,
            isBanker = snapshot.game.bankerPlayerId == id,
            status = status,
            isVoid = isVoid(snapshot, id, effective),
            firstBuyInAt = buyIns.firstOrNull()?.createdAt,
            lastCashOutAt = lastCashOut?.createdAt,
            buyInCount = buyIns.size,
            rebuyCount = maxOf(0, buyIns.size - 1),
            chipsBought = chipsBought,
            rake = sums[EntryType.RAKE],
            bankerFee = sums[EntryType.BANKER_FEE],
            charged = charged,
            paid = sums[EntryType.PAYMENT],
            paidOut = sums[EntryType.PAYOUT],
            forgiven = sums[EntryType.DEBT_FORGIVEN],
            chipsCashedOut = sums[EntryType.CASH_OUT],
            tipsGiven = tipsGiven,
            tipsToBanker = own.filter {
                it.type == EntryType.TIP && it.counterpartyPlayerId == null
            }.sumOf { it.amount },
            tipsReceived = tipsReceived,
            chipTipsReceived = TypeSums(received)[EntryType.TIP_CHIPS],
            withdrawal = withdrawal,
            outstanding = outstanding,
            balanceAtExit = lastCashOut?.let { balanceAt(id, it, effective) },
            result = if (status == PlayerStatus.PLAYING) null else withdrawal - chipsBought,
            session = if (status == PlayerStatus.PLAYING) sessionStart?.let { session(it, own, outstanding) } else null,
            earlyExit = own.any { it.type == EntryType.CASH_OUT && it.earlyExit },
        )
    }

    private fun isVoid(snapshot: GameSnapshot, id: PlayerId, effective: List<LedgerEntry>): Boolean {
        fun LedgerEntry.touches() = playerId == id || counterpartyPlayerId == id
        return snapshot.entries.any { it.type != EntryType.REVERSAL && it.touches() } && effective.none { it.touches() }
    }

    private fun balanceAt(id: PlayerId, cashOut: LedgerEntry, effective: List<LedgerEntry>): Long {
        val end = effective.filter { it.operationId == cashOut.operationId }.maxOf { it.seq }
        return outstandingOf(id, effective.filter { it.seq <= end })
    }

    private fun session(start: LedgerEntry, own: List<LedgerEntry>, outstanding: Long): SessionFigures {
        val sums = TypeSums(own.filter { it.seq >= start.seq })
        val charged = sums[EntryType.BUY_IN] + sums[EntryType.RAKE] + sums[EntryType.BANKER_FEE]
        val paid = sums[EntryType.PAYMENT]
        return SessionFigures(
            startedAt = start.createdAt,
            chipsBought = sums[EntryType.BUY_IN],
            charged = charged,
            paid = paid,
            carriedOver = outstanding - paid + charged,
        )
    }

    private fun table(game: Game, effective: List<LedgerEntry>, players: List<PlayerState>): TableSummary {
        val sums = TypeSums(effective)
        val chipsIssued = sums[EntryType.BUY_IN]
        val chipsReturned = sums[EntryType.CASH_OUT] - sums[EntryType.TIP_CHIPS]
        val rakeDue = sums[EntryType.RAKE] - sums[EntryType.RAKE_PAID]
        val incomeByBanker = incomeByBanker(game, effective)
        val bankerIncome = incomeByBanker.sumOf { it.net }
        val incomeTaken = sums[EntryType.INCOME_TAKEN]
        return TableSummary(
            chipsIssued = chipsIssued,
            chipsReturned = chipsReturned,
            chipsInPlay = chipsIssued - chipsReturned,
            rebuyCount = players.sumOf { it.rebuyCount },
            playersAtTable = players.count { it.isSeated },
            playersLeft = players.count { !it.isVoid && it.status != PlayerStatus.PLAYING },
            playersOwe = players.sumOf { it.debt },
            bankerOwes = players.sumOf { it.owedByBanker },
            cashOnHand = sums[EntryType.PAYMENT] - sums[EntryType.PAYOUT] - sums[EntryType.RAKE_PAID] - incomeTaken,
            rakeAccrued = sums[EntryType.RAKE],
            rakePaid = sums[EntryType.RAKE_PAID],
            rakeDue = rakeDue,
            bankerIncome = bankerIncome,
            incomeTaken = incomeTaken,
            incomeByBanker = incomeByBanker,
            expectedCash = rakeDue + bankerIncome - incomeTaken,
        )
    }

    private fun incomeByBanker(game: Game, effective: List<LedgerEntry>): List<BankerIncome> {
        val relevant = effective.filter {
            it.type in INCOME_TYPES &&
                !(it.type == EntryType.TIP && it.counterpartyPlayerId != null)
        }
        val names = (relevant.map { it.bankerName } + game.currentBankerName).distinct()
        return names.map { name ->
            val sums = TypeSums(relevant.filter { it.bankerName == name })
            BankerIncome(
                bankerName = name,
                fees = sums[EntryType.BANKER_FEE],
                tips = sums[EntryType.TIP],
                forgiven = sums[EntryType.DEBT_FORGIVEN],
                taken = sums[EntryType.INCOME_TAKEN],
            )
        }
    }

    private val INCOME_TYPES =
        setOf(EntryType.BANKER_FEE, EntryType.TIP, EntryType.DEBT_FORGIVEN, EntryType.INCOME_TAKEN)
}

/** За столом и уже покупал: банкир-игрок без закупа не считается сидящим и не мешает закрыть игру. */
internal val PlayerState.isSeated: Boolean
    get() = status == PlayerStatus.PLAYING && !isVoid && buyInCount > 0

/** `outstanding` игрока по данному набору действующих записей (§3.3). */
internal fun outstandingOf(id: PlayerId, entries: List<LedgerEntry>): Long = entries.sumOf { contribution(id, it) }

private fun contribution(id: PlayerId, entry: LedgerEntry): Long = when {
    entry.playerId == id -> when (entry.type) {
        EntryType.CASH_OUT, EntryType.PAYMENT, EntryType.DEBT_FORGIVEN -> entry.amount

        EntryType.BUY_IN, EntryType.RAKE, EntryType.BANKER_FEE, EntryType.PAYOUT,
        EntryType.TIP, EntryType.TIP_CHIPS,
        -> -entry.amount

        else -> 0L
    }

    entry.counterpartyPlayerId == id && entry.type == EntryType.TIP -> entry.amount

    else -> 0L
}

/** Суммы по типам записей. */
private class TypeSums(entries: List<LedgerEntry>) {
    private val sums: Map<EntryType, Long> =
        entries.groupBy { it.type }.mapValues { (_, list) -> list.sumOf { it.amount } }

    operator fun get(type: EntryType): Long = sums[type] ?: 0L
}
