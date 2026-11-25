package utils

import java.time.DayOfWeek
import java.time.LocalDateTime

fun calculateParkFee(entryTime: LocalDateTime = LocalDateTime.now()): Long {
    val isWeekend = entryTime.dayOfWeek == DayOfWeek.SATURDAY || entryTime.dayOfWeek == DayOfWeek.SUNDAY
    if (isWeekend) {
        return 100_000L
    }

    return if (entryTime.hour in 6..17) {
        50_000L
    } else {
        80_000L
    }
}