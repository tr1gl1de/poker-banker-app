package app.feltbook.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountEntryTest {
    private fun AmountEntry.type(vararg keys: KeypadKey, max: Long = DEFAULT_MAX_AMOUNT): AmountEntry =
        keys.fold(this) { entry, key -> entry.press(key, max) ?: entry }

    private fun d(value: Int) = KeypadKey.Digit(value)

    @Test
    fun digitsAppend() {
        assertEquals(1_250L, AmountEntry().type(d(1), d(2), d(5), d(0)).value)
    }

    @Test
    fun leadingZerosAreIgnored() {
        assertEquals(AmountEntry(), AmountEntry().press(d(0)))
        assertEquals(AmountEntry(), AmountEntry().press(KeypadKey.TripleZero))
        assertEquals(7L, AmountEntry().type(d(0), d(0), d(7)).value)
    }

    @Test
    fun tripleZeroMultipliesByThousand() {
        assertEquals(2_000L, AmountEntry().type(d(2), KeypadKey.TripleZero).value)
        assertEquals(15_000L, AmountEntry().type(d(1), d(5), KeypadKey.TripleZero).value)
    }

    @Test
    fun backspaceRemovesLastDigitAndClearEmpties() {
        assertEquals(12L, AmountEntry(125L).press(KeypadKey.Backspace)?.value)
        assertEquals(0L, AmountEntry(5L).press(KeypadKey.Backspace)?.value)
        assertEquals(AmountEntry(), AmountEntry(0L).press(KeypadKey.Backspace))
        assertEquals(AmountEntry(), AmountEntry(98_765L).press(KeypadKey.Clear))
    }

    @Test
    fun overflowIsRejected() {
        val max = 100_000_000L
        assertEquals(max, AmountEntry(10_000_000L).press(d(0), max)?.value)
        assertNull(AmountEntry(10_000_000L).press(d(1), max))
        assertNull(AmountEntry(100_000_000L).press(d(0), max))
        assertEquals(max, AmountEntry(100_000L).press(KeypadKey.TripleZero, max)?.value)
        assertNull(AmountEntry(100_001L).press(KeypadKey.TripleZero, max))
        assertNull(AmountEntry(5L).press(d(1), maxValue = 50L))
    }

    @Test
    fun presetIsReplacedByNextDigit() {
        val chip = AmountEntry.preset(2_000L)
        assertEquals(AmountEntry(5L), chip.press(d(5)))
        assertEquals(AmountEntry(), chip.press(d(0)))
        assertEquals(AmountEntry(), chip.press(KeypadKey.TripleZero))
        assertEquals(AmountEntry(200L), chip.press(KeypadKey.Backspace))
    }
}
