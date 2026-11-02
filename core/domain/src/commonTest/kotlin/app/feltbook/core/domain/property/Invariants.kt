package app.feltbook.core.domain.property

import app.feltbook.core.domain.engine.CloseConfirmation
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.engine.ImportPlan
import app.feltbook.core.domain.engine.Imports
import app.feltbook.core.domain.engine.Ledger
import app.feltbook.core.domain.engine.LedgerValidator
import app.feltbook.core.domain.engine.PlayerState
import app.feltbook.core.domain.engine.Settlements
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Инварианты контракта §7 (docs/plan/m1-domain.md) для одного снимка игры. Каждая проверка бросает AssertionError
 * с понятным текстом; seed и трассу шагов добавляет [Sim].
 */
internal object Invariants {
    /** §7.1: журнал и акты только дописываются, игроки не пропадают. */
    fun appendOnly(before: GameSnapshot, after: GameSnapshot) {
        assertEquals(before.entries, after.entries.take(before.entries.size), "§7.1 журнал — префикс нового")
        assertEquals(before.handovers, after.handovers.take(before.handovers.size), "акты только дописываются")
        val ids = after.players.map { it.id }.toSet()
        assertTrue(before.players.all { it.id in ids }, "игроки не удаляются")
    }

    /** §7.1: валидатор пуст (в т. ч. seq 1..n без пропусков). */
    fun valid(snapshot: GameSnapshot) {
        val problems = LedgerValidator.validate(snapshot)
        assertTrue(problems.isEmpty(), "§7.1 валидатор: $problems")
        snapshot.entries.forEachIndexed { i, e -> assertEquals(i + 1L, e.seq, "seq без пропусков") }
    }

    /** §7.2, §7.3, §7.7 и сумма балансов по табло и итогу. */
    fun money(snapshot: GameSnapshot) {
        val state = GameStates.compute(snapshot)
        val t = state.table
        assertEquals(
            t.cashOnHand,
            t.expectedCash + t.chipsInPlay + t.bankerOwes - t.playersOwe,
            "§7.2 тождество кассы: $t",
        )
        assertEquals(t.chipsIssued - t.chipsReturned, t.chipsInPlay, "§7.3 фишки в игре")
        assertEquals(t.bankerIncome, t.incomeByBanker.sumOf { it.net }, "§7.7 доход = Σ по банкирам")
        assertEquals(t.incomeTaken, t.incomeByBanker.sumOf { it.taken }, "забранный доход = Σ по банкирам")
        assertEquals(t.rakeAccrued - t.rakePaid, t.rakeDue, "рейк к отдаче")
        assertEquals(t.rakeDue + t.bankerIncome - t.incomeTaken, t.expectedCash, "ожидаемая касса")
        assertTrue(t.incomeByBanker.any { it.bankerName == snapshot.game.currentBankerName }, "текущий банкир в доходе")
        assertEquals(t.playersOwe, state.players.sumOf { it.debt }, "долги игроков = Σ")
        assertEquals(t.bankerOwes, state.players.sumOf { it.owedByBanker }, "долг банкира = Σ")
        val outstanding = state.players.sumOf { it.outstanding }
        assertEquals(t.cashOnHand - t.expectedCash - t.chipsInPlay, outstanding, "Σ балансов игроков")
        state.players.forEach { player(it) }
        settlement(snapshot, state.players)
    }

    private fun player(p: PlayerState) {
        assertEquals(p.chipsBought + p.rake + p.bankerFee, p.charged, "начислено ${p.player.name}")
        assertEquals(p.chipsCashedOut - p.tipsGiven + p.tipsReceived, p.withdrawal, "W ${p.player.name}")
        assertEquals(
            p.withdrawal + p.paid + p.forgiven - p.charged - p.paidOut,
            p.outstanding,
            "§3.3 баланс ${p.player.name}",
        )
        assertTrue(p.debt == 0L || p.owedByBanker == 0L)
        when (p.status) {
            PlayerStatus.PLAYING -> assertEquals(null, p.result)
            PlayerStatus.CASHED_OUT -> assertTrue(p.outstanding >= 0)
            PlayerStatus.LEFT_WITH_DEBT -> assertTrue(p.outstanding < 0)
        }
        if (p.status != PlayerStatus.PLAYING) assertEquals(p.withdrawal - p.chipsBought, p.result)
        assertEquals(maxOf(0, p.buyInCount - 1), p.rebuyCount)
    }

