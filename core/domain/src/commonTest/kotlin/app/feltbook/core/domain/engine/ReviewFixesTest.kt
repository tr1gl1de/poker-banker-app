package app.feltbook.core.domain.engine

import app.feltbook.core.domain.Example81
import app.feltbook.core.domain.T0
import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.entry
import app.feltbook.core.domain.entryId
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.Handover
import app.feltbook.core.domain.model.HandoverId
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.apply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Сценарии ревью M1: банкир за столом, отмены, ломающие расчёт, чаевые фишками, импорт. */
class BankerSeatingTest {
    @Test
    fun `void-банкир возвращается по имени банкиром — без сбора`() {
        val table = TestTable(banker = "Андрей", bankerPlays = true)
        table.buy("Андрей", 1_000)
        table.reverse(table.lastOp("Андрей", EntryType.BUY_IN))
        assertTrue(table.state.player(table.id("Андрей"))!!.isVoid)
        val quote = Pricing.quoteNewPlayer(table.snapshot, "Андрей", 1_000)
        assertEquals(0L, quote.bankerFee)
        assertTrue(quote.feeWaivedForBanker)
        val change = Commands.addPlayer(table.snapshot, "Андрей", 1_000, BuyInPayment.DEBT, table.clock.stamp())
        assertNull(change.game, "банкир уже за столом")
        assertTrue(change.entries.none { it.type == EntryType.BANKER_FEE })
    }

    @Test
    fun `банкир, начавший «не играет», садится за стол — становится банкиром-игроком без сбора`() {
        val table = TestTable(banker = "Олег", bankerPlays = false)
        val quote = Pricing.quoteNewPlayer(table.snapshot, " Олег ", 1_000)
        assertEquals(0L, quote.bankerFee)
        assertEquals(100L, quote.rake)
        val oleg = table.add("Олег", 1_000)
        assertEquals(oleg, table.snapshot.game.bankerPlayerId)
        assertTrue(table.state.player(oleg)!!.isBanker)
        assertTrue(table.snapshot.entries.none { it.type == EntryType.BANKER_FEE })
        val rebuy = Pricing.quoteBuyIn(table.snapshot, oleg, 1_000)
        assertEquals(0L, rebuy.bankerFee)
        // Другой игрок со сбором как обычно; банкир уже сидит — второй «Олег» невозможен (NameTaken).
        assertEquals(100L, Pricing.quoteNewPlayer(table.snapshot, "Борис", 1_000).bankerFee)
    }
}

class ReviewReversalTest {
    @Test
    fun `первый закуп при действующем докупе не отменяется — иначе банкир теряет сбор`() {
        val table = TestTable().apply {
            add("Андрей", 1_000)
            buy("Андрей", 1_000)
        }
        val first = table.snapshot.entries.first().operationId
        assertViolation(DomainError.OperationNotReversible(NotReversibleReason.LATER_PURCHASES_EXIST)) {
            table.reverse(first)
        }
        table.reverse(table.lastOp("Андрей", EntryType.BUY_IN))
        table.reverse(first)
        assertTrue(table.state.player(table.id("Андрей"))!!.isVoid)
        assertEquals(100L, Pricing.quoteNewPlayer(table.snapshot, "Андрей", 1_000).bankerFee, "сбор снова берётся")
    }

    @Test
    fun `выход с чаевыми фишками не отменяется, если получатель уже вышел — иначе двойная выплата`() {
        val table = TestTable().apply {
            add("Егор", 1_000, BuyInPayment.PAID)
            add("Дима", 1_000, BuyInPayment.PAID)
            later()
            cashOut("Егор", 1_500, Tip(100, TipTarget.ToPlayer(id("Дима"))))
            cashOut("Дима", 600)
        }
        val egorExit = table.lastOp("Егор", EntryType.CASH_OUT)
        assertViolation(DomainError.OperationNotReversible(NotReversibleReason.TIP_RECIPIENT_CASHED_OUT)) {
            table.reverse(egorExit)
        }
        val journal = Journal.forPlayer(table.snapshot, table.id("Егор")).first { it.operationId == egorExit }
        assertFalse(journal.reversible)
        // Порядок исправления: сначала выход получателя, потом дарителя.
        table.reverse(table.lastOp("Дима", EntryType.CASH_OUT))
        table.reverse(egorExit)
        assertEquals(PlayerStatus.PLAYING, table.state.player(table.id("Егор"))!!.status)
        assertCashIdentity(table.state.table)
    }

    @Test
    fun `выход с чаевыми фишками отменяется, пока получатель за столом`() {
        val table = TestTable().apply {
            add("Егор", 1_000, BuyInPayment.PAID)
            add("Дима", 1_000, BuyInPayment.PAID)
            later()
            cashOut("Егор", 1_500, Tip(100, TipTarget.ToPlayer(id("Дима"))))
        }
        table.reverse(table.lastOp("Егор", EntryType.CASH_OUT))
        assertEquals(0L, table.state.player(table.id("Дима"))!!.chipTipsReceived)
    }
}

