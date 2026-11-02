package app.feltbook.core.domain.engine

import app.feltbook.core.domain.T0
import app.feltbook.core.domain.TestClock
import app.feltbook.core.domain.TestTable
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.Handover
import app.feltbook.core.domain.model.HandoverId
import app.feltbook.core.domain.model.PlayerName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class PlayerNamesTest {
    private val directory = listOf(
        PlayerName("Влад", T0),
        PlayerName("Владик", T0 + 1.days, mergedInto = "Влад"),
        PlayerName("Вл", T0 + 2.days, mergedInto = "Владик"),
        PlayerName("Виктор", T0 + 3.days),
        PlayerName("Вика", T0 + 4.days),
        PlayerName("Борис", T0 + 5.days),
    )

    @Test
    fun `нормализация — trim и схлопывание пробелов, регистр сохраняется`() {
        assertEquals("Андрей Большой", PlayerNames.normalize("  Андрей \t  Большой "))
        assertEquals("", PlayerNames.normalize("   "))
    }

    @Test
    fun `каноническое имя — по цепочке объединений, при цикле исходное`() {
        assertEquals("Влад", PlayerNames.canonical("Вл", directory))
        assertEquals("Влад", PlayerNames.canonical(" Владик ", directory))
        assertEquals("Пётр", PlayerNames.canonical("Пётр", directory))
        val cycle = listOf(PlayerName("А", T0, "Б"), PlayerName("Б", T0, "А"))
        assertEquals("А", PlayerNames.canonical("А", cycle))
    }

    @Test
    fun `объединить можно разные существующие имена без цикла`() {
        assertTrue(PlayerNames.canMerge("Вика", "Виктор", directory))
        assertFalse(PlayerNames.canMerge("Вика", "Вика", directory))
        assertFalse(PlayerNames.canMerge("Вика", "Пётр", directory))
        assertFalse(PlayerNames.canMerge("Пётр", "Вика", directory))
        assertFalse(PlayerNames.canMerge("Влад", "Вл", directory), "Вл → Владик → Влад — цикл")
        assertTrue(PlayerNames.canMerge("Борис", "Вл", directory))
        val cycle = listOf(PlayerName("А", T0, "Б"), PlayerName("Б", T0, "А"), PlayerName("В", T0))
        assertFalse(PlayerNames.canMerge("В", "А", cycle), "цепочка цели уже зациклена")
    }

    @Test
    fun `подсказки — по префиксу без учёта регистра, недавние сверху, без сидящих и объединённых`() {
        assertEquals(listOf("Вика", "Виктор", "Влад"), PlayerNames.suggestions("в", directory, emptySet()))
        assertEquals(listOf("Виктор", "Влад"), PlayerNames.suggestions(" В ", directory, setOf("Вика")))
        assertEquals(listOf("Вика"), PlayerNames.suggestions("ви", directory, emptySet(), limit = 1))
        assertEquals(6 - 2, PlayerNames.suggestions("", directory, emptySet()).size)
    }
}

/** Закрытая игра 8.2 (Влад ушёл с долгом 1 053), начатая в `start`. */
fun closedVladDebtGame(clock: TestClock = TestClock()): TestTable {
    val table = example81(TestTable(clock = clock), vladPays = false)
    table.apply(Commands.finish(table.snapshot))
    table.apply(Commands.close(table.snapshot, true, setOf(CloseConfirmation.OPEN_DEBTS), table.clock.stamp()))
    return table
}

