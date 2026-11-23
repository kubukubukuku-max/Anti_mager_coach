package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class GreetingPeriod {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT
}

object DateTimeHelper {

    private val isoOutputPattern = "yyyy-MM-dd'T'HH:mm:ssXXX"
    private val parsePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd"
    )

    private val indonesianMonths = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    private val indonesianDaysShort = arrayOf(
        "Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"
    )

    fun formatNowIso(nowMillis: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat(isoOutputPattern, Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date(nowMillis))
    }

    fun parseIsoToMillis(isoString: String): Long? {
        val trimmed = isoString.trim()
        if (trimmed.isEmpty()) return null
        for (pattern in parsePatterns) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.US)
                formatter.isLenient = false
                if (pattern.endsWith("'Z'")) {
                    formatter.timeZone = TimeZone.getTimeZone("UTC")
                } else {
                    formatter.timeZone = TimeZone.getDefault()
                }
                val parsed = formatter.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    fun toLocalDateKey(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    fun startOfLocalDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Returns calendar-day difference between [earlierMillis] and [laterMillis] in local timezone.
     * 0 = same day, 1 = yesterday, 2 = two days ago, etc.
     */
    fun calendarDaysBetween(earlierMillis: Long, laterMillis: Long): Int {
        val startEarlier = startOfLocalDayMillis(earlierMillis)
        val startLater = startOfLocalDayMillis(laterMillis)
        val diffMillis = startLater - startEarlier
        val millisPerDay = 24L * 60L * 60L * 1000L
        return Math.round(diffMillis.toDouble() / millisPerDay.toDouble()).toInt()
    }

    /**
     * Returns 7 day pairs (label, dateKey, isToday, isFuture) from Monday ("Sen") to Sunday ("Min")
     * for the current week containing [nowMillis].
     */
    fun currentWeekMonToSun(nowMillis: Long = System.currentTimeMillis()): List<WeekDaySlot> {
        val todayKey = toLocalDateKey(nowMillis)
        val todayStart = startOfLocalDayMillis(nowMillis)

        val cal = Calendar.getInstance()
        cal.timeInMillis = todayStart
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2, ..., Sat=7
        val offsetFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -offsetFromMonday)

        val labels = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
        return labels.map { label ->
            val slotMillis = cal.timeInMillis
            val dateKey = toLocalDateKey(slotMillis)
            val shortDate = "${cal.get(Calendar.DAY_OF_MONTH)} ${indonesianMonths[cal.get(Calendar.MONTH)]}"
            val slot = WeekDaySlot(
                dayLabel = label,
                dateKey = dateKey,
                isToday = dateKey == todayKey,
                isFuture = slotMillis > todayStart,
                shortDateLabel = shortDate
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
            slot
        }
    }

    /**
     * Returns the chronological rolling 7-day window ending today (today - 6 days .. today)
     * in the device's local timezone so every active Firestore session in the last 7 days appears.
     */
    fun rollingLast7Days(nowMillis: Long = System.currentTimeMillis()): List<WeekDaySlot> {
        val todayKey = toLocalDateKey(nowMillis)
        val todayStart = startOfLocalDayMillis(nowMillis)
        val cal = Calendar.getInstance()
        cal.timeInMillis = todayStart
        cal.add(Calendar.DAY_OF_MONTH, -6)

        return (0..6).map {
            val slotMillis = cal.timeInMillis
            val dateKey = toLocalDateKey(slotMillis)
            val dayLabel = indonesianDaysShort[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val shortDate = "${cal.get(Calendar.DAY_OF_MONTH)} ${indonesianMonths[cal.get(Calendar.MONTH)]}"
            val slot = WeekDaySlot(
                dayLabel = dayLabel,
                dateKey = dateKey,
                isToday = dateKey == todayKey,
                isFuture = false,
                shortDateLabel = shortDate
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
            slot
        }
    }

    fun getGreetingPeriod(nowMillis: Long = System.currentTimeMillis()): GreetingPeriod {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 4..10 -> GreetingPeriod.MORNING
            in 11..14 -> GreetingPeriod.AFTERNOON
            in 15..17 -> GreetingPeriod.EVENING
            else -> GreetingPeriod.NIGHT
        }
    }

    fun formatSessionDisplayDate(isoString: String, nowMillis: Long = System.currentTimeMillis()): String {
        val millis = parseIsoToMillis(isoString) ?: return isoString
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val timeText = String.format(Locale.US, "%02d:%02d", hour, minute)

        val dayDiff = calendarDaysBetween(millis, nowMillis)
        return when (dayDiff) {
            0 -> "Hari ini, $timeText"
            1 -> "Kemarin, $timeText"
            else -> {
                val dayName = indonesianDaysShort[cal.get(Calendar.DAY_OF_WEEK) - 1]
                val dayNum = cal.get(Calendar.DAY_OF_MONTH)
                val monthName = indonesianMonths[cal.get(Calendar.MONTH)]
                "$dayName, $dayNum $monthName • $timeText"
            }
        }
    }
}

data class WeekDaySlot(
    val dayLabel: String,
    val dateKey: String,
    val isToday: Boolean,
    val isFuture: Boolean,
    val shortDateLabel: String = ""
)
