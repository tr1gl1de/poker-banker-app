package app.feltbook.core.domain.engine

import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.emptyGame
import app.feltbook.core.domain.gameId
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.apply
import app.feltbook.core.domain.playerId
import app.feltbook.core.domain.tableFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Пример 8.3 до seq 10 включительно: банкир Андрей играет, Дима в долг, Егор оплатил. */
fun example84Start(rules: GameRules = GameRules.DEFAULT): TestTable =
    TestTable(rules = rules, banker = "Андрей", bankerPlays = true).apply {
        buy("Андрей", 1_000, BuyInPayment.PAID)
        add("Дима", 1_000)
        add("Егор", 1_000, BuyInPayment.PAID)
    }

/** Приём снимка как новой игры (локальной копии нет). */
fun receive(incoming: GameSnapshot, change: app.feltbook.core.domain.model.GameChange): GameSnapshot =
    GameSnapshot(change.game!!, change.players, change.entries, change.handovers).also {
        assertEquals(incoming.entries, it.entries)
    }

class HandoverExample84Test {
    @Test
    fun `акт передачи посреди игры — касса 2 300, рейк 300, долги 1 200, доход Андрея 200`() {
        val table = example84Start()
        assertEquals(10, table.snapshot.entries.size)
        val act = Handovers.act(table.snapshot)
        assertEquals(
            HandoverAct(
                fromBanker = "Андрей",
                cashOnHand = 2_300,
                rakeDue = 300,
                playersOwe = 1_200,
                bankerOwes = 0,
                fromBankerIncome = 200,
                incomeTakeable = 200,
                playersAtTable = 3,
                chipsInPlay = 3_000,
                entryCount = 10,
            ),
            act,
        )
        assertEquals(2_100L, act.cashToHandOver(keepIncome = true))
        assertEquals(2_300L, act.cashToHandOver(keepIncome = false))
    }

    @Test
    fun `оставить доход себе — INCOME_TAKEN 200, у Влада касса 2 100 и ожидаемо 300`() {
        val table = example84Start()
        val prepared = Handovers.prepare(table.snapshot, " Влад ", keepIncome = true, stamp = table.clock.stamp())
        val taken = prepared.senderChange.entries.single()
        assertEquals(EntryType.INCOME_TAKEN, taken.type)
        assertEquals(200L, taken.amount)
        assertEquals(11L, taken.seq)
        assertEquals("Андрей", taken.bankerName)
        assertNull(taken.playerId)
        val h = prepared.handover
        assertEquals("Андрей", h.fromBanker)
        assertEquals("Влад", h.toBanker)
        assertEquals(2_100L, h.cashHandedOver)
        assertEquals(200L, h.incomeTaken)
        assertEquals(11L, h.ledgerSeq)
        assertEquals(listOf(h), prepared.senderChange.handovers)
        assertEquals(GameStatus.HANDED_OVER, prepared.senderChange.game!!.status)
        assertEquals(GameStatus.RUNNING, prepared.outgoing.game.status)
        assertEquals(listOf(h), prepared.outgoing.handovers)

        val accepted = Handovers.accept(prepared.outgoing, h, local = null, activeGame = null, receiverPlayerId = null)
        val vlad = receive(prepared.outgoing, accepted)
        assertEquals("Влад", vlad.game.currentBankerName)
        assertNull(vlad.game.bankerPlayerId)
        val t = GameStates.compute(vlad).table
        assertEquals(2_100L, t.cashOnHand)
        assertEquals(300L, t.expectedCash)
        assertCashIdentity(t)

        // Дальше записи — с bankerName = Влад; доход раздельно.
        val withVlad = tableFrom(vlad)
        withVlad.add("Гоша", 1_000, BuyInPayment.PAID)
        val income = withVlad.state.table.incomeByBanker
        assertEquals(listOf(BankerIncome("Андрей", 200, 0, 0, 200), BankerIncome("Влад", 100, 0, 0, 0)), income)
        assertTrue(withVlad.snapshot.entries.takeLast(4).all { it.bankerName == "Влад" })
        assertCashIdentity(withVlad.state.table)
        assertEquals(0L, Handovers.act(withVlad.snapshot).let { it.fromBankerIncome - 100 })

        // Отправитель коммитит те же записи после ACK.
        val sender = table.snapshot.apply(prepared.senderChange)
        assertEquals(GameStatus.HANDED_OVER, sender.game.status)
        assertEquals(prepared.outgoing.entries, sender.entries)
    }

