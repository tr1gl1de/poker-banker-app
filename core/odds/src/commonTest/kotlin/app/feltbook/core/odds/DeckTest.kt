package app.feltbook.core.odds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class DeckTest {
    @Test
    fun standardDeckHas52Cards() {
        assertEquals(52, Deck.SIZE)
        assertEquals(52, Deck.cards.size)
        assertEquals(52, Deck.cards.toSet().size)
        assertEquals((0 until 52).toList(), Deck.cards.map { it.id })
    }

    @Test
    fun `разбор и запись карты`() {
        val ace = Card.parse("As")
        assertEquals(Rank.ACE, ace.rank)
        assertEquals(Suit.SPADES, ace.suit)
        assertEquals("As", ace.toString())
        assertEquals("Td", Card.parse("td").toString())
        assertEquals("Kh", Card.parse(" KH ").toString())
        assertSame(Card.of(Rank.TWO, Suit.CLUBS), Card.parse("2c"))
        assertSame(Card.fromId(51), Card.parse("As"))
        assertSame(Card.fromId(0), Card.parse("2c"))
    }

    @Test
    fun `неверная запись карты`() {
        assertNull(Card.parseOrNull("A"))
        assertNull(Card.parseOrNull("Asd"))
        assertNull(Card.parseOrNull("1s"))
        assertNull(Card.parseOrNull("Ax"))
        assertFailsWith<IllegalArgumentException> { Card.parse("10s") }
        assertFailsWith<IllegalArgumentException> { Card.fromId(52) }
        assertFailsWith<IllegalArgumentException> { Card.fromId(-1) }
    }

    @Test
    fun `разбор списка карт`() {
        assertEquals(listOf("As", "Kd", "Th"), Card.parseList("As Kd,Th").map(Card::toString))
        assertEquals(listOf("As", "Kd"), Card.parseList("AsKd").map(Card::toString))
        assertEquals(emptyList(), Card.parseList(""))
        assertFailsWith<IllegalArgumentException> { Card.parseList("AsK") }
    }

    @Test
    fun `карты упорядочены по номеру`() {
        assertEquals(listOf("2c", "2s", "As"), Card.parseList("As 2s 2c").sorted().map(Card::toString))
    }
}
