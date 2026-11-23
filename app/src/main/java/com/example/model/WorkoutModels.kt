package com.example.model

import com.squareup.moshi.JsonClass

enum class SportType(val displayName: String) {
    RUNNING("Lari"),
    BADMINTON("Badminton"),
    DUMBBELL("Beban / Bodyweight"),
    WALKING("Jalan Santai"),
    REST("Istirahat Aktif");

    val isActiveMovement: Boolean
        get() = this != REST

    val isStrength: Boolean
        get() = this == DUMBBELL

    val isCardio: Boolean
        get() = this == RUNNING || this == BADMINTON || this == WALKING
}

enum class SessionSource(val displayName: String) {
    MANUAL("Manual"),
    STRAVA("Strava"),
    HEALTH("Health")
}

enum class TodayStatus(
    val label: String,
    val shortHint: String
) {
    FRESH("Senggang & Fit", "Energi dan waktu cukup untuk gerak nyaman"),
    TIRED("Agak Capek", "Utamakan lembut dan ramah di badan"),
    BUSY("Waktu Mepet", "Micro-workout ringkas yang tetap bikin lega")
}

@JsonClass(generateAdapter = true)
data class DumbbellItem(
    val exerciseName: String,
    val weightKg: Double,
    val sets: Int,
    val reps: Int
)

@JsonClass(generateAdapter = true)
data class WorkoutSession(
    val id: String,
    val externalId: String? = null,
    val sportType: SportType,
    val source: SessionSource,
    val startedAtIso: String,
    val durationMin: Int,
    val distanceKm: Double? = null,
    val avgHr: Int? = null,
    val rpe: Int,
    val notes: String? = null,
    val dumbbellItems: List<DumbbellItem> = emptyList()
)

data class WorkoutInsights(
    val daysSinceLastWorkout: Int? = null,
    val consecutiveActiveDays: Int = 0,
    val lastSession: WorkoutSession? = null,
    val isLowerBodyFatigued: Boolean = false,
    val lowerBodyFatigueSource: WorkoutSession? = null,
    val weeklyCardioCount: Int = 0,
    val weeklyStrengthCount: Int = 0,
    val hasRunningThisWeek: Boolean = false,
    val readyForProgression: Boolean = false
)

data class SuggestionOption(
    val badge: String,
    val title: String,
    val sportType: SportType,
    val estimatedDurationMin: Int,
    val durationLabel: String,
    val reason: String,
    val relaxedGuidance: String,
    val defaultRpe: Int,
    val suggestedDumbbellItems: List<DumbbellItem> = emptyList()
)

data class DailyRecommendation(
    val idealOption: SuggestionOption,
    val lightOption: SuggestionOption,
    val contextSummary: String,
    val insightBadge: String = "",
    val insights: WorkoutInsights = WorkoutInsights(),
    val isAiGenerated: Boolean = false
)

data class DayRhythmIndicator(
    val dayLabel: String,
    val dateKey: String,
    val isActive: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val totalMinutes: Int = 0,
    val strengthMinutes: Int = 0,
    val cardioMinutes: Int = 0,
    val sessionCount: Int = 0,
    val shortDateLabel: String = ""
)

data class WeeklyRhythmSummary(
    val activeDaysCount: Int,
    val totalActiveMinutes: Int,
    val strengthSessionCount: Int,
    val cardioSessionCount: Int,
    val weekDays: List<DayRhythmIndicator>,
    val last7Days: List<DayRhythmIndicator> = weekDays
) {
    val peakDayMinutes: Int
        get() = last7Days.maxOfOrNull { it.totalMinutes }
            ?.coerceAtLeast(weekDays.maxOfOrNull { it.totalMinutes } ?: 0)
            ?: 0

    val averageActiveDayMinutes: Int
        get() = if (activeDaysCount > 0) totalActiveMinutes / activeDaysCount else 0

    val consistencyPercent: Int
        get() = ((activeDaysCount.coerceIn(0, 7) * 100) + 3) / 7
}

object ActiveWorkoutTimerMath {
    /**
     * Computes accurate elapsed workout time from wall-clock timestamps minus accumulated pauses.
     * Immune to screen-off suspension or background throttling.
     */
    fun calculateElapsedMillis(
        startedAtEpochMs: Long,
        accumulatedPausedMs: Long,
        pausedAtEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Long {
        val effectiveNow = pausedAtEpochMs ?: nowEpochMs
        return (effectiveNow - startedAtEpochMs - accumulatedPausedMs).coerceAtLeast(0L)
    }

    /**
     * Converts elapsed milliseconds to logged minutes, rounding to nearest minute with a minimum of 1 min.
     */
    fun calculateDurationMinutes(elapsedMillis: Long): Int {
        val safeElapsed = elapsedMillis.coerceAtLeast(0L)
        return ((safeElapsed + 30_000L) / 60_000L).toInt().coerceIn(1, 1440)
    }

    /**
     * Formats elapsed time as MM:SS, or HH:MM:SS once duration reaches 1 hour or more.
     */
    fun formatStopwatchTime(elapsedMillis: Long): String {
        val totalSeconds = elapsedMillis.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}

data class ActiveWorkoutState(
    val title: String,
    val sportType: SportType,
    val source: SessionSource = SessionSource.MANUAL,
    val targetDurationMin: Int? = null,
    val targetDurationLabel: String? = null,
    val isFromIdealSuggestion: Boolean? = null,
    val defaultRpe: Int = 5,
    val initialDistanceKm: Double? = null,
    val initialAvgHr: Int? = null,
    val initialNotes: String? = null,
    val dumbbellItems: List<DumbbellItem> = emptyList(),
    val completedExerciseIndices: Set<Int> = emptySet(),
    val startedAtEpochMs: Long,
    val accumulatedPausedMs: Long = 0L,
    val pausedAtEpochMs: Long? = null
) {
    val isPaused: Boolean
        get() = pausedAtEpochMs != null

    fun elapsedMillis(nowEpochMs: Long = System.currentTimeMillis()): Long {
        return ActiveWorkoutTimerMath.calculateElapsedMillis(
            startedAtEpochMs = startedAtEpochMs,
            accumulatedPausedMs = accumulatedPausedMs,
            pausedAtEpochMs = pausedAtEpochMs,
            nowEpochMs = nowEpochMs
        )
    }

    fun pause(nowEpochMs: Long = System.currentTimeMillis()): ActiveWorkoutState {
        if (pausedAtEpochMs != null) return this
        return copy(pausedAtEpochMs = nowEpochMs)
    }

    fun resume(nowEpochMs: Long = System.currentTimeMillis()): ActiveWorkoutState {
        val pausedAt = pausedAtEpochMs ?: return this
        val pauseDuration = (nowEpochMs - pausedAt).coerceAtLeast(0L)
        return copy(
            accumulatedPausedMs = accumulatedPausedMs + pauseDuration,
            pausedAtEpochMs = null
        )
    }

    fun togglePause(nowEpochMs: Long = System.currentTimeMillis()): ActiveWorkoutState {
        return if (isPaused) resume(nowEpochMs) else pause(nowEpochMs)
    }

    fun toggleExerciseCompleted(index: Int): ActiveWorkoutState {
        val updated = if (index in completedExerciseIndices) {
            completedExerciseIndices - index
        } else {
            completedExerciseIndices + index
        }
        return copy(completedExerciseIndices = updated)
    }
}

