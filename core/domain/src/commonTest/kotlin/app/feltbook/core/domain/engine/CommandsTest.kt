package app.feltbook.core.domain.engine

import app.feltbook.core.domain.T0
import app.feltbook.core.domain.TestClock
import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.emptyGame
import app.feltbook.core.domain.emptySnapshot
import app.feltbook.core.domain.entry
import app.feltbook.core.domain.entryId
import app.feltbook.core.domain.gameId
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.Player
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.RulesProblem
import app.feltbook.core.domain.model.apply
import app.feltbook.core.domain.playerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

fun assertViolation(expected: DomainError, block: () -> Unit) {
    assertEquals(expected, assertFailsWith<DomainRuleViolation> { block() }.error)
}

class StartGameTest {
    private val clock = TestClock()
    private fun input(
        title: String = "",
        banker: String = " Олег ",
        plays: Boolean = false,
        rules: GameRules = GameRules.DEFAULT,
    ) = NewGameInput(title, "18 окт, 19:00", banker, plays, rules)

    @Test
    fun `старт игры — RUNNING, слепок правил, название по умолчанию`() {
        val rules = GameRules(rakeValue = 5)
        val change = Commands.startGame(input(rules = rules), null, clock.stamp())
        val game = change.game!!
        assertEquals(change.gameId, game.id)
        assertEquals(GameStatus.RUNNING, game.status)
        assertEquals("18 окт, 19:00", game.title)
        assertEquals("Олег", game.currentBankerName)
        assertEquals(rules, game.rules)
        assertEquals(T0, game.createdAt)
        assertNull(game.closedAt)
        assertNull(game.bankerPlayerId)
        assertTrue(change.players.isEmpty())
        assertTrue(change.entries.isEmpty())
    }

    @Test
    fun `банкир играет — игрок-банкир без закупа`() {
        val change = Commands.startGame(input(title = "  Пятница ", plays = true), null, clock.stamp())
        val banker = change.players.single()
        assertEquals("Олег", banker.name)
        assertEquals(banker.id, change.game!!.bankerPlayerId)
        assertEquals("Пятница", change.game!!.title)
        assertTrue(change.entries.isEmpty())
    }

    @Test
    fun `вторая активная игра, плохие правила, пустое имя, длинное название — блокеры`() {
        val active = emptyGame()
        assertViolation(DomainError.AnotherGameActive(active.id)) { Commands.startGame(input(), active, clock.stamp()) }
        assertViolation(DomainError.InvalidRules(listOf(RulesProblem.RAKE_NEGATIVE))) {
            Commands.startGame(input(rules = GameRules(rakeValue = -1)), null, clock.stamp())
        }
        assertViolation(DomainError.NameBlank) { Commands.startGame(input(banker = "  "), null, clock.stamp()) }
        assertViolation(DomainError.TitleTooLong) {
            Commands.startGame(input(title = "x".repeat(61)), null, clock.stamp())
        }
    }
}

class PurchaseCommandsTest {
    @Test
    fun `закуп 1000 в долг — к оплате 1200, три записи одной операцией`() {
        val table = TestTable()
        val change = Commands.addPlayer(table.snapshot, " Андрей ", 1_000, BuyInPayment.DEBT, table.clock.stamp())
        val player = change.players.single()
        assertEquals("Андрей", player.name)
        assertEquals(listOf(EntryType.BUY_IN, EntryType.RAKE, EntryType.BANKER_FEE), change.entries.map { it.type })
        assertEquals(listOf(1_000L, 100L, 100L), change.entries.map { it.amount })
        assertEquals(listOf(1L, 2L, 3L), change.entries.map { it.seq })
        assertEquals(1, change.entries.map { it.operationId }.distinct().size)
        assertTrue(change.entries.all { it.playerId == player.id && it.bankerName == "Олег" && it.createdAt == T0 })
        table.apply(change)
        assertEquals(1_200L, table.state.player(player.id)!!.debt)
    }