class DebtsTest {
    @Test
    fun `открытый долг Влада — с даты выхода, в новой игре предупреждение`() {
        val old = closedVladDebtGame()
        val clock = TestClock(T0 + 3.days)
        val fresh = TestTable(clock = clock).apply { add("Андрей", 1_000) }
        val games = listOf(old.snapshot, fresh.snapshot)
        val debts = Debts.open(games, emptyList())
        val debt = debts.single()
        assertEquals("Влад", debt.canonicalName)
        assertEquals("Влад", debt.playerName)
        assertEquals(old.id("Влад"), debt.playerId)
        assertEquals(old.snapshot.game.id, debt.gameId)
        assertEquals("Пятница", debt.gameTitle)
        assertEquals(T0, debt.gameCreatedAt)
        assertEquals(1_053L, debt.amount)
        assertEquals(1_053L, debt.owedAtExit)
        assertEquals(T0 + 61.minutesInHours(), debt.since)
        assertEquals(listOf(debt), Debts.forName("Влад", games, emptyList(), excludeGameId = fresh.snapshot.game.id))
        assertTrue(Debts.forName("Влад", games, emptyList(), excludeGameId = old.snapshot.game.id).isEmpty())
        assertTrue(Debts.forName("Борис", games, emptyList(), null).isEmpty())
    }

    @Test
    fun `долг по объединённому имени, частичная оплата — отдал 600 из 1 200`() {
        val table = TestTable().apply {
            add("Владик", 1_000)
            later()
            cashOut("Владик", 0, settlement = SettlementChoice.Partial(600))
        }
        val directory = listOf(PlayerName("Влад", T0), PlayerName("Владик", T0, mergedInto = "Влад"))
        val debt = Debts.forName("Влад", listOf(table.snapshot), directory, null).single()
        assertEquals("Влад", debt.canonicalName)
        assertEquals("Владик", debt.playerName)
        assertEquals(600L, debt.amount)
        assertEquals(1_200L, debt.owedAtExit)
    }

    @Test
    fun `долги переданной игры не открыты здесь, сортировка — старые сверху`() {
        val first = closedVladDebtGame(TestClock(T0 + 1.days))
        val second = closedVladDebtGame(TestClock(T0))
        val handed = closedVladDebtGame(TestClock(T0 - 1.days)).snapshot.let {
            it.copy(game = it.game.copy(status = GameStatus.HANDED_OVER))
        }
        val debts = Debts.open(listOf(first.snapshot, second.snapshot, handed), emptyList())
        assertEquals(listOf(second.snapshot.game.id, first.snapshot.game.id), debts.map { it.gameId })
    }

    private fun Int.minutesInHours() = kotlin.time.Duration.parse("${this}m")
}

class StatsTest {
    @Test
    fun `строка истории закрытой игры 8_1`() {
        val table = example81()
        table.apply(Commands.finish(table.snapshot))
        table.apply(Commands.close(table.snapshot, true, emptySet(), table.clock.stamp()))
        val item = Stats.historyItem(table.snapshot)
        assertEquals(table.snapshot.game.id, item.gameId)
        assertEquals("Пятница", item.title)
        assertEquals(GameStatus.CLOSED, item.status)
        assertEquals(T0, item.startedAt)
        assertEquals(table.snapshot.game.closedAt, item.endedAt)
        assertEquals(3, item.playerCount)
        assertEquals(1, item.rebuyCount)
        assertEquals(5_000L, item.turnover)
        assertEquals(500L, item.rake)
        assertEquals(353L, item.bankerIncome)
        assertFalse(item.hasOpenDebt)
        assertFalse(item.editedAfterClose)
    }

    @Test
    fun `история активной и переданной игры`() {
        val table = TestTable().apply { add("Андрей", 1_000) }
        assertNull(Stats.historyItem(table.snapshot).endedAt)
        val at = T0 + 2.hours
        val handover = Handover(HandoverId(Uuid.fromLongs(5, 5)), table.snapshot.game.id, "Олег", "Влад", 0, 0, 3, at)
        table.apply(
            GameChange(
                table.snapshot.game.id,
                baseSeq = table.snapshot.lastSeq,
                game = table.snapshot.game.copy(status = GameStatus.HANDED_OVER),
                handovers = listOf(handover),
            ),
        )
        assertEquals(at, Stats.historyItem(table.snapshot).endedAt)
        val noAct = table.snapshot.copy(handovers = emptyList())
        assertNull(Stats.historyItem(noAct).endedAt)
    }