    @Test
    fun `бывший банкир в режиме с каждого закупа платит сбор`() {
        val table = example84Start(GameRules(bankerFeeMode = BankerFeeMode.EACH_FIXED))
        val prepared = Handovers.prepare(table.snapshot, "Влад", keepIncome = false, stamp = table.clock.stamp())
        assertTrue(prepared.senderChange.entries.isEmpty())
        assertEquals(10L, prepared.handover.ledgerSeq)
        assertEquals(0L, prepared.handover.incomeTaken)
        val vlad = receive(prepared.outgoing, Handovers.accept(prepared.outgoing, prepared.handover, null, null, null))
        val quote = Pricing.quoteBuyIn(vlad, table.id("Андрей"), 1_000)
        assertEquals(100L, quote.bankerFee)
    }

    @Test
    fun `новый банкир играет — receiverPlayerId становится банкиром`() {
        val table = example84Start()
        val dima = table.id("Дима")
        val prepared = Handovers.prepare(table.snapshot, "Дима", keepIncome = false, stamp = table.clock.stamp())
        val change = Handovers.accept(prepared.outgoing, prepared.handover, null, null, receiverPlayerId = dima)
        assertEquals(dima, change.game!!.bankerPlayerId)
        assertViolation(DomainError.BankerNotAPlayer(playerId(99))) {
            Handovers.accept(prepared.outgoing, prepared.handover, null, null, receiverPlayerId = playerId(99))
        }
    }

    @Test
    fun `возврат игры первому банкиру — локальная переданная копия префикс, Extend`() {
        val andrey = example84Start()
        val first = Handovers.prepare(andrey.snapshot, "Влад", keepIncome = true, stamp = andrey.clock.stamp())
        andrey.apply(first.senderChange)
        val local = andrey.snapshot
        val vlad =
            tableFrom(receive(first.outgoing, Handovers.accept(first.outgoing, first.handover, null, null, null)))
        vlad.buy("Дима", 500)
        val back = Handovers.prepare(vlad.snapshot, "Андрей", keepIncome = false, stamp = vlad.clock.stamp())
        val change = Handovers.accept(
            back.outgoing,
            back.handover,
            local,
            activeGame = null,
            receiverPlayerId = local.players.first().id,
        )
        assertEquals(back.outgoing.entries.drop(local.entries.size), change.entries)
        assertEquals(listOf(back.handover), change.handovers)
        val restored = local.apply(change)
        assertEquals("Андрей", restored.game.currentBankerName)
        assertEquals(GameStatus.RUNNING, restored.game.status)
        assertEquals(back.outgoing.entries, restored.entries)
        assertEquals(2, restored.handovers.size)
        // Повторный приём того же пакета — ничего нового.
        val again = Handovers.accept(back.outgoing, back.handover, restored, restored.game, local.players.first().id)
        assertEquals(app.feltbook.core.domain.model.GameChange(restored.game.id), again)
    }

    @Test
    fun `приём при другой активной игре или расходящейся копии — ошибка`() {
        val table = example84Start()
        val prepared = Handovers.prepare(table.snapshot, "Влад", keepIncome = false, stamp = table.clock.stamp())
        val other = emptyGame(id = gameId(77))
        assertViolation(DomainError.AnotherGameActive(other.id)) {
            Handovers.accept(prepared.outgoing, prepared.handover, null, other, null)
        }
        val diverged = table.snapshot.copy(
            entries =
                table.snapshot.entries.dropLast(1) + table.snapshot.entries.last().copy(amount = 1),
        )
        assertViolation(DomainError.ImportConflict(ImportConflictReason.DIVERGED_LEDGER)) {
            Handovers.accept(prepared.outgoing, prepared.handover, diverged, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            Handovers.accept(table.snapshot, prepared.handover, null, null, null)
        }
    }

    @Test
    fun `доход не забрать, если наличных меньше рейка, а отрицательная касса не передаётся`() {
        val table = TestTable().apply { add("Андрей", 1_000) }
        val act = Handovers.act(table.snapshot)
        assertEquals(0L, act.cashOnHand)
        assertEquals(100L, act.fromBankerIncome)
        assertEquals(0L, act.incomeTakeable)
        table.apply(Commands.payRake(table.snapshot, table.clock.stamp()))
        val negative = Handovers.act(table.snapshot)
        assertEquals(-100L, negative.cashOnHand)
        assertEquals(0L, negative.cashToHandOver(keepIncome = true))
        assertViolation(DomainError.NameBlank) { Handovers.prepare(table.snapshot, " ", false, table.clock.stamp()) }
        val closed = table.snapshot.copy(game = table.snapshot.game.copy(status = GameStatus.CLOSED))
        assertFailsWith<DomainRuleViolation> { Handovers.act(closed) }
    }

    @Test
    fun `пустая игра — ledgerSeq 0, доход 0`() {
        val table = TestTable(banker = "Олег")
        val prepared = Handovers.prepare(table.snapshot, "Влад", keepIncome = true, stamp = table.clock.stamp())
        assertEquals(0L, prepared.handover.ledgerSeq)
        val withoutBanker = table.snapshot.copy(game = table.snapshot.game.copy(currentBankerName = "Никто"))
        assertEquals(0L, Handovers.act(withoutBanker).fromBankerIncome)
    }

    @Test
    fun `шаблон после расчёта — правила и имена, игра не меняется`() {
        val table = example81()
        table.apply(Commands.finish(table.snapshot))
        assertFailsWith<DomainRuleViolation> { Handovers.template(table.snapshot) }
        table.apply(Commands.close(table.snapshot, true, emptySet(), table.clock.stamp()))
        val template = Handovers.template(table.snapshot)
        assertEquals(GameTemplate("Олег", GameRules.DEFAULT, listOf("Андрей", "Борис", "Влад")), template)
    }
}

class ImportsTest {
    private val table = example81()
    private val incoming = table.snapshot

