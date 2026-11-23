package com.example

import com.example.data.AppBackupCodec
import com.example.data.ReminderSettings
import com.example.model.DumbbellItem
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.TodayStatus
import com.example.model.WorkoutSession
import com.example.ui.theme.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AppBackupCodecTest {

    @Test
    fun defaultBackupFilenameUsesIsoDateFormat() {
        val fileName = AppBackupCodec.defaultBackupFileName(LocalDate.of(2026, 11, 23))
        assertEquals("anti-mager-backup-2026-11-23.json", fileName)
    }

    @Test
    fun fullBackupRoundTripPreservesSessionsAndPreferencesWithoutApiKeys() {
        val session = WorkoutSession(
            id = "session-1",
            externalId = "hc-123",
            sportType = SportType.DUMBBELL,
            source = SessionSource.MANUAL,
            startedAtIso = "2026-11-22T07:30:00Z",
            durationMin = 25,
            distanceKm = null,
            avgHr = 128,
            rpe = 6,
            notes = "Upper body santai",
            dumbbellItems = listOf(
                DumbbellItem(exerciseName = "Dumbbell Row", weightKg = 6.0, sets = 3, reps = 10)
            )
        )

        val snapshot = AppBackupCodec.createSnapshot(
            sessions = listOf(session),
            themeMode = AppThemeMode.DEEP_NIGHT,
            reminderSettings = ReminderSettings(enabled = true, hour = 18, minute = 15),
            selectedAiProvider = AiProvider.GROQ,
            todayStatus = TodayStatus.BUSY,
            displayName = "Budi",
            appVersion = "1.1.2"
        )

        val json = AppBackupCodec.encodeToJson(snapshot)

        // Ensure no API keys or sensitive credential fields exist in backup JSON
        assertFalse(json.contains("apiKey", ignoreCase = true))
        assertFalse(json.contains("groq_api_key", ignoreCase = true))
        assertFalse(json.contains("gemini_api_key", ignoreCase = true))
        assertFalse(json.contains("gsk_"))

        val decoded = AppBackupCodec.decodeFromJson(json)
        assertTrue(decoded.isSuccess)

        val restored = decoded.getOrThrow()
        assertTrue(restored.hasPreferencesSection)
        assertEquals(1, restored.workoutSessions.size)
        assertEquals("session-1", restored.workoutSessions.first().id)
        assertEquals("Upper body santai", restored.workoutSessions.first().notes)
        assertEquals(1, restored.workoutSessions.first().dumbbellItems.size)
        assertEquals(AppThemeMode.DEEP_NIGHT, restored.preferences.themeMode)
        assertEquals(true, restored.preferences.reminderSettings.enabled)
        assertEquals(18, restored.preferences.reminderSettings.hour)
        assertEquals(15, restored.preferences.reminderSettings.minute)
        assertEquals(AiProvider.GROQ, restored.preferences.selectedAiProvider)
        assertEquals(TodayStatus.BUSY, restored.preferences.todayStatus)
        assertEquals("Budi", restored.profileSummary.displayName)
    }

    @Test
    fun legacyWorkoutArrayJsonStillDecodesCleanly() {
        val legacyJson = """
            [
              {
                "id": "legacy-1",
                "sportType": "RUNNING",
                "source": "MANUAL",
                "startedAtIso": "2026-11-20T06:00:00Z",
                "durationMin": 30,
                "rpe": 5
              }
            ]
        """.trimIndent()

        val result = AppBackupCodec.decodeFromJson(legacyJson)
        assertTrue(result.isSuccess)
        val restored = result.getOrThrow()
        assertFalse(restored.hasPreferencesSection)
        assertEquals(1, restored.workoutSessions.size)
        assertEquals(SportType.RUNNING, restored.workoutSessions.first().sportType)
    }

    @Test
    fun invalidOrCorruptedJsonFailsSafelyWithoutCrashing() {
        assertTrue(AppBackupCodec.decodeFromJson("").isFailure)
        assertTrue(AppBackupCodec.decodeFromJson("{broken json").isFailure)
        assertTrue(AppBackupCodec.decodeFromJson("{\"hello\":\"world\"}").isFailure)
    }
}