class ChipTipRecipientTest {
    @Test
    fun `чаевые фишками банкиру-игроку без закупа — блокер TipRecipientNotSeated`() {
        val table = TestTable(banker = "Андрей", bankerPlays = true).apply {
            add("Егор", 1_000, BuyInPayment.PAID)
            later()
        }
        val andrey = table.id("Андрей")
        val tip = Tip(100, TipTarget.ToPlayer(andrey))
        val quote = CashOuts.quote(table.snapshot, table.id("Егор"), 1_500, tip, table.clock.now)
        assertEquals(listOf<DomainError>(DomainError.TipRecipientNotSeated(andrey)), quote.blockers)
        assertFalse(quote.tipAsChips)
        assertViolation(DomainError.TipRecipientNotSeated(andrey)) { table.cashOut("Егор", 1_500, tip) }
        // Чаевые банкиру — в доход, как раньше.
        table.cashOut("Егор", 1_500, Tip(100, TipTarget.Banker))
        assertEquals(200L, table.state.table.bankerIncome)
    }
}

class ImportReviewTest {
    private val table = example81()
    private val full = table.snapshot

    @Test
    fun `входящий журнал — строгий префикс локального — LOCAL_NEWER`() {
        val older = full.copy(entries = full.entries.take(12))
        assertEquals(ImportPlan.Conflict(ImportConflictReason.LOCAL_NEWER), Imports.plan(older, full, null))
    }

    @Test
    fun `тот же журнал, другие строки — закрытую игру не переоткрываем, имена не откатываем`() {
        val closed = full.apply(Commands.finish(full)).let {
            it.apply(Commands.close(it, true, emptySet(), table.clock.stamp()))
        }
        val oldBackup = closed.copy(game = full.game, entries = closed.entries)
        assertEquals(ImportPlan.Conflict(ImportConflictReason.LOCAL_NEWER), Imports.plan(oldBackup, closed, null))
        val renamed = full.apply(Commands.renamePlayer(full, full.players.first().id, "Андрюха"))
        assertEquals(ImportPlan.Conflict(ImportConflictReason.LOCAL_NEWER), Imports.plan(full, renamed, null))
    }

    @Test
    fun `своя переданная игра без новых записей снова активна — Extend`() {
        val handed = full.copy(game = full.game.copy(status = GameStatus.HANDED_OVER))
        val plan = assertIs<ImportPlan.Extend>(Imports.plan(full, handed, null))
        assertEquals(GameStatus.RUNNING, plan.change.game!!.status)
        assertTrue(plan.change.entries.isEmpty())
        val stillHanded = assertIs<ImportPlan.Identical>(Imports.plan(handed, handed, null))
        assertEquals(ImportPlan.Identical, stillHanded)
    }
}

class ValidatorReviewTest {
    private val base = Example81.snapshot(Example81.full)

    private fun codes(snapshot: GameSnapshot) = LedgerValidator.validate(snapshot).map { it.code }

    private fun act(incomeTaken: Long, ledgerSeq: Long) =
        Handover(HandoverId(Uuid.fromLongs(3, 3)), base.game.id, "Олег", "Влад", 0, incomeTaken, ledgerSeq, T0)

    @Test
    fun `дубль игрока и чаевые себе`() {
        assertEquals(
            listOf(LedgerProblemCode.DUPLICATE_PLAYER_ID),
            codes(
                base.copy(
                    players =
                        base.players + base.players.first(),
                ),
            ),
        )
        val self = base.copy(
            entries =
                base.entries + entry(20, EntryType.TIP, Example81.boris, 1, counterparty = Example81.boris),
        )
        assertEquals(listOf(LedgerProblemCode.TIP_TO_SELF), codes(self))
    }

    @Test
    fun `акт передачи — доход и seq сверяются с журналом, INCOME_TAKEN не отменяется`() {
        val taken = entry(20, EntryType.INCOME_TAKEN, null, 300)
        val ok = base.copy(entries = base.entries + taken, handovers = listOf(act(300, 20)))
        assertTrue(codes(ok).isEmpty())
        assertTrue(codes(base.copy(handovers = listOf(act(0, 19)))).isEmpty())
        assertEquals(
            listOf(LedgerProblemCode.HANDOVER_INCOME_MISMATCH, LedgerProblemCode.HANDOVER_INCOME_MISMATCH),
            codes(ok.copy(handovers = listOf(act(200, 20)))),
        )
        assertEquals(
            listOf(LedgerProblemCode.HANDOVER_SEQ_OUT_OF_RANGE),
            codes(base.copy(handovers = listOf(act(0, 20)))),
        )
        val reversed = ok.copy(entries = ok.entries + entry(21, EntryType.REVERSAL, null, 300, reverses = entryId(20)))
        assertEquals(listOf(LedgerProblemCode.INCOME_TAKEN_REVERSED), codes(reversed))
    }

    @Test
    fun `снимок после реальной передачи — без проблем`() {
        val start = example84Start(GameRules(bankerFeeMode = BankerFeeMode.EACH_FIXED))
        val prepared = Handovers.prepare(start.snapshot, "Влад", keepIncome = true, stamp = start.clock.stamp())
        assertTrue(LedgerValidator.validate(prepared.outgoing).isEmpty())
        assertTrue(LedgerValidator.validate(start.snapshot.apply(prepared.senderChange)).isEmpty())
    }
}