    @Test
    fun `статистика периода — только завершённые игры и вышедшие игроки`() {
        val g1 = closedVladDebtGame(TestClock(T0))
        val g2 = closedVladDebtGame(TestClock(T0 + 7.days))
        val active = TestTable(clock = TestClock(T0 + 8.days)).apply { add("Влад", 5_000) }
        val handed = TestTable(clock = TestClock(T0 + 9.days)).apply {
            add("Влад", 1_000, BuyInPayment.PAID)
            add("Гоша", 1_000, BuyInPayment.PAID)
            later()
            cashOut("Влад", 1_500)
        }.snapshot.let { it.copy(game = it.game.copy(status = GameStatus.HANDED_OVER)) }
        val games = listOf(g2.snapshot, active.snapshot, handed, g1.snapshot)
        val all = Stats.period(games, emptyList(), null, null)
        assertEquals(3, all.gameCount)
        assertEquals(12_000L, all.turnover)
        assertEquals(1_200L, all.rake)
        assertEquals(353L * 2 + 200, all.bankerIncome)
        assertEquals(listOf(T0, T0 + 7.days, T0 + 9.days), all.incomeByGame.map { it.startedAt })
        assertEquals(listOf("Борис", "Влад", "Андрей"), all.players.map { it.canonicalName })
        val vlad = all.players.single { it.canonicalName == "Влад" }
        assertEquals(3, vlad.gamesPlayed)
        assertEquals(-753L * 2 + 500, vlad.totalResult)
        assertEquals(5_000L, vlad.totalChipsBought)
        assertEquals(3, vlad.buyInCount)
        assertEquals(0, vlad.rebuyCount)
        assertEquals(2_106L, vlad.openDebt)
        assertEquals(T0 + 9.days, vlad.lastPlayedAt)
        assertEquals(1_667L, vlad.averageBuyInPerGame())
        assertTrue(all.players.none { it.canonicalName == "Гоша" }, "за столом при передаче — не в статистике")

        val week = Stats.period(games, emptyList(), T0 + 1.days, T0 + 9.days)
        assertEquals(1, week.gameCount)
        assertEquals(g2.snapshot.game.id, week.incomeByGame.single().gameId)
        val andrey = Stats.player("Андрей", games, emptyList())!!
        assertEquals(2, andrey.gamesPlayed)
        assertEquals(2, andrey.rebuyCount)
        assertNull(Stats.player("Гоша", games, emptyList()))
        assertEquals(0L, PlayerStats("Х", 0, 0, 0, 0, 0, 0, null).averageBuyInPerGame())
    }

    @Test
    fun `статистика по объединённым именам`() {
        val g1 = TestTable(clock = TestClock(T0)).apply {
            add("Влад", 1_000, BuyInPayment.PAID)
            later()
            cashOut("Влад", 1_500)
        }
        val g2 = TestTable(clock = TestClock(T0 + 1.days)).apply {
            add("Владик", 2_000, BuyInPayment.PAID)
            later()
            cashOut("Владик", 1_000)
        }
        val closed = listOf(g1, g2).map { t ->
            t.apply(Commands.finish(t.snapshot))
            t.apply(Commands.close(t.snapshot, true, CloseConfirmation.entries.toSet(), t.clock.stamp()))
            t.snapshot
        }
        val directory = listOf(PlayerName("Влад", T0), PlayerName("Владик", T0, mergedInto = "Влад"))
        val stats = Stats.player("Владик", closed, directory)!!
        assertEquals("Влад", stats.canonicalName)
        assertEquals(2, stats.gamesPlayed)
        assertEquals(500L - 1_000L, stats.totalResult)
        assertEquals(1_500L, stats.averageBuyInPerGame())
        val single: List<GameSnapshot> = closed.take(1)
        assertEquals(1, Stats.period(single, directory, null, null).players.single().gamesPlayed)
    }
}