    @Test
    fun `закуп оплачено — PAYMENT на всю сумму`() {
        val table = TestTable()
        val change = Commands.addPlayer(table.snapshot, "Борис", 1_000, BuyInPayment.PAID, table.clock.stamp())
        assertEquals(EntryType.PAYMENT, change.entries.last().type)
        assertEquals(1_200L, change.entries.last().amount)
        table.apply(change)
        assertEquals(0L, table.state.player(table.id("Борис"))!!.outstanding)
        assertEquals(1_200L, table.state.table.cashOnHand)
    }

    @Test
    fun `раздельная оплата — комиссии сразу, фишки в долг, PAYMENT 200`() {
        val table = TestTable(rules = GameRules(splitPayment = true))
        val change = Commands.addPlayer(
            table.snapshot,
            "Андрей",
            1_000,
            BuyInPayment(chipsPaid = false, feesPaid = true),
            table.clock.stamp(),
        )
        assertEquals(200L, change.entries.single { it.type == EntryType.PAYMENT }.amount)
        table.apply(change)
        assertEquals(1_000L, table.state.player(table.id("Андрей"))!!.debt)
    }

    @Test
    fun `раздельная оплата при выключенной опции — блокер`() {
        val table = TestTable()
        assertViolation(DomainError.SplitPaymentDisabled) {
            Commands.addPlayer(
                table.snapshot,
                "Андрей",
                1_000,
                BuyInPayment(chipsPaid = true, feesPaid = false),
                table.clock.stamp(),
            )
        }
        table.add("Андрей", 1_000)
        assertViolation(DomainError.SplitPaymentDisabled) {
            table.buy("Андрей", 1_000, BuyInPayment(chipsPaid = false, feesPaid = true))
        }
    }

    @Test
    fun `нулевой рейк и сбор не пишутся`() {
        val table = TestTable(rules = GameRules(rakeValue = 0, bankerFeeValue = 0))
        val change = Commands.addPlayer(table.snapshot, "Андрей", 1_000, BuyInPayment.DEBT, table.clock.stamp())
        assertEquals(listOf(EntryType.BUY_IN), change.entries.map { it.type })
    }

    @Test
    fun `докуп — рейк без разового сбора, счётчик докупов`() {
        val table = TestTable()
        table.add("Андрей", 1_000)
        val change = table.buy("Андрей", 1_000)
        assertEquals(listOf(EntryType.BUY_IN, EntryType.RAKE), change.entries.map { it.type })
        assertEquals(listOf(4L, 5L), change.entries.map { it.seq })
        val state = table.state.player(table.id("Андрей"))!!
        assertEquals(1, state.rebuyCount)
        assertEquals(2_300L, state.debt)
        assertEquals(1, table.state.table.rebuyCount)
    }

    @Test
    fun `возврат вышедшего — докуп, снова за столом, счётчик докупов растёт`() {
        val table = TestTable(rules = GameRules(bankerFeeMode = BankerFeeMode.EACH_FIXED))
        val id = table.add("Андрей", 1_000, BuyInPayment.PAID)
        table.apply(
            changeOf(table.snapshot, entry(table.snapshot.nextSeq, EntryType.CASH_OUT, id, 1_000, op = 100)),
        )
        assertEquals(PlayerStatus.CASHED_OUT, table.state.player(id)!!.status)
        val change = table.buy("Андрей", 500)
        assertEquals(listOf(EntryType.BUY_IN, EntryType.RAKE, EntryType.BANKER_FEE), change.entries.map { it.type })
        val state = table.state.player(id)!!
        assertEquals(PlayerStatus.PLAYING, state.status)
        assertEquals(1, state.rebuyCount)
        assertEquals(500L, state.session!!.chipsBought)
    }

