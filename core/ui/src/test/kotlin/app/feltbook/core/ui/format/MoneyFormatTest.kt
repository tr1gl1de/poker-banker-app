package app.feltbook.core.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {
    private val nbsp = '\u00A0'
    private val minus = '\u2212'

    @Test
    fun groupsThousandsWithNbspFromFourDigits() {
        assertEquals("0", MoneyFormat.number(0))
        assertEquals("500", MoneyFormat.number(500))
        assertEquals("1${nbsp}053", MoneyFormat.number(1_053))
        assertEquals("214${nbsp}500", MoneyFormat.number(214_500))
        assertEquals("1${nbsp}000${nbsp}000", MoneyFormat.number(1_000_000))
        assertEquals("100${nbsp}000${nbsp}000", MoneyFormat.number(100_000_000))
    }

    @Test
    fun negativeUsesUnicodeMinus() {
        assertEquals("${minus}3${nbsp}400", MoneyFormat.number(-3_400))
        assertEquals("${minus}5", MoneyFormat.number(-5))
        assertEquals(
            "${minus}9${nbsp}223${nbsp}372${nbsp}036${nbsp}854${nbsp}775${nbsp}808",
            MoneyFormat.number(Long.MIN_VALUE),
        )
    }

    @Test
    fun rublesHaveNbspBeforeSign() {
        assertEquals("2${nbsp}300$nbsp₽", MoneyFormat.rub(2_300))
        assertEquals("${minus}3${nbsp}400$nbsp₽", MoneyFormat.rub(-3_400))
        assertEquals("0$nbsp₽", MoneyFormat.rub(0))
    }

    @Test
    fun signedShowsPlusOnlyForPositive() {
        assertEquals("+353$nbsp₽", MoneyFormat.signedRub(353))
        assertEquals("${minus}353$nbsp₽", MoneyFormat.signedRub(-353))
        assertEquals("0$nbsp₽", MoneyFormat.signedRub(0))
        assertEquals("+2${nbsp}700", MoneyFormat.signedNumber(2_700))
    }

    @Test
    fun percents() {
        assertEquals("10$nbsp%", MoneyFormat.percent(10))
        assertEquals("38,4$nbsp%", MoneyFormat.percentOneDecimal(38.44))
        assertEquals("38,5$nbsp%", MoneyFormat.percentOneDecimal(38.45))
        assertEquals("100,0$nbsp%", MoneyFormat.percentOneDecimal(100.0))
        assertEquals("±0,3$nbsp%", MoneyFormat.plusMinusPercent(0.3))
    }

    @Test
    fun ratioRoundsHalfUpToOneDecimal() {
        assertEquals("1,4", MoneyFormat.ratioOneDecimal(7, 5))
        // 1 / 20 = 0,05 → половина вверх → 0,1.
        assertEquals("0,1", MoneyFormat.ratioOneDecimal(1, 20))
        assertEquals("0,3", MoneyFormat.ratioOneDecimal(1, 3))
        assertEquals("0,7", MoneyFormat.ratioOneDecimal(2, 3))
        assertEquals("0,0", MoneyFormat.ratioOneDecimal(0, 4))
        assertEquals("1${nbsp}000,0", MoneyFormat.ratioOneDecimal(1_000, 1))
    }
}
