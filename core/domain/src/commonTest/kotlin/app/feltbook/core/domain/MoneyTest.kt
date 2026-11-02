package app.feltbook.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {
    @Test
    fun rakeIsNotRoundedToTens() {
        // ТЗ §9: закуп 1 250 → рейк 10 % = 125 ₽.
        assertEquals(125L, percentOf(amount = 1_250L, percent = 10L))
    }

    @Test
    fun halfRoundsUp() {
        assertEquals(1L, percentOf(amount = 5L, percent = 10L))
        assertEquals(0L, percentOf(amount = 4L, percent = 10L))
    }
}
