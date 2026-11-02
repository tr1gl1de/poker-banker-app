package app.feltbook.core.ui.format

import android.content.res.Resources
import app.feltbook.core.ui.R
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Строки с ресурсами: русские plurals (локаль ru), длительности, даты, фразы TalkBack. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru")
class ResourceFormatTest {
    private val res: Resources = RuntimeEnvironment.getApplication().resources
    private val nbsp = '\u00A0'

    @Test
    fun rublesPlurals() {
        assertEquals("1 рубль", res.a11yRubles(1).plain())
        assertEquals("2 рубля", res.a11yRubles(2).plain())
        assertEquals("5 рублей", res.a11yRubles(5).plain())
        assertEquals("11 рублей", res.a11yRubles(11).plain())
        assertEquals("21 рубль", res.a11yRubles(21).plain())
        assertEquals("101 рубль", res.a11yRubles(101).plain())
        assertEquals("112 рублей", res.a11yRubles(112).plain())
        assertEquals("353 рубля", res.a11yRubles(353).plain())
        assertEquals("1 053 рубля", res.a11yRubles(1_053).plain())
        assertEquals("0 рублей", res.a11yRubles(0).plain())
    }

    @Test
    fun signedPhrases() {
        assertEquals("минус 3 400 рублей", res.a11yRubles(-3_400).plain())
        assertEquals("плюс 353 рубля", res.a11ySignedRubles(353).plain())
        assertEquals("Банкир выплачивает 353 рубля", res.a11yBankerPays(353).plain())
        assertEquals("Влад доплачивает 1 053 рубля", res.a11yPlayerPays("Влад", 1_053).plain())
        assertEquals("Долг 1 053 рубля", res.a11yDebt(1_053).plain())
        assertEquals("Влад должен 21 рубль", res.a11yOwesBanker("Влад", 21).plain())
    }

    @Test
    fun durations() {
        assertEquals("47${nbsp}мин", res.formatDuration(47.minutes + 59.seconds))
        assertEquals("0${nbsp}мин", res.formatDuration((-5).minutes))
        assertEquals("1${nbsp}ч 00${nbsp}мин", res.formatDuration(60.minutes))
        assertEquals("4${nbsp}ч 05${nbsp}мин", res.formatDuration(4.hours + 5.minutes))
        assertEquals("2${nbsp}ч 14${nbsp}мин", res.formatDuration(134.minutes))
    }

    @Test
    fun dates() {
        assertEquals("18${nbsp}окт", res.formatShortDate(LocalDate.of(2026, 10, 18)))
        assertEquals("1${nbsp}мая", res.formatShortDate(LocalDate.of(2026, 5, 1)))
        assertEquals("Октябрь 2026", res.formatMonthYear(2026, 10))
        assertEquals("13.09", TimeFormat.chartDate(LocalDate.of(2026, 9, 13)))
    }

    @Test
    fun chipsPlural() {
        val five = res.getQuantityString(R.plurals.chips_count, pluralQuantity(5_000), MoneyFormat.number(5_000))
        assertEquals("5 000 фишек", five.plain())
        val two = res.getQuantityString(R.plurals.chips_count, pluralQuantity(2), "2")
        assertEquals("2 фишки", two.plain())
    }

    /** Неразрывные пробелы → обычные, чтобы ожидания читались. */
    private fun String.plain(): String = replace(nbsp, ' ')
}
