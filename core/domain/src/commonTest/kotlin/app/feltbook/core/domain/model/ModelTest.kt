package app.feltbook.core.domain.model

import app.feltbook.core.domain.T0
import app.feltbook.core.domain.emptyGame
import app.feltbook.core.domain.emptySnapshot
import app.feltbook.core.domain.entry
import app.feltbook.core.domain.gameId
import app.feltbook.core.domain.playerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class GameRulesTest {
    @Test
    fun `правила по умолчанию — разово 100, рейк 10 процентов, минимум 500, 60 минут`() {
        val rules = GameRules.DEFAULT
        assertEquals(BankerFeeMode.ONCE_FIXED, rules.bankerFeeMode)
        assertEquals(100L, rules.bankerFeeValue)
        assertEquals(RakeMode.PERCENT, rules.rakeMode)
        assertEquals(10L, rules.rakeValue)
        assertEquals(500L, rules.minBuyIn)
        assertFalse(rules.splitPayment)
        assertEquals(60, rules.minPlayMinutes)
        assertTrue(rules.problems().isEmpty())
    }

    @Test
    fun `отрицательные сбор и рейк — проблемы`() {
        val problems = GameRules(bankerFeeValue = -1, rakeValue = -1).problems()
        assertEquals(listOf(RulesProblem.BANKER_FEE_NEGATIVE, RulesProblem.RAKE_NEGATIVE), problems)
    }

    @Test
    fun `процент выше 100 — проблема, фикс 101 рубль — нет`() {
        val percent = GameRules(
            bankerFeeMode = BankerFeeMode.EACH_PERCENT,
            bankerFeeValue = 101,
            rakeMode = RakeMode.PERCENT,
            rakeValue = 101,
        )
        assertEquals(
            listOf(RulesProblem.BANKER_FEE_PERCENT_ABOVE_MAX, RulesProblem.RAKE_PERCENT_ABOVE_MAX),
            percent.problems(),
        )
        val fixed =
            GameRules(
                bankerFeeMode = BankerFeeMode.EACH_FIXED,
                bankerFeeValue = 101,
                rakeMode = RakeMode.FIXED,
                rakeValue = 101,
            )
        assertTrue(fixed.problems().isEmpty())
        assertTrue(
            GameRules(
                bankerFeeMode = BankerFeeMode.ONCE_PERCENT,
                bankerFeeValue = 100,
                rakeValue = 100,
            ).problems().isEmpty(),
        )
    }

    @Test
    fun `фиксированные суммы выше MAX_AMOUNT — проблемы`() {
        val rules = GameRules(
            bankerFeeMode = BankerFeeMode.ONCE_FIXED,
            bankerFeeValue = 100_000_001,
            rakeMode = RakeMode.FIXED,
            rakeValue = 100_000_001,
            minBuyIn = 100_000_001,
        )
        assertEquals(
            listOf(
                RulesProblem.BANKER_FEE_ABOVE_MAX_AMOUNT,
                RulesProblem.RAKE_ABOVE_MAX_AMOUNT,
                RulesProblem.MIN_BUY_IN_ABOVE_MAX_AMOUNT,
            ),
            rules.problems(),
        )
    }

    @Test
    fun `минимальная покупка 0 и время игры вне диапазона — проблемы`() {
        assertEquals(
            listOf(RulesProblem.MIN_BUY_IN_NOT_POSITIVE, RulesProblem.MIN_PLAY_MINUTES_OUT_OF_RANGE),
            GameRules(minBuyIn = 0, minPlayMinutes = -1).problems(),
        )
        assertEquals(
            listOf(RulesProblem.MIN_PLAY_MINUTES_OUT_OF_RANGE),
            GameRules(minPlayMinutes = GameRules.MAX_MIN_PLAY_MINUTES + 1).problems(),
        )
        assertTrue(GameRules(minPlayMinutes = 0, minBuyIn = 1).problems().isEmpty())
        assertTrue(GameRules(minPlayMinutes = GameRules.MAX_MIN_PLAY_MINUTES).problems().isEmpty())
    }

    @Test
    fun `признаки режимов сбора`() {
        assertTrue(BankerFeeMode.ONCE_FIXED.isOnce)
        assertTrue(BankerFeeMode.ONCE_PERCENT.isOnce)
        assertFalse(BankerFeeMode.EACH_FIXED.isOnce)
        assertFalse(BankerFeeMode.EACH_PERCENT.isOnce)
        assertTrue(BankerFeeMode.ONCE_PERCENT.isPercent)
        assertTrue(BankerFeeMode.EACH_PERCENT.isPercent)
        assertFalse(BankerFeeMode.ONCE_FIXED.isPercent)
        assertFalse(BankerFeeMode.EACH_FIXED.isPercent)
    }
}

class GameModelTest {
    @Test
    fun `активны только RUNNING и SETTLING`() {
        assertEquals(
            setOf(GameStatus.RUNNING, GameStatus.SETTLING),
            GameStatus.entries.filter { it.isActive }.toSet(),
        )
    }

