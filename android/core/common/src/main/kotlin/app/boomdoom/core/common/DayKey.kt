package app.boomdoom.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Tech Spec §4.2: a "day" runs from [resetTime] (default 04:00 local) to the next [resetTime].
 * Scrolling at 1:30 AM still counts towards the previous day.
 */
object DayKey {
    val DEFAULT_RESET: LocalTime = LocalTime.of(4, 0)

    fun of(at: Instant, zone: ZoneId, resetTime: LocalTime = DEFAULT_RESET): String =
        dateOf(at, zone, resetTime).toString()

    fun dateOf(at: Instant, zone: ZoneId, resetTime: LocalTime = DEFAULT_RESET): LocalDate {
        val local = at.atZone(zone).toLocalDateTime()
        return local.minusHours(resetTime.hour.toLong()).minusMinutes(resetTime.minute.toLong()).toLocalDate()
    }
}
