package app.feltbook.core.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class PluralQuantityTest {
    @Test
    fun keepsLastTwoDigits() {
        assertEquals(0, pluralQuantity(0))
        assertEquals(1, pluralQuantity(1))
        assertEquals(11, pluralQuantity(11))
        assertEquals(99, pluralQuantity(99))
        assertEquals(100, pluralQuantity(100))
        assertEquals(101, pluralQuantity(101))
        assertEquals(153, pluralQuantity(1_053))
        assertEquals(112, pluralQuantity(100_000_012))
    }

    @Test
    fun ignoresSign() {
        assertEquals(21, pluralQuantity(-21))
        assertEquals(100, pluralQuantity(-3_400))
        assertEquals(108, pluralQuantity(Long.MIN_VALUE))
    }
}