    @Test
    fun `докуп и закуп — блокеры котировки`() {
        val table = TestTable()
        assertViolation(DomainError.BelowMinBuyIn(500)) { table.add("Андрей", 499) }
        assertViolation(DomainError.PlayerNotFound(playerId(99))) {
            Commands.buyIn(table.snapshot, playerId(99), 1_000, BuyInPayment.DEBT, table.clock.stamp())
        }
        table.add("Андрей", 500)
        assertViolation(DomainError.NameTaken(table.id("Андрей"))) { table.add("Андрей", 1_000) }
        table.apply(Commands.finish(table.snapshot))
        assertViolation(DomainError.WrongGameStatus(GameStatus.SETTLING, setOf(GameStatus.RUNNING))) {
            table.buy("Андрей", 1_000)
        }
    }

    @Test
    fun `void-игрок возвращается по имени без нового Player`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000)
        val buy = table.snapshot.entries
        val reversals = buy.mapIndexed { i, e ->
            entry(table.snapshot.nextSeq + i, EntryType.REVERSAL, id, e.amount, op = 200, reverses = e.id)
        }
        table.apply(app.feltbook.core.domain.model.GameChange(table.snapshot.game.id, entries = reversals))
        assertTrue(table.state.player(id)!!.isVoid)
        val change = Commands.addPlayer(table.snapshot, "Андрей", 2_000, BuyInPayment.DEBT, table.clock.stamp())
        assertTrue(change.players.isEmpty())
        assertTrue(change.entries.all { it.playerId == id })
        table.apply(change)
        val state = table.state.player(id)!!
        assertEquals(2_300L, state.debt)
        assertEquals(0, state.rebuyCount)
        assertEquals(1, table.snapshot.players.size)
    }

    @Test
    fun `банкир-игрок закупается без сбора, рейк платит`() {
        val table = TestTable(banker = "Андрей", bankerPlays = true)
        val change = table.buy("Андрей", 1_000, BuyInPayment.PAID)
        assertEquals(listOf(EntryType.BUY_IN, EntryType.RAKE, EntryType.PAYMENT), change.entries.map { it.type })
        assertEquals(1_100L, change.entries.last().amount)
    }

    private fun changeOf(snapshot: GameSnapshot, vararg entries: app.feltbook.core.domain.model.LedgerEntry) =
        app.feltbook.core.domain.model.GameChange(
            snapshot.game.id,
            entries = entries.map {
                it.copy(gameId = snapshot.game.id)
            },
        )
}

