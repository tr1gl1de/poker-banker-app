package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerStatus

/**
 * Строка таблицы «Расчёт с игроками» (макеты Settlement, DebtSettlement):
 * «Андрей платит 2 300 ₽ · начислено 2 300 · оплачено 0 · вывод 0».
 * @property paidBeforeExit оплачено до последнего выхода (по `seq`), без расчёта при выходе и поздних погашений.
 * @property balanceAtExit «платит» (< 0) / «получает» (> 0) — сумма расчёта при выходе.
 * @property outstanding остаток сейчас: 0 — «✓ рассчитан», < 0 — «долг N», > 0 — «банкир должен N».
 */
data class SettlementRow(
    val playerId: PlayerId,
    val name: String,
    val isBanker: Boolean,
    val status: PlayerStatus,
    val charged: Long,
    val paidBeforeExit: Long,
    val withdrawal: Long,
    val tipsGiven: Long,
    val balanceAtExit: Long?,
    val outstanding: Long,
    val earlyExit: Boolean,
)

/** Должник/кредитор для блоков «Не оплачено» и «Ещё не выплачено». */
data class PlayerAmount(val playerId: PlayerId, val name: String, val amount: Long)

/**
 * Итоговый расчёт (ТЗ §3.5, §4 п. 3). Тождество: `cashOnHand = expectedCash − chipDiff + Σ unpaidPayouts − Σ
 *   unpaidDebts`
 * (когда все вышли; см. §3.4 документа).
 * @property expectedCash «Должно быть на руках/в кассе» = rakeDue + bankerIncome − incomeTaken.
 * @property unpaidDebts блок «Не оплачено»; `shortage = Σ` — «Недостача N ₽ — не получено от X».
 * @property requiredConfirmations что нужно подтвердить для закрытия ([CloseConfirmation]).
 * @property blockers сейчас — только PlayersStillPlaying / WrongGameStatus.
 */
data class GameSettlement(
    val chipsIssued: Long,
    val chipsReturned: Long,
    val chipDiff: Long,
    val rebuyCount: Int,
    val playerCount: Int,
    val rakeAccrued: Long,
    val rakePaid: Long,
    val rakeDue: Long,
    val bankerFees: Long,
    val tipsToBanker: Long,
    val forgiven: Long,
    val bankerIncome: Long,
    val incomeTaken: Long,
    val incomeByBanker: List<BankerIncome>,
    val expectedCash: Long,
    val cashOnHand: Long,
    val unpaidDebts: List<PlayerAmount>,
    val unpaidPayouts: List<PlayerAmount>,
    val rows: List<SettlementRow>,
    val requiredConfirmations: Set<CloseConfirmation>,
    val blockers: List<DomainError>,
) {
    val chipsReconciled: Boolean get() = chipDiff == 0L
    val shortage: Long get() = unpaidDebts.sumOf { it.amount }
}

object Settlements {
    fun compute(snapshot: GameSnapshot): GameSettlement {
        val state = GameStates.compute(snapshot)
        val table = state.table
        val players = state.players.filterNot { it.isVoid }
        val effective = Ledger.effective(snapshot.entries)
        val unpaidDebts = players.filter { it.debt > 0 }.map { PlayerAmount(it.player.id, it.player.name, it.debt) }
        val unpaidPayouts = players.filter { it.owedByBanker > 0 }.map {
            PlayerAmount(it.player.id, it.player.name, it.owedByBanker)
        }
        val chipDiff = table.chipsReturned - table.chipsIssued
        val stillPlaying = players.filter { it.isSeated }.map { it.player.id }
        return GameSettlement(
            chipsIssued = table.chipsIssued,
            chipsReturned = table.chipsReturned,
            chipDiff = chipDiff,
            rebuyCount = table.rebuyCount,
            playerCount = players.size,
            rakeAccrued = table.rakeAccrued,
            rakePaid = table.rakePaid,
            rakeDue = table.rakeDue,
            bankerFees = table.incomeByBanker.sumOf { it.fees },
            tipsToBanker = table.incomeByBanker.sumOf { it.tips },
            forgiven = table.incomeByBanker.sumOf { it.forgiven },
            bankerIncome = table.bankerIncome,
            incomeTaken = table.incomeTaken,
            incomeByBanker = table.incomeByBanker,
            expectedCash = table.expectedCash,
            cashOnHand = table.cashOnHand,
            unpaidDebts = unpaidDebts,
            unpaidPayouts = unpaidPayouts,
            rows = players.map { row(it, effective) },
            requiredConfirmations = buildSet {
                if (chipDiff != 0L) add(CloseConfirmation.CHIPS_NOT_RECONCILED)
                if (unpaidDebts.isNotEmpty()) add(CloseConfirmation.OPEN_DEBTS)
                if (unpaidPayouts.isNotEmpty()) add(CloseConfirmation.UNPAID_PAYOUTS)
            },
            blockers = buildList {
                if (snapshot.game.status != GameStatus.SETTLING) {
                    add(DomainError.WrongGameStatus(snapshot.game.status, setOf(GameStatus.SETTLING)))
                }
                if (stillPlaying.isNotEmpty()) add(DomainError.PlayersStillPlaying(stillPlaying))
            },
        )
    }

    private fun row(player: PlayerState, effective: List<LedgerEntry>): SettlementRow {
        val id = player.player.id
        val lastExitSeq =
            effective.lastOrNull { it.playerId == id && it.type == EntryType.CASH_OUT }?.seq ?: Long.MAX_VALUE
        return SettlementRow(
            playerId = id,
            name = player.player.name,
            isBanker = player.isBanker,
            status = player.status,
            charged = player.charged,
            paidBeforeExit = effective
                .filter { it.playerId == id && it.type == EntryType.PAYMENT && it.seq < lastExitSeq }
                .sumOf { it.amount },
            withdrawal = player.withdrawal,
            tipsGiven = player.tipsGiven,
            balanceAtExit = player.balanceAtExit,
            outstanding = player.outstanding,
            earlyExit = player.earlyExit,
        )
    }
}