    @Test
    fun `нет такой игры — Insert целиком`() {
        val plan = assertIs<ImportPlan.Insert>(Imports.plan(incoming, null, null))
        assertEquals(incoming, receive(incoming, plan.change))
        val created = incoming.copy(game = incoming.game.copy(status = GameStatus.CREATED))
        val asRunning = assertIs<ImportPlan.Insert>(Imports.plan(created, null, null))
        assertEquals(GameStatus.RUNNING, asRunning.change.game!!.status)
    }

    @Test
    fun `та же игра — Identical`() {
        assertEquals(ImportPlan.Identical, Imports.plan(incoming, incoming, incoming.game.id))
    }

    @Test
    fun `локальный журнал — префикс — Extend хвостом`() {
        val local = incoming.copy(entries = incoming.entries.take(12), players = incoming.players.take(2))
        val plan = assertIs<ImportPlan.Extend>(Imports.plan(incoming, local, null))
        assertEquals(incoming.entries.drop(12), plan.change.entries)
        assertEquals(incoming.players.drop(2), plan.change.players)
        assertNull(plan.change.game)
        assertEquals(incoming, local.apply(plan.change))
    }

    @Test
    fun `отправитель открыл свой же файл — Extend без записей, игра снова активна`() {
        val start = example84Start()
        val prepared = Handovers.prepare(start.snapshot, "Влад", keepIncome = true, stamp = start.clock.stamp())
        val local = start.snapshot.apply(prepared.senderChange)
        val plan = assertIs<ImportPlan.Extend>(Imports.plan(prepared.outgoing, local, null))
        assertTrue(plan.change.entries.isEmpty())
        assertEquals(GameStatus.RUNNING, plan.change.game!!.status)
        assertEquals("Андрей", plan.change.game!!.currentBankerName)
    }

    @Test
    fun `журналы разошлись, вторая активная игра, битый журнал — Conflict`() {
        val diverged = incoming.copy(entries = incoming.entries.take(5) + incoming.entries[6])
        assertEquals(
            ImportPlan.Conflict(ImportConflictReason.DIVERGED_LEDGER),
            Imports.plan(
                incoming,
                diverged.copy(
                    entries = diverged.entries.mapIndexed { i, e ->
                        e.copy(seq = i + 1L)
                    },
                ),
                null,
            ),
        )
        val longerLocal = incoming.copy(handovers = listOf(prepareHandover()))
        assertEquals(
            ImportPlan.Conflict(ImportConflictReason.LOCAL_NEWER),
            Imports.plan(incoming, longerLocal, null),
        )
        assertEquals(
            ImportPlan.Conflict(ImportConflictReason.SECOND_ACTIVE_GAME),
            Imports.plan(incoming, null, gameId(42)),
        )
        val closed = incoming.copy(game = incoming.game.copy(status = GameStatus.CLOSED))
        assertIs<ImportPlan.Insert>(Imports.plan(closed, null, gameId(42)))
        val broken = incoming.copy(entries = incoming.entries.drop(1))
        assertEquals(ImportPlan.Conflict(ImportConflictReason.INVALID_LEDGER), Imports.plan(broken, null, null))
        assertFailsWith<IllegalArgumentException> { Imports.plan(incoming, emptyGameSnapshot(), null) }
    }

    private fun prepareHandover() = app.feltbook.core.domain.model.Handover(
        app.feltbook.core.domain.model.HandoverId(kotlin.uuid.Uuid.fromLongs(8, 8)),
        incoming.game.id,
        "Олег",
        "Влад",
        0,
        0,
        19,
        table.clock.now,
    )

    private fun emptyGameSnapshot() = app.feltbook.core.domain.emptySnapshot(emptyGame(id = gameId(500)))
}