class PaymentCommandsTest {
    @Test
    fun `частичная оплата долга, затем полная`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000)
        val change = table.pay("Андрей", 500)
        assertEquals(EntryType.PAYMENT, change.entries.single().type)
        assertEquals(700L, table.state.player(id)!!.debt)
        table.pay("Андрей", 700)
        assertEquals(0L, table.state.player(id)!!.debt)
        assertEquals(1_200L, table.state.table.cashOnHand)
    }

    @Test
    fun `оплата больше долга, ноль, не тот игрок — ошибки`() {
        val table = TestTable()
        table.add("Андрей", 1_000)
        assertViolation(DomainError.PaymentExceedsDebt(1_200)) { table.pay("Андрей", 1_201) }
        assertViolation(DomainError.AmountNotPositive) { table.pay("Андрей", 0) }
        assertViolation(DomainError.AmountTooLarge) { table.pay("Андрей", Money.MAX_AMOUNT + 1) }
        assertViolation(DomainError.PlayerNotFound(playerId(99))) {
            Commands.pay(table.snapshot, playerId(99), 100, table.clock.stamp())
        }
    }

    @Test
    fun `погашение с другим банкиром пишет его имя`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000)
        val change = Commands.pay(table.snapshot, id, 100, table.clock.stamp(), bankerName = "Влад")
        assertEquals("Влад", change.entries.single().bankerName)
    }

    @Test
    fun `выплата — не больше долга банкира`() {
        val p = playerId(1)
        val snapshot = GameSnapshot(
            emptyGame(),
            listOf(Player(p, gameId(1), "П", T0)),
            listOf(entry(1, EntryType.BUY_IN, p, 1_000), entry(2, EntryType.CASH_OUT, p, 1_500, op = 2)),
            emptyList(),
        )
        assertViolation(DomainError.PayoutExceedsOwed(500)) { Commands.payOut(snapshot, p, 501, TestClock().stamp()) }
        val change = Commands.payOut(snapshot, p, 500, TestClock().stamp())
        assertEquals(EntryType.PAYOUT, change.entries.single().type)
        assertEquals(3L, change.entries.single().seq)
        assertEquals(0L, GameStates.compute(snapshot.apply(change)).player(p)!!.owedByBanker)
        assertViolation(DomainError.PayoutExceedsOwed(0)) {
            Commands.payOut(snapshot.apply(change), p, 1, TestClock().stamp(), bankerName = "Олег")
        }
    }

    @Test
    fun `оплата в закрытой игре можно, в переданной — нет`() {
        val p = playerId(1)
        val entries = listOf(entry(1, EntryType.BUY_IN, p, 1_000), entry(2, EntryType.CASH_OUT, p, 0, op = 2))
        val players = listOf(Player(p, gameId(1), "П", T0))
        val closed = GameSnapshot(emptyGame(status = GameStatus.CLOSED), players, entries, emptyList())
        val change = Commands.pay(closed, p, 1_000, TestClock().stamp())
        assertEquals(closed.game, closed.apply(change).game, "editedAfterClose не меняется")
        val handed = closed.copy(game = closed.game.copy(status = GameStatus.HANDED_OVER))
        assertViolation(
            DomainError.WrongGameStatus(
                GameStatus.HANDED_OVER,
                setOf(GameStatus.RUNNING, GameStatus.SETTLING, GameStatus.CLOSED),
            ),
        ) { Commands.pay(handed, p, 1_000, TestClock().stamp()) }
    }
}

class GameFlowCommandsTest {
    @Test
    fun `завершить и вернуть игру`() {
        val snapshot = emptySnapshot()
        val settling = snapshot.apply(Commands.finish(snapshot))
        assertEquals(GameStatus.SETTLING, settling.game.status)
        assertEquals(GameStatus.RUNNING, settling.apply(Commands.resume(settling)).game.status)
        assertViolation(DomainError.WrongGameStatus(GameStatus.SETTLING, setOf(GameStatus.RUNNING))) {
            Commands.finish(settling)
        }
        assertViolation(DomainError.WrongGameStatus(GameStatus.RUNNING, setOf(GameStatus.SETTLING))) {
            Commands.resume(snapshot)
        }
    }

    @Test
    fun `переименование — upsert игрока, журнал не меняется`() {
        val table = TestTable()
        val id = table.add("Андрей", 1_000)
        table.add("Борис", 1_000)
        val change = Commands.renamePlayer(table.snapshot, id, "  Андрей   Большой ")
        assertEquals("Андрей Большой", change.players.single().name)
        assertTrue(change.entries.isEmpty())
        assertViolation(DomainError.NameTaken(table.id("Борис"))) { Commands.renamePlayer(table.snapshot, id, "Борис") }
        assertViolation(DomainError.NameBlank) { Commands.renamePlayer(table.snapshot, id, " ") }
        assertViolation(DomainError.PlayerNotFound(playerId(99))) {
            Commands.renamePlayer(table.snapshot, playerId(99), "Х")
        }
        assertEquals("Андрей", Commands.renamePlayer(table.snapshot, id, "Андрей").players.single().name)
        val handed = table.snapshot.copy(game = table.snapshot.game.copy(status = GameStatus.HANDED_OVER))
        assertFailsWith<DomainRuleViolation> { Commands.renamePlayer(handed, id, "Андрюха") }
    }
}