    @Test
    fun `банкир играет, если задан bankerPlayerId`() {
        assertFalse(emptyGame().bankerIsPlayer)
        assertTrue(emptyGame(bankerPlayerId = playerId(1)).bankerIsPlayer)
        assertEquals(Game.LEDGER_SCHEMA_VERSION, emptyGame().schemaVersion)
        assertFalse(emptyGame().editedAfterClose)
    }

    @Test
    fun `настройки по умолчанию — тёмная тема, экран не гаснет`() {
        val settings = AppSettings()
        assertEquals(GameRules.DEFAULT, settings.rulesForNewGame)
        assertEquals("", settings.lastBankerName)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertFalse(settings.dynamicColor)
        assertTrue(settings.keepScreenOn)
        assertTrue(settings.haptics)
        assertFalse(settings.oddsCalculatorVisible)
    }

    @Test
    fun `id — обёртки над Uuid`() {
        val uuid = Uuid.fromLongs(1, 2)
        assertEquals(uuid, GameId(uuid).value)
        assertEquals(uuid, PlayerId(uuid).value)
        assertEquals(uuid, EntryId(uuid).value)
        assertEquals(uuid, OperationId(uuid).value)
        assertEquals(uuid, HandoverId(uuid).value)
    }
}

class GameSnapshotApplyTest {
    private val anna = Player(playerId(1), gameId(1), "Анна", T0)

    @Test
    fun `пустое изменение даёт равный снимок`() {
        val snapshot = emptySnapshot()
        assertEquals(snapshot, snapshot.apply(GameChange(gameId(1), baseSeq = 0)))
        assertEquals(1L, snapshot.nextSeq)
        assertNull(snapshot.player(playerId(1)))
    }

    @Test
    fun `изменение дописывает журнал, игроков и акты, заменяет строку игры`() {
        val snapshot = emptySnapshot()
        val closed = snapshot.game.copy(status = GameStatus.CLOSED)
        val handover = Handover(HandoverId(Uuid.fromLongs(9, 9)), gameId(1), "Олег", "Влад", 0, 0, 1, T0)
        val result = snapshot.apply(
            GameChange(
                gameId = gameId(1),
                baseSeq = 0,
                game = closed,
                players = listOf(anna),
                entries = listOf(entry(1, EntryType.BUY_IN, anna.id, 1000)),
                handovers = listOf(handover),
            ),
        )
        assertSame(closed, result.game)
        assertEquals(listOf(anna), result.players)
        assertEquals(2L, result.nextSeq)
        assertEquals(anna, result.player(anna.id))
        assertEquals(listOf(handover), result.handovers)
    }

    @Test
    fun `переименование обновляет игрока на месте`() {
        val bob = Player(playerId(2), gameId(1), "Борис", T0)
        val snapshot = emptySnapshot().copy(players = listOf(anna, bob))
        val renamed = anna.copy(name = "Аня")
        val result = snapshot.apply(GameChange(gameId(1), baseSeq = 0, players = listOf(renamed)))
        assertEquals(listOf(renamed, bob), result.players)
    }

    @Test
    fun `акты упорядочены по ledgerSeq, а не по часам телефонов`() {
        val second = Handover(HandoverId(Uuid.fromLongs(9, 1)), gameId(1), "Б", "В", 0, 0, 2, T0)
        val first = Handover(HandoverId(Uuid.fromLongs(9, 2)), gameId(1), "А", "Б", 0, 0, 1, T0 + 5.minutes)
        val sameSeq = Handover(HandoverId(Uuid.fromLongs(9, 3)), gameId(1), "В", "Г", 0, 0, 2, T0 - 5.minutes)
        val result = emptySnapshot().apply(
            GameChange(gameId(1), baseSeq = 0, handovers = listOf(second, first, sameSeq)),
        )
        assertEquals(listOf(first, second, sameSeq), result.handovers)
    }

    @Test
    fun `чужая игра или разрыв seq — ошибка`() {
        val snapshot = emptySnapshot()
        assertFailsWith<IllegalArgumentException> { snapshot.apply(GameChange(gameId(2), baseSeq = 0)) }
        assertFailsWith<IllegalArgumentException> {
            snapshot.apply(GameChange(gameId(1), baseSeq = 0, game = emptyGame(id = gameId(2))))
        }
        assertFailsWith<IllegalArgumentException> {
            snapshot.apply(
                GameChange(gameId(1), baseSeq = 0, entries = listOf(entry(2, EntryType.BUY_IN, anna.id, 1000))),
            )
        }
        // Изменение, построенное по устаревшему (или чужому) снимку.
        assertFailsWith<IllegalArgumentException> { snapshot.apply(GameChange(gameId(1), baseSeq = 3)) }
        val one = snapshot.apply(
            GameChange(gameId(1), baseSeq = 0, entries = listOf(entry(1, EntryType.BUY_IN, anna.id, 1000))),
        )
        assertEquals(1L, one.lastSeq)
        assertFailsWith<IllegalArgumentException> { one.apply(GameChange(gameId(1), baseSeq = 0)) }
    }
}
