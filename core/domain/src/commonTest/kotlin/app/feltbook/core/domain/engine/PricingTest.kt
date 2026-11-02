package app.feltbook.core.domain.engine

import app.feltbook.core.domain.Example81
import app.feltbook.core.domain.T0
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
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.playerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PricingTest {
    private val anna = Player(playerId(10), gameId(1), "Анна", T0)

    private fun withAnna(
        rules: GameRules = GameRules.DEFAULT,
        vararg entries: app.feltbook.core.domain.model.LedgerEntry,
    ) = GameSnapshot(emptyGame(rules = rules), listOf(anna), entries.toList(), emptyList())

    private fun annaBought(rules: GameRules = GameRules.DEFAULT) =
        withAnna(rules, entry(1, EntryType.BUY_IN, anna.id, 1_000))

    private fun annaLeft(rules: GameRules = GameRules.DEFAULT) = withAnna(
        rules,
        entry(1, EntryType.BUY_IN, anna.id, 1_000),
        entry(2, EntryType.CASH_OUT, anna.id, 1_000, op = 2),
    )

    // ТЗ §3.2, таблица «Примеры из правил стола».

    @Test
    fun `первый закуп в долг на 1 000 при рейке 10 процентов — к оплате 1 200`() {
        val quote = Pricing.quoteNewPlayer(emptySnapshot(), "Андрей", 1_000)
        assertEquals(1_000L, quote.chips)
        assertEquals(100L, quote.rake)
        assertEquals(100L, quote.bankerFee)
        assertEquals(1_200L, quote.total)
        assertEquals(1_200L, quote.toDebt(BuyInPayment.DEBT))
        assertEquals(0L, quote.paidNow(BuyInPayment.DEBT))
        assertTrue(quote.isFirstBuyIn)
        assertFalse(quote.isReturn)
        assertNull(quote.playerId)
        assertTrue(quote.canConfirm)
    }

    @Test
    fun `первый закуп на 2 000 при рейке 10 процентов — к оплате 2 300`() {
        val quote = Pricing.quoteNewPlayer(emptySnapshot(), "Влад", 2_000)
        assertEquals(200L, quote.rake)
        assertEquals(100L, quote.bankerFee)
        assertEquals(2_300L, quote.total)
        assertEquals(2_300L, quote.paidNow(BuyInPayment.PAID))
        assertEquals(0L, quote.toDebt(BuyInPayment.PAID))
    }

    @Test
    fun `первый закуп на 2 000 при рейке 100 рублей фикс — к оплате 2 200`() {
        val rules = GameRules(rakeMode = RakeMode.FIXED, rakeValue = 100)
        val quote = Pricing.quoteNewPlayer(emptySnapshot(emptyGame(rules = rules)), "Влад", 2_000)
        assertEquals(100L, quote.rake)
        assertEquals(2_200L, quote.total)
    }

    @Test
    fun `докуп на 1 000 при рейке 10 процентов — к оплате 1 100, сбор уже взят`() {
        val quote = Pricing.quoteBuyIn(annaBought(), anna.id, 1_000)
        assertEquals(100L, quote.rake)
        assertEquals(0L, quote.bankerFee)
        assertEquals(1_100L, quote.total)
        assertFalse(quote.isFirstBuyIn)
        assertFalse(quote.isReturn)
        assertTrue(quote.feeAlreadyTaken)
        assertFalse(quote.feeWaivedForBanker)
        assertEquals(anna.id, quote.playerId)
    }

    // Округление.

    @Test
    fun `закуп 1 250 — рейк 125, 1 255 — 126, 1 254 — 125`() {
        assertEquals(125L, Pricing.rake(1_250, GameRules.DEFAULT))
        assertEquals(126L, Pricing.rake(1_255, GameRules.DEFAULT))
        assertEquals(125L, Pricing.rake(1_254, GameRules.DEFAULT))
        assertEquals(1_475L, Pricing.quoteNewPlayer(emptySnapshot(), "Андрей", 1_250).total)
    }

    @Test
    fun `огромный закуп 10 000 000 — рейк 1 000 000 без переполнения`() {
        val quote = Pricing.quoteNewPlayer(emptySnapshot(), "Олигарх", 10_000_000)
        assertEquals(1_000_000L, quote.rake)
        assertEquals(11_000_100L, quote.total)
    }

    // Четыре режима сбора.

    @Test
    fun `разово фикс — сбор только с первого закупа`() {
        val rules = GameRules(bankerFeeMode = BankerFeeMode.ONCE_FIXED, bankerFeeValue = 150)
        assertEquals(150L, Pricing.bankerFee(2_000, rules, isFirstBuyIn = true, isBanker = false))
        assertEquals(0L, Pricing.bankerFee(2_000, rules, isFirstBuyIn = false, isBanker = false))
    }

    @Test
    fun `разово процент — от первого закупа с округлением`() {
        val rules = GameRules(bankerFeeMode = BankerFeeMode.ONCE_PERCENT, bankerFeeValue = 5)
        assertEquals(63L, Pricing.bankerFee(1_255, rules, isFirstBuyIn = true, isBanker = false))
        assertEquals(0L, Pricing.bankerFee(1_255, rules, isFirstBuyIn = false, isBanker = false))
        val quote = Pricing.quoteBuyIn(annaBought(rules), anna.id, 1_000)
        assertEquals(0L, quote.bankerFee)
        assertTrue(quote.feeAlreadyTaken)
    }

    @Test
    fun `с каждого закупа фикс — сбор и с докупа`() {
        val rules = GameRules(bankerFeeMode = BankerFeeMode.EACH_FIXED, bankerFeeValue = 100)
        val quote = Pricing.quoteBuyIn(annaBought(rules), anna.id, 1_000)
        assertEquals(100L, quote.bankerFee)
        assertEquals(1_200L, quote.total)
        assertFalse(quote.feeAlreadyTaken)
    }

    @Test
    fun `с каждого закупа процент — сбор с каждой покупки`() {
        val rules = GameRules(bankerFeeMode = BankerFeeMode.EACH_PERCENT, bankerFeeValue = 5)
        val quote = Pricing.quoteBuyIn(annaBought(rules), anna.id, 1_255)
        assertEquals(63L, quote.bankerFee)
        assertEquals(126L, quote.rake)
        assertEquals(1_444L, quote.total)
    }

    @Test
    fun `возврат вышедшего — докуп, в разовом режиме без сбора`() {
        val quote = Pricing.quoteBuyIn(annaLeft(), anna.id, 1_000)
        assertTrue(quote.isReturn)
        assertFalse(quote.isFirstBuyIn)
        assertEquals(0L, quote.bankerFee)
        assertEquals(1_100L, quote.total)
    }

    @Test
    fun `возврат вышедшего в режиме с каждого закупа — со сбором`() {
        val rules = GameRules(bankerFeeMode = BankerFeeMode.EACH_FIXED, bankerFeeValue = 100)
        val quote = Pricing.quoteBuyIn(annaLeft(rules), anna.id, 1_000)
        assertTrue(quote.isReturn)
        assertEquals(100L, quote.bankerFee)
        assertEquals(1_200L, quote.total)
    }

    @Test
    fun `банкир-игрок платит рейк, но сбора с себя не берёт ни в одном режиме`() {
        BankerFeeMode.entries.forEach { mode ->
            val rules = GameRules(bankerFeeMode = mode, bankerFeeValue = 10)
            val game = emptyGame(rules = rules, bankerPlayerId = anna.id)
            val first = Pricing.quoteBuyIn(GameSnapshot(game, listOf(anna), emptyList(), emptyList()), anna.id, 1_000)
            assertEquals(0L, first.bankerFee, "первый закуп, $mode")
            assertEquals(100L, first.rake, "рейк, $mode")
            assertTrue(first.feeWaivedForBanker, "без сбора, $mode")
            assertFalse(first.feeAlreadyTaken)
            val rebuy = Pricing.quoteBuyIn(annaBought(rules).copy(game = game), anna.id, 1_000)
            assertEquals(0L, rebuy.bankerFee, "докуп, $mode")
            assertEquals(!mode.isOnce, rebuy.feeWaivedForBanker, "строка «без сбора» на докупе, $mode")
            assertEquals(0L, Pricing.bankerFee(1_000, rules, isFirstBuyIn = true, isBanker = true))
        }
    }

    // Минимальная покупка и границы суммы.

    @Test
    fun `минимум 500 — 499 блокер, 500 можно`() {
        val below = Pricing.quoteNewPlayer(emptySnapshot(), "Андрей", 499)
        assertEquals(listOf<DomainError>(DomainError.BelowMinBuyIn(500)), below.blockers)
        assertFalse(below.canConfirm)
        assertTrue(Pricing.quoteNewPlayer(emptySnapshot(), "Андрей", 500).canConfirm)
        val custom = emptySnapshot(emptyGame(rules = GameRules(minBuyIn = 1_000)))
        assertEquals(
            listOf<DomainError>(DomainError.BelowMinBuyIn(1_000)),
            Pricing.quoteNewPlayer(custom, "А", 999).blockers,
        )
    }

    @Test
    fun `нулевая, отрицательная и слишком большая сумма — блокеры`() {
        assertEquals(
            listOf<DomainError>(DomainError.AmountNotPositive),
            Pricing.quoteNewPlayer(emptySnapshot(), "А", 0).blockers,
        )
        val negative = Pricing.quoteNewPlayer(emptySnapshot(), "А", -100)
        assertEquals(listOf<DomainError>(DomainError.AmountNotPositive), negative.blockers)
        assertEquals(0L, negative.rake)
        assertTrue(negative.warnings.isEmpty())
        val huge = Pricing.quoteNewPlayer(emptySnapshot(), "А", Money.MAX_AMOUNT + 1)
        assertEquals(listOf<DomainError>(DomainError.AmountTooLarge), huge.blockers)
        assertEquals(0L, huge.bankerFee)
    }

    // Раздельная оплата.

    @Test
    fun `раздельная оплата — комиссии сразу, фишки в долг`() {
        val quote = Pricing.quoteNewPlayer(emptySnapshot(), "Андрей", 1_000)
        val feesOnly = BuyInPayment(chipsPaid = false, feesPaid = true)
        assertFalse(feesOnly.isSingleStatus)
        assertEquals(200L, quote.fees)
        assertEquals(200L, quote.paidNow(feesOnly))
        assertEquals(1_000L, quote.toDebt(feesOnly))
        val chipsOnly = BuyInPayment(chipsPaid = true, feesPaid = false)
        assertEquals(1_000L, quote.paidNow(chipsOnly))
        assertTrue(BuyInPayment.PAID.isSingleStatus)
        assertTrue(BuyInPayment.DEBT.isSingleStatus)
    }

    // Подозрительные суммы.

    @Test
    fun `без покупок подозрителен только порог 20 000`() {
        assertTrue(Pricing.quoteNewPlayer(emptySnapshot(), "А", 20_000).warnings.isEmpty())
        assertEquals(
            listOf<DomainWarning>(DomainWarning.SuspiciousBuyIn(20_001, null)),
            Pricing.quoteNewPlayer(emptySnapshot(), "А", 20_001).warnings,
        )
        assertNull(Pricing.typicalPurchase(emptySnapshot()))
    }

    @Test
    fun `больше пяти средних — подозрительно`() {
        val snapshot = Example81.snapshot(Example81.afterOp4)
        assertEquals(1_250L, Pricing.typicalPurchase(snapshot))
        assertTrue(Pricing.quoteBuyIn(snapshot, Example81.andrey, 6_250).warnings.isEmpty())
        assertEquals(
            listOf<DomainWarning>(DomainWarning.SuspiciousBuyIn(6_251, 1_250)),
            Pricing.quoteBuyIn(snapshot, Example81.andrey, 6_251).warnings,
        )
    }

    @Test
    fun `средняя покупка — по действующим закупам`() {
        val snapshot = withAnna(
            GameRules.DEFAULT,
            entry(1, EntryType.BUY_IN, anna.id, 1_000),
            entry(2, EntryType.BUY_IN, anna.id, 501, op = 2),
            entry(3, EntryType.BUY_IN, anna.id, 9_000, op = 3),
            entry(4, EntryType.REVERSAL, anna.id, 9_000, op = 4, reverses = entryId(3)),
        )
        assertEquals(751L, Pricing.typicalPurchase(snapshot))
    }

    // Статус игры, игрок, имя.

    @Test
    fun `покупка только в идущей игре`() {
        val settling = annaBought().let { it.copy(game = it.game.copy(status = GameStatus.SETTLING)) }
        val expected = DomainError.WrongGameStatus(GameStatus.SETTLING, setOf(GameStatus.RUNNING))
        assertEquals(listOf<DomainError>(expected), Pricing.quoteBuyIn(settling, anna.id, 1_000).blockers)
        assertEquals(listOf<DomainError>(expected), Pricing.quoteNewPlayer(settling, "Борис", 1_000).blockers)
    }

    @Test
    fun `докуп неизвестному или void-игроку — PlayerNotFound`() {
        val unknown = Pricing.quoteBuyIn(emptySnapshot(), playerId(99), 1_000)
        assertEquals(listOf<DomainError>(DomainError.PlayerNotFound(playerId(99))), unknown.blockers)
        assertTrue(unknown.isFirstBuyIn)
        val void = withAnna(
            GameRules.DEFAULT,
            entry(1, EntryType.BUY_IN, anna.id, 1_000),
            entry(2, EntryType.REVERSAL, anna.id, 1_000, op = 2, reverses = entryId(1)),
        )
        assertEquals(
            listOf<DomainError>(DomainError.PlayerNotFound(anna.id)),
            Pricing.quoteBuyIn(void, anna.id, 1_000).blockers,
        )
    }

    @Test
    fun `имя — пустое, длинное, занятое`() {
        assertEquals(
            listOf<DomainError>(DomainError.NameBlank),
            Pricing.quoteNewPlayer(emptySnapshot(), "   ", 1_000).blockers,
        )
        assertEquals(
            listOf<DomainError>(DomainError.NameTooLong),
            Pricing.quoteNewPlayer(emptySnapshot(), "Я".repeat(PlayerNames.MAX_LENGTH + 1), 1_000).blockers,
        )
        assertTrue(Pricing.quoteNewPlayer(emptySnapshot(), "Я".repeat(PlayerNames.MAX_LENGTH), 1_000).canConfirm)
        assertEquals(
            listOf<DomainError>(DomainError.NameTaken(anna.id)),
            Pricing.quoteNewPlayer(annaBought(), "  Анна ", 1_000).blockers,
        )
        assertTrue(Pricing.quoteNewPlayer(annaBought(), "анна", 1_000).canConfirm, "регистр учитывается")
    }

    @Test
    fun `имя void-игрока не занято — котировка возвращает его`() {
        val void = withAnna(
            GameRules.DEFAULT,
            entry(1, EntryType.BUY_IN, anna.id, 1_000),
            entry(2, EntryType.REVERSAL, anna.id, 1_000, op = 2, reverses = entryId(1)),
        )
        val quote = Pricing.quoteNewPlayer(void, "Анна", 1_000)
        assertTrue(quote.canConfirm)
        assertEquals(anna.id, quote.playerId)
        assertTrue(quote.isFirstBuyIn)
        assertEquals(100L, quote.bankerFee)
    }
}
