package com.example

import com.example.model.ActiveWorkoutState
import com.example.model.ActiveWorkoutTimerMath
import com.example.model.SportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWorkoutTimerTest {

    @Test
    fun elapsedMillis_remainsAccurateAcrossScreenOffAndMultiplePauses() {
        val t0 = 1_000_000L
        var state = ActiveWorkoutState(
            title = "Bodyweight Dasar",
            sportType = SportType.DUMBBELL,
            startedAtEpochMs = t0
        )

        // Screen off for 2 minutes (120_000 ms) — wall-clock diff stays exact
        assertEquals(120_000L, state.elapsedMillis(t0 + 120_000L))

        // Pause at t0 + 120s
        state = state.pause(t0 + 120_000L)
        assertTrue(state.isPaused)

        // Wait 5 minutes while paused — elapsed must stay frozen at 120s
        assertEquals(120_000L, state.elapsedMillis(t0 + 420_000L))

        // Resume at t0 + 420s (paused for 300s)
        state = state.resume(t0 + 420_000L)
        assertFalse(state.isPaused)
        assertEquals(300_000L, state.accumulatedPausedMs)

        // Run another 90s -> total active elapsed should be 210s (3m 30s)
        assertEquals(210_000L, state.elapsedMillis(t0 + 510_000L))

        // Pause a second time for 60s and resume
        state = state.togglePause(t0 + 510_000L)
        state = state.togglePause(t0 + 570_000L)
        assertEquals(360_000L, state.accumulatedPausedMs)

        // After another 30s -> total active elapsed should be 240s (4m 00s)
        assertEquals(240_000L, state.elapsedMillis(t0 + 600_000L))
    }

    @Test
    fun formatStopwatchTime_usesMmSsUnderOneHourAndHhMmSsAtOrOverOneHour() {
        assertEquals("00:00", ActiveWorkoutTimerMath.formatStopwatchTime(0L))
        assertEquals("00:09", ActiveWorkoutTimerMath.formatStopwatchTime(9_400L))
        assertEquals("14:35", ActiveWorkoutTimerMath.formatStopwatchTime((14 * 60 + 35) * 1000L))
        assertEquals("59:59", ActiveWorkoutTimerMath.formatStopwatchTime((59 * 60 + 59) * 1000L))
        assertEquals("01:00:00", ActiveWorkoutTimerMath.formatStopwatchTime(3_600_000L))
        assertEquals("01:23:45", ActiveWorkoutTimerMath.formatStopwatchTime((3600 + 23 * 60 + 45) * 1000L))
    }

    @Test
    fun calculateDurationMinutes_enforcesMinimumOneMinuteAndRoundsNearest() {
        assertEquals(1, ActiveWorkoutTimerMath.calculateDurationMinutes(0L))
        assertEquals(1, ActiveWorkoutTimerMath.calculateDurationMinutes(12_000L))
        assertEquals(1, ActiveWorkoutTimerMath.calculateDurationMinutes(89_999L))
        assertEquals(2, ActiveWorkoutTimerMath.calculateDurationMinutes(90_000L))
        assertEquals(25, ActiveWorkoutTimerMath.calculateDurationMinutes(25 * 60_000L))
    }

    @Test
    fun toggleExerciseCompleted_togglesChecklistIndicesIndependently() {
        var state = ActiveWorkoutState(
            title = "Upper & Core",
            sportType = SportType.DUMBBELL,
            startedAtEpochMs = 10_000L
        )
        state = state.toggleExerciseCompleted(0)
        state = state.toggleExerciseCompleted(2)
        assertEquals(setOf(0, 2), state.completedExerciseIndices)

        state = state.toggleExerciseCompleted(0)
        assertEquals(setOf(2), state.completedExerciseIndices)
    }
}
