package app.feltbook.core.ui.format

import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun hoursAndMinutes() {
        assertEquals(TimeFormat.HoursMinutes(0, 47), TimeFormat.hoursMinutes(47.minutes))
        assertEquals(TimeFormat.HoursMinutes(4, 5), TimeFormat.hoursMinutes(4.hours + 5.minutes))
        assertEquals(TimeFormat.HoursMinutes(0, 0), TimeFormat.hoursMinutes((-1).hours))
    }

    @Test
    fun clockIn24Hours() {
        val moscow = ZoneId.of("Europe/Moscow")
        // 2026-10-18T17:05:00Z = 20:05 по Москве.
        val instant = Instant.fromEpochMilliseconds(1_792_343_100_000L)
        assertEquals("20:05", TimeFormat.clock(instant, moscow))
        assertEquals("17:05", TimeFormat.clock(instant, ZoneId.of("UTC")))
    }
}