    private fun settlement(snapshot: GameSnapshot, players: List<PlayerState>) {
        val s = Settlements.compute(snapshot)
        val t = GameStates.compute(snapshot).table
        assertEquals(t.playersOwe, s.shortage, "недостача = долги игроков")
        assertEquals(t.bankerOwes, s.unpaidPayouts.sumOf { it.amount })
        assertEquals(-t.chipsInPlay, s.chipDiff, "расхождение фишек")
        assertEquals(s.bankerIncome, s.bankerFees + s.tipsToBanker - s.forgiven, "банкиру = сборы + чаевые − прощено")
        val live = players.filterNot { it.isVoid }
        assertEquals(live.map { it.player.id to it.outstanding }, s.rows.map { it.playerId to it.outstanding })
        assertEquals(s.chipDiff != 0L, CloseConfirmation.CHIPS_NOT_RECONCILED in s.requiredConfirmations)
        assertEquals(s.shortage > 0, CloseConfirmation.OPEN_DEBTS in s.requiredConfirmations)
        assertEquals(s.unpaidPayouts.isNotEmpty(), CloseConfirmation.UNPAID_PAYOUTS in s.requiredConfirmations)
    }

    /**
     * Отменённые записи исключены (§3.2, §7.5): состояние равно состоянию журнала, из которого вычеркнуты все
     * отменённые и отменяющие записи.
     */
    fun reversedExcluded(snapshot: GameSnapshot) {
        val effective = Ledger.effective(snapshot.entries)
        val struck = snapshot.copy(entries = effective.mapIndexed { i, e -> e.copy(seq = i + 1L) })
        val expected = GameStates.compute(struck)
        val actual = GameStates.compute(snapshot)
        assertEquals(expected.table, actual.table, "§7.5 табло = журнал без отменённого")
        assertEquals(expected.players.map { it.comparable() }, actual.players.map { it.comparable() })
    }

    private fun PlayerState.comparable() =
        copy(isVoid = false, session = session?.copy(startedAt = Instant.DISTANT_PAST))

    /**
     * Сбор банкира: банкир его не платит никогда; в разовом режиме — ровно один раз с первой действующей покупки
     * игрока, если она сделана не банкиром; в режиме «с каждого» — с каждой небанкирской покупки.
     * @param bankerOps операции покупок, сделанные игроком в момент, когда он был банкиром.
     */
    fun bankerFees(snapshot: GameSnapshot, bankerOps: Set<OperationId>) {
        val rules = snapshot.game.rules
        val effective = Ledger.effective(snapshot.entries)
        val fees = effective.filter { it.type == EntryType.BANKER_FEE }
        assertTrue(fees.none { it.operationId in bankerOps }, "банкир не платит сбор: $fees")
        val buyIns = effective.filter { it.type == EntryType.BUY_IN }
        fun feeExpected(chips: Long) =
            (if (rules.bankerFeeMode.isPercent) (chips * rules.bankerFeeValue + 50) / 100 else rules.bankerFeeValue) > 0
        if (rules.bankerFeeMode.isOnce) {
            buyIns.groupBy { it.playerId }.forEach { (player, purchases) ->
                val own = fees.filter { it.playerId == player }
                val first = purchases.first().operationId
                val expected = if (feeExpected(purchases.first().amount) && first !in bankerOps) 1 else 0
                assertEquals(expected, own.size, "разовый сбор ровно один раз: игрок $player")
                assertTrue(own.all { it.operationId == first }, "разовый сбор — с первой покупки")
            }
        } else {
            buyIns.forEach { buy ->
                val expected = if (feeExpected(buy.amount) && buy.operationId !in bankerOps) 1 else 0
                assertEquals(expected, fees.count { it.operationId == buy.operationId }, "сбор с каждой покупки")
            }
        }
        assertTrue(fees.all { fee -> buyIns.any { it.operationId == fee.operationId } }, "сбор только с покупкой")
        val rakes = effective.filter { it.type == EntryType.RAKE }
        assertTrue(rakes.all { r -> buyIns.any { it.operationId == r.operationId } }, "рейк только с покупкой")
    }

    /** §7.6 + импорт своего экспорта: encode → decode равен, `Imports.plan` своего же снимка — Identical. */
    fun codec(snapshot: GameSnapshot, activeGameId: GameId?, at: Instant) {
        val text = FeltbookCodec.encode(FeltbookPackage.BackupPackage(at, listOf(snapshot), emptyList()), at)
        val decoded = assertIs<DecodeResult.Ok>(FeltbookCodec.decode(text), "§7.6 пакет читается")
        val games = assertIs<FeltbookPackage.BackupPackage>(decoded.pkg).games
        assertEquals(listOf(snapshot), games, "§7.6 encode → decode без потерь")
        val plan = Imports.plan(games.single(), snapshot, activeGameId)
        assertEquals(ImportPlan.Identical, plan, "импорт своего экспорта")
    }

    /** Строки игры по статусу. */
    fun gameRow(snapshot: GameSnapshot) {
        val game = snapshot.game
        assertEquals(game.status == GameStatus.CLOSED, game.closedAt != null, "closedAt только у закрытой")
        game.bankerPlayerId?.let { id -> assertTrue(snapshot.players.any { it.id == id }, "банкир-игрок есть") }
    }
}
