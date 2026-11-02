package app.feltbook.core.domain.engine

import app.feltbook.core.domain.T0
import app.feltbook.core.domain.TestIds
import app.feltbook.core.domain.emptySnapshot
import app.feltbook.core.domain.entry
import app.feltbook.core.domain.entryId
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.RulesProblem
import app.feltbook.core.domain.playerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class MoneyTest {
    @Test
    fun `рейк 10 процентов с 1 250 — 125, кратность 10 не нужна`() {
        assertEquals(125L, Money.percentOf(1_250, 10))
    }

    @Test
    fun `половина округляется вверх — 1 255 даёт 126`() {
        assertEquals(126L, Money.percentOf(1_255, 10))
    }

    @Test
    fun `меньше половины округляется вниз — 1 254 даёт 125`() {
        assertEquals(125L, Money.percentOf(1_254, 10))
    }

    @Test
    fun `граничные проценты и суммы`() {
        assertEquals(0L, Money.percentOf(0, 10))
        assertEquals(0L, Money.percentOf(1_000, 0))
        assertEquals(1_000L, Money.percentOf(1_000, 100))
        assertEquals(1L, Money.percentOf(5, 10))
        assertEquals(0L, Money.percentOf(4, 10))
        assertEquals(10_000_000L, Money.percentOf(Money.MAX_AMOUNT, 10))
        assertEquals(Money.MAX_AMOUNT, Money.percentOf(Money.MAX_AMOUNT, 100))
    }

    @Test
    fun `огромная сумма 10 000 000 без переполнения`() {
        assertEquals(1_000_000L, Money.percentOf(10_000_000, 10))
        assertEquals(333_333L, Money.percentOf(3_333_333, 10))
    }

    @Test
    fun `отрицательная сумма и процент вне 0-100 запрещены контрактом`() {
        assertFailsWith<IllegalArgumentException> { Money.percentOf(-5, 10) }
        assertFailsWith<IllegalArgumentException> { Money.percentOf(Money.MAX_AMOUNT + 1, 10) }
        assertFailsWith<IllegalArgumentException> { Money.percentOf(100, -1) }
        assertFailsWith<IllegalArgumentException> { Money.percentOf(100, 101) }
    }

    @Test
    fun `деление с округлением половины вверх`() {
        assertEquals(2L, Money.divRoundHalfUp(3, 2))
        assertEquals(1L, Money.divRoundHalfUp(4, 3))
        assertEquals(2L, Money.divRoundHalfUp(5, 3))
        assertEquals(0L, Money.divRoundHalfUp(0, 7))
        assertEquals(1_667L, Money.divRoundHalfUp(5_000, 3))
        assertFailsWith<IllegalArgumentException> { Money.divRoundHalfUp(-1, 3) }
        assertFailsWith<IllegalArgumentException> { Money.divRoundHalfUp(1, 0) }
    }
}

class StampTest {
    @Test
    fun `время обрезается до миллисекунд, id берутся из генератора`() {
        val ids = TestIds()
        val stamp = Stamp(T0 + 1_999_999.nanoseconds, ids::uuid)
        assertEquals(Instant.fromEpochMilliseconds(T0.toEpochMilliseconds() + 1), stamp.at)
        val first = stamp.nextUuid()
        assertNotEquals(first, stamp.nextUuid())
    }

    @Test
    fun `штамп по часам`() {
        val clock = object : Clock {
            override fun now(): Instant = T0
        }
        assertEquals(T0, Stamp.now(clock).at)
        val real = Stamp.now()
        assertNotEquals(real.nextUuid(), real.nextUuid())
    }
}

class LedgerTest {
    private val p = playerId(1)

    @Test
    fun `отменённые и отменяющие записи исключаются`() {
        val entries = listOf(
            entry(1, EntryType.BUY_IN, p, 1_000, op = 1),
            entry(2, EntryType.RAKE, p, 100, op = 1),
            entry(3, EntryType.BUY_IN, p, 500, op = 2),
            entry(4, EntryType.REVERSAL, p, 500, op = 3, reverses = entryId(3)),
        )
        assertEquals(listOf(entries[0], entries[1]), Ledger.effective(entries))
        assertEquals(setOf(entryId(3)), Ledger.reversedIds(entries))
        assertTrue(Ledger.effective(emptySnapshot().entries).isEmpty())
    }
}

class DomainErrorTest {
    @Test
    fun `нарушение правила несёт ошибку в сообщении`() {
        val error = DomainError.WrongGameStatus(GameStatus.CLOSED, setOf(GameStatus.RUNNING))
        val violation = DomainRuleViolation(error)
        assertSame(error, violation.error)
        assertTrue(violation.message!!.contains("CLOSED"))
        val thrown = assertFailsWith<DomainRuleViolation> { listOf<DomainError>(DomainError.NameBlank).throwFirst() }
        assertEquals(DomainError.NameBlank, thrown.error)
        emptyList<DomainError>().throwFirst()
        assertEquals(
            DomainError.InvalidRules(listOf(RulesProblem.RAKE_NEGATIVE)),
            DomainError.InvalidRules(listOf(RulesProblem.RAKE_NEGATIVE)),
        )
        assertEquals(PlayerStatus.PLAYING, DomainError.PlayerNotPlaying(p, PlayerStatus.PLAYING).status)
        assertEquals(5L, Suspicion.AVERAGE_MULTIPLIER)
        assertEquals(20_000L, Suspicion.ABSOLUTE_LIMIT)
    }

    private val p = playerId(1)
}
