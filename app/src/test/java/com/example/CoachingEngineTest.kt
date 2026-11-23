package com.example

import com.example.domain.CoachingEngine
import com.example.domain.DateTimeHelper
import com.example.model.DumbbellItem
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.TodayStatus
import com.example.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingEngineTest {

    private val nowMillis = 1_763_820_000_000L // Fixed timestamp for deterministic tests
    private val oneDayMillis = 24L * 60L * 60L * 1000L

    @Test
    fun freshEmptyHistory_returnsStarterBodyweightAndWalk() {
        val rec = CoachingEngine.generateDailyRecommendation(
            sessions = emptyList(),
            todayStatus = TodayStatus.FRESH,
            nowMillis = nowMillis
        )
        assertEquals("Ritme Utama", rec.idealOption.badge)
        assertEquals("Anti-Mager", rec.lightOption.badge)
        assertEquals(20, rec.idealOption.estimatedDurationMin)
        assertEquals(15, rec.lightOption.estimatedDurationMin)
        assertEquals(SportType.WALKING, rec.lightOption.sportType)
        assertTrue(rec.insightBadge.isNotBlank())
    }

    @Test
    fun freshConsecutive4Days_recommendsActiveRecoveryAndTotalRest() {
        val sessions = (1..4).map { dayOffset ->
            WorkoutSession(
                id = "s_$dayOffset",
                sportType = if (dayOffset % 2 == 0) SportType.DUMBBELL else SportType.WALKING,
                source = SessionSource.MANUAL,
                startedAtIso = DateTimeHelper.formatNowIso(nowMillis - dayOffset * oneDayMillis),
                durationMin = 25,
                rpe = 5
            )
        }

        val insights = CoachingEngine.analyzeInsights(sessions, nowMillis)
        assertEquals(4, insights.consecutiveActiveDays)

        val rec = CoachingEngine.generateDailyRecommendation(sessions, TodayStatus.FRESH, nowMillis)
        assertEquals(SportType.WALKING, rec.idealOption.sportType)
        assertEquals(SportType.REST, rec.lightOption.sportType)
        assertTrue(rec.idealOption.reason.contains("4 hari"))
    }

    @Test
    fun freshLowerBodyFatiguedFromBadmintonOrSquat_recommendsUpperAndCore() {
        val yesterdayIso = DateTimeHelper.formatNowIso(nowMillis - oneDayMillis)
        val badmintonHeavy = listOf(
            WorkoutSession(
                id = "bad1",
                sportType = SportType.BADMINTON,
                source = SessionSource.STRAVA,
                startedAtIso = yesterdayIso,
                durationMin = 60,
                avgHr = 158,
                rpe = 8
            )
        )

        val rec = CoachingEngine.generateDailyRecommendation(badmintonHeavy, TodayStatus.FRESH, nowMillis)
        assertTrue(rec.insights.isLowerBodyFatigued)
        assertEquals(SportType.DUMBBELL, rec.idealOption.sportType)
        assertTrue(rec.idealOption.title.contains("Upper & Core", ignoreCase = true))
        assertTrue(rec.idealOption.reason.contains("Badminton", ignoreCase = true))
        assertTrue(rec.insightBadge.contains("Badminton", ignoreCase = true))

        // Also test lower body fatigue triggered by DUMBBELL session with Squat
        val legDumbbell = listOf(
            WorkoutSession(
                id = "leg1",
                sportType = SportType.DUMBBELL,
                source = SessionSource.MANUAL,
                startedAtIso = yesterdayIso,
                durationMin = 25,
                rpe = 5,
                dumbbellItems = listOf(DumbbellItem("Goblet Squat", 8.0, 3, 10))
            )
        )
        val legInsights = CoachingEngine.analyzeInsights(legDumbbell, nowMillis)
        assertTrue(legInsights.isLowerBodyFatigued)
    }

    @Test
    fun freshCardioHeavyLowStrengthAndReadyForProgression_suggestsFullBodyWithProgressionNote() {
        val sessions = listOf(
            // 2 cardio sessions this week (not taxing lower body within 48h -> 3 & 5 days ago)
            WorkoutSession("c1", sportType = SportType.WALKING, source = SessionSource.MANUAL, startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 3 * oneDayMillis), durationMin = 25, rpe = 3),
            WorkoutSession("c2", sportType = SportType.RUNNING, source = SessionSource.STRAVA, startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 5 * oneDayMillis), durationMin = 30, rpe = 4),
            // 2 past dumbbell sessions with RPE <= 5 (1 within 7 days, 1 older than 7 days -> weeklyStrengthCount == 1 < 2, readyForProgression == true)
            WorkoutSession("d1", sportType = SportType.DUMBBELL, source = SessionSource.MANUAL, startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 4 * oneDayMillis), durationMin = 25, rpe = 4),
            WorkoutSession("d2", sportType = SportType.DUMBBELL, source = SessionSource.MANUAL, startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 9 * oneDayMillis), durationMin = 25, rpe = 5)
        )

        val rec = CoachingEngine.generateDailyRecommendation(sessions, TodayStatus.FRESH, nowMillis)
        assertEquals(1, rec.insights.weeklyStrengthCount)
        assertEquals(2, rec.insights.weeklyCardioCount)
        assertTrue(rec.insights.readyForProgression)
        assertEquals(SportType.DUMBBELL, rec.idealOption.sportType)
        assertTrue(rec.idealOption.title.contains("Full Body", ignoreCase = true))
        assertTrue(rec.idealOption.relaxedGuidance.contains("naikkan beban", ignoreCase = true))
        assertTrue(rec.insightBadge.contains("siap naik level", ignoreCase = true))
    }

    @Test
    fun freshNoRunningThisWeek_suggestsEasyZone2TalkTestRun() {
        val sessions = listOf(
            WorkoutSession("d1", sportType = SportType.DUMBBELL, source = SessionSource.MANUAL, startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 3 * oneDayMillis), durationMin = 25, rpe = 5)
        )

        val rec = CoachingEngine.generateDailyRecommendation(sessions, TodayStatus.FRESH, nowMillis)
        assertFalse(rec.insights.hasRunningThisWeek)
        assertEquals(SportType.RUNNING, rec.idealOption.sportType)
        assertTrue(rec.idealOption.title.contains("Zona 2", ignoreCase = true))
        assertTrue(rec.idealOption.relaxedGuidance.contains("talk test", ignoreCase = true))
    }

    @Test
    fun tiredAfterHeavySession_recommendsDynamicStretchWalkAndTotalRest() {
        val sessions = listOf(
            WorkoutSession("r1", sportType = SportType.RUNNING, source = SessionSource.STRAVA, startedAtIso = DateTimeHelper.formatNowIso(nowMillis - oneDayMillis), durationMin = 50, rpe = 8)
        )

        val rec = CoachingEngine.generateDailyRecommendation(sessions, TodayStatus.TIRED, nowMillis)
        assertEquals(SportType.WALKING, rec.idealOption.sportType)
        assertEquals(SportType.REST, rec.lightOption.sportType)
        assertTrue(rec.idealOption.reason.contains("Lari", ignoreCase = true))
        assertTrue(rec.idealOption.reason.contains("RPE 8"))
    }

    @Test
    fun tiredAfterInactiveGap_recommendsRefreshingWalkAndBedroomStretch() {
        val sessions = listOf(
            WorkoutSession("w1", sportType = SportType.WALKING, source = SessionSource.MANUAL, startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 3 * oneDayMillis), durationMin = 20, rpe = 3)
        )

        val rec = CoachingEngine.generateDailyRecommendation(sessions, TodayStatus.TIRED, nowMillis)
        assertEquals(3, rec.insights.daysSinceLastWorkout)
        assertEquals(SportType.WALKING, rec.idealOption.sportType)
        assertTrue(rec.idealOption.reason.contains("3 hari"))
        assertEquals(SportType.REST, rec.lightOption.sportType)
    }

    @Test
    fun busyAdaptsMicroWorkoutBasedOnWeeklyStrengthCount() {
        // Low strength (< 2) -> Quick Bodyweight / Dumbbell Circuit + Plank & Push-up Singkat
        val lowStrength = listOf(
            WorkoutSession("w1", sportType = SportType.WALKING, source = SessionSource.MANUAL, startedAtIso = DateTimeHelper.formatNowIso(nowMillis - oneDayMillis), durationMin = 20, rpe = 3)
        )
        val recLow = CoachingEngine.generateDailyRecommendation(lowStrength, TodayStatus.BUSY, nowMillis)
        assertEquals(SportType.DUMBBELL, recLow.idealOption.sportType)
        assertTrue(recLow.idealOption.estimatedDurationMin in 12..15)
        assertTrue(recLow.lightOption.title.contains("Plank & Push-up", ignoreCase = true))

        // Strength >= 2 -> Brisk Walk / Short Jog + 10 min walk
        val highStrength = listOf(
            WorkoutSession("d1", sportType = SportType.DUMBBELL, source = SessionSource.MANUAL, startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 3 * oneDayMillis), durationMin = 25, rpe = 5),
            WorkoutSession("d2", sportType = SportType.DUMBBELL, source = SessionSource.MANUAL, startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 5 * oneDayMillis), durationMin = 25, rpe = 5)
        )
        val recHigh = CoachingEngine.generateDailyRecommendation(highStrength, TodayStatus.BUSY, nowMillis)
        assertEquals(SportType.RUNNING, recHigh.idealOption.sportType)
        assertEquals(15, recHigh.idealOption.estimatedDurationMin)
        assertEquals(SportType.WALKING, recHigh.lightOption.sportType)
        assertEquals(10, recHigh.lightOption.estimatedDurationMin)
    }

    @Test
    fun weeklyRhythmSummary_countsActiveDaysMinutesAndSplitAccurately() {
        val todayIso = DateTimeHelper.formatNowIso(nowMillis)
        val yesterdayIso = DateTimeHelper.formatNowIso(nowMillis - oneDayMillis)
        val sessions = listOf(
            WorkoutSession("a", sportType = SportType.RUNNING, source = SessionSource.STRAVA, startedAtIso = todayIso, durationMin = 25, rpe = 4),
            WorkoutSession("b", sportType = SportType.DUMBBELL, source = SessionSource.MANUAL, startedAtIso = yesterdayIso, durationMin = 20, rpe = 5),
            WorkoutSession("c", sportType = SportType.REST, source = SessionSource.MANUAL, startedAtIso = todayIso, durationMin = 10, rpe = 1)
        )

        val summary = CoachingEngine.computeWeeklyRhythm(sessions, nowMillis)
        assertEquals(2, summary.activeDaysCount)
        assertEquals(45, summary.totalActiveMinutes)
        assertEquals(1, summary.strengthSessionCount)
        assertEquals(1, summary.cardioSessionCount)
        assertEquals(7, summary.weekDays.size)
        assertEquals("Sen", summary.weekDays.first().dayLabel)
        assertEquals("Min", summary.weekDays.last().dayLabel)
        assertEquals(25, summary.peakDayMinutes)
        assertEquals(22, summary.averageActiveDayMinutes)

        val todayKey = DateTimeHelper.toLocalDateKey(nowMillis)
        val todayIndicator = summary.weekDays.first { it.dateKey == todayKey }
        assertEquals(25, todayIndicator.totalMinutes)
        assertEquals(25, todayIndicator.cardioMinutes)
        assertEquals(0, todayIndicator.strengthMinutes)
        assertEquals(1, todayIndicator.sessionCount)

        // Rolling last7Days window must end on today and include yesterday regardless of calendar week boundary
        assertEquals(7, summary.last7Days.size)
        assertTrue(summary.last7Days.last().isToday)
        assertEquals(25, summary.last7Days.last().totalMinutes)
        assertEquals(20, summary.last7Days[5].totalMinutes)
        assertEquals(20, summary.last7Days[5].strengthMinutes)
        assertEquals(29, summary.consistencyPercent) // 2/7 rounded -> 29%
    }
}
