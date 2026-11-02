package app.feltbook.core.odds

import kotlin.test.Test
import kotlin.test.assertEquals

class DeckTest {
    @Test
    fun standardDeckHas52Cards() {
        assertEquals(52, Deck.SIZE)
    }
}
