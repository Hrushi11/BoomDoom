package app.boomdoom.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DayKeyTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(ist).toInstant()

    @Test fun beforeResetBelongsToPreviousDay() {
        assertEquals("2026-11-20", DayKey.of(at("2026-11-21T01:30:00"), ist))
        assertEquals("2026-11-20", DayKey.of(at("2026-11-21T03:59:59"), ist))
    }

    @Test fun atOrAfterResetIsToday() {
        assertEquals("2026-11-21", DayKey.of(at("2026-11-21T04:00:00"), ist))
        assertEquals("2026-11-21", DayKey.of(at("2026-11-21T23:59:00"), ist))
    }
}
