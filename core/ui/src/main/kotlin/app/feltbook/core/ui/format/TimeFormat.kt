package app.feltbook.core.ui.format

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.feltbook.core.ui.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Время и даты (m3-ui-spec §0.1). Длительность: `< 60 мин` → `47 мин`, иначе `4 ч 05 мин`.
 * Дата короткая: `18 окт` (месяц в родительном, без точки). Время — `HH:mm`, дата графика — `dd.MM`.
 */
object TimeFormat {
    /** Часы и минуты длительности (отрицательная — как 0; секунды отбрасываются). */
    data class HoursMinutes(val hours: Long, val minutes: Int)

    fun hoursMinutes(duration: Duration): HoursMinutes {
        val totalMinutes = duration.inWholeMinutes.coerceAtLeast(0)
        return HoursMinutes(totalMinutes / MINUTES_PER_HOUR, (totalMinutes % MINUTES_PER_HOUR).toInt())
    }

    fun zoned(instant: Instant, zone: ZoneId): ZonedDateTime =
        java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()).atZone(zone)

    fun localDate(instant: Instant, zone: ZoneId): LocalDate = zoned(instant, zone).toLocalDate()

    /** `20:05`. */
    fun clock(instant: Instant, zone: ZoneId): String {
        val time = zoned(instant, zone)
        return "${twoDigits(time.hour)}:${twoDigits(time.minute)}"
    }

    /** `13.09`. */
    fun chartDate(date: LocalDate): String = "${twoDigits(date.dayOfMonth)}.${twoDigits(date.monthValue)}"

    private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

    private const val MINUTES_PER_HOUR = 60L
}

/** `47 мин` / `4 ч 05 мин`. */
fun Resources.formatDuration(duration: Duration): String {
    val (hours, minutes) = TimeFormat.hoursMinutes(duration)
    return if (hours == 0L) {
        getString(R.string.duration_minutes, minutes)
    } else {
        getString(R.string.duration_hours_minutes, hours.toInt(), minutes)
    }
}

/** `18 окт`. */
fun Resources.formatShortDate(date: LocalDate): String =
    getString(R.string.date_day_month, date.dayOfMonth, getStringArray(R.array.months_short)[date.monthValue - 1])

/** `Октябрь 2026`. */
fun Resources.formatMonthYear(year: Int, month: Int): String =
    getString(R.string.month_year, getStringArray(R.array.months_full_standalone)[month - 1], year)

@Composable
@ReadOnlyComposable
fun formatDuration(duration: Duration): String {
    val (hours, minutes) = TimeFormat.hoursMinutes(duration)
    return if (hours == 0L) {
        stringResource(R.string.duration_minutes, minutes)
    } else {
        stringResource(R.string.duration_hours_minutes, hours.toInt(), minutes)
    }
}

@Composable
fun formatShortDate(date: LocalDate): String = stringResource(
    R.string.date_day_month,
    date.dayOfMonth,
    stringArrayResource(R.array.months_short)[
        date.monthValue -
            1,
    ],
)

@Composable
fun formatMonthYear(year: Int, month: Int): String =
    stringResource(R.string.month_year, stringArrayResource(R.array.months_full_standalone)[month - 1], year)
