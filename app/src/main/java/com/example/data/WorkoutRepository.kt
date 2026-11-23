package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.DateTimeHelper
import com.example.model.WorkoutSession
import com.example.ui.theme.AppThemeMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.workoutDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anti_mager_coach_prefs"
)

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 30
)

class WorkoutRepository(private val context: Context) {

    private val sessionsJsonKey = stringPreferencesKey("workout_sessions_json")
    private val themeModeKey = stringPreferencesKey("app_theme_mode")
    private val reminderEnabledKey = booleanPreferencesKey("daily_reminder_enabled")
    private val reminderHourKey = intPreferencesKey("daily_reminder_hour")
    private val reminderMinuteKey = intPreferencesKey("daily_reminder_minute")

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val sessionListType = Types.newParameterizedType(
        List::class.java,
        WorkoutSession::class.java
    )
    private val sessionListAdapter = moshi.adapter<List<WorkoutSession>>(sessionListType)
        .indent("  ")

    private val safePreferencesFlow: Flow<Preferences> = context.workoutDataStore.data
        .catch { emit(emptyPreferences()) }

    val themeModeFlow: Flow<AppThemeMode> = safePreferencesFlow
        .map { preferences ->
            AppThemeMode.fromStorageKey(preferences[themeModeKey])
        }

    val reminderSettingsFlow: Flow<ReminderSettings> = safePreferencesFlow
        .map { preferences ->
            ReminderSettings(
                enabled = preferences[reminderEnabledKey] ?: false,
                hour = (preferences[reminderHourKey] ?: 7).coerceIn(0, 23),
                minute = (preferences[reminderMinuteKey] ?: 30).coerceIn(0, 59)
            )
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.workoutDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.storageKey
        }
    }

    suspend fun saveReminderSettings(settings: ReminderSettings) {
        context.workoutDataStore.edit { prefs ->
            prefs[reminderEnabledKey] = settings.enabled
            prefs[reminderHourKey] = settings.hour.coerceIn(0, 23)
            prefs[reminderMinuteKey] = settings.minute.coerceIn(0, 59)
        }
    }

    /**
     * Emits all locally stored sessions sorted newest-first.
     * Starts strictly empty on fresh install (never inserts dummy data).
     */
    val sessionsFlow: Flow<List<WorkoutSession>> = safePreferencesFlow
        .map { preferences ->
            val rawJson = preferences[sessionsJsonKey]
            if (rawJson.isNullOrBlank()) {
                emptyList()
            } else {
                parseSessionsSafely(rawJson)
            }
        }

    suspend fun addSession(session: WorkoutSession) {
        context.workoutDataStore.edit { prefs ->
            val current = parseSessionsSafely(prefs[sessionsJsonKey])
            val updated = (listOf(session) + current)
                .distinctBy { it.id }
                .sortedByDescending { DateTimeHelper.parseIsoToMillis(it.startedAtIso) ?: 0L }
            prefs[sessionsJsonKey] = sessionListAdapter.toJson(updated)
        }
    }

    suspend fun updateSession(updated: WorkoutSession) {
        context.workoutDataStore.edit { prefs ->
            val current = parseSessionsSafely(prefs[sessionsJsonKey])
            val replaced = current.map { existing ->
                if (existing.id == updated.id) updated else existing
            }.sortedByDescending { DateTimeHelper.parseIsoToMillis(it.startedAtIso) ?: 0L }
            prefs[sessionsJsonKey] = sessionListAdapter.toJson(replaced)
        }
    }

    suspend fun deleteSession(sessionId: String) {
        context.workoutDataStore.edit { prefs ->
            val current = parseSessionsSafely(prefs[sessionsJsonKey])
            val updated = current.filterNot { it.id == sessionId }
            prefs[sessionsJsonKey] = sessionListAdapter.toJson(updated)
        }
    }

    /**
     * Inserts newly synced sessions and updates existing ones matched by `externalId`
     * (preserving user-edited RPE/notes if already present). Returns count of newly inserted sessions.
     */
    suspend fun upsertSyncedSessions(syncedSessions: List<WorkoutSession>): Int {
        if (syncedSessions.isEmpty()) return 0
        var newInsertedCount = 0

        context.workoutDataStore.edit { prefs ->
            val current = parseSessionsSafely(prefs[sessionsJsonKey]).toMutableList()

            for (incoming in syncedSessions) {
                val extId = incoming.externalId?.takeIf { it.isNotBlank() }
                val existingIndex = current.indexOfFirst { existing ->
                    (extId != null && existing.externalId == extId) || existing.id == incoming.id
                }

                if (existingIndex >= 0) {
                    val existing = current[existingIndex]
                    current[existingIndex] = existing.copy(
                        durationMin = incoming.durationMin,
                        distanceKm = incoming.distanceKm ?: existing.distanceKm,
                        avgHr = incoming.avgHr ?: existing.avgHr,
                        notes = existing.notes ?: incoming.notes
                    )
                } else {
                    current.add(incoming)
                    newInsertedCount++
                }
            }

            val sorted = current.sortedByDescending {
                DateTimeHelper.parseIsoToMillis(it.startedAtIso) ?: 0L
            }
            prefs[sessionsJsonKey] = sessionListAdapter.toJson(sorted)
        }

        return newInsertedCount
    }

    suspend fun clearAllSessions() {
        context.workoutDataStore.edit { prefs ->
            prefs.remove(sessionsJsonKey)
        }
    }

    suspend fun replaceAllSessions(sessions: List<WorkoutSession>) {
        val sorted = sessions
            .distinctBy { it.id }
            .sortedByDescending { DateTimeHelper.parseIsoToMillis(it.startedAtIso) ?: 0L }
        context.workoutDataStore.edit { prefs ->
            if (sorted.isEmpty()) {
                prefs.remove(sessionsJsonKey)
            } else {
                prefs[sessionsJsonKey] = sessionListAdapter.toJson(sorted)
            }
        }
    }

    suspend fun exportSessionsJson(): String {
        val current = sessionsFlow.first()
        return sessionListAdapter.toJson(current)
    }

    fun serializeSessionsJson(sessions: List<WorkoutSession>): String {
        return sessionListAdapter.toJson(sessions)
    }

    fun parseSessionsJson(rawJson: String): Result<List<WorkoutSession>> {
        return try {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("JSON masih kosong."))
            }
            val parsed = sessionListAdapter.fromJson(trimmed)
                ?: return Result.failure(IllegalArgumentException("Format JSON tidak valid."))
            val sanitized = parsed
                .filter { it.durationMin > 0 }
                .map { item ->
                    item.copy(
                        rpe = item.rpe.coerceIn(1, 10),
                        durationMin = item.durationMin.coerceIn(1, 1440)
                    )
                }
            Result.success(sanitized)
        } catch (_: Exception) {
            Result.failure(
                IllegalArgumentException(
                    "Gagal membaca JSON. Pastikan format array WorkoutSession sudah benar."
                )
            )
        }
    }

    /**
     * Imports a JSON array of [WorkoutSession] and merges with existing sessions,
     * skipping duplicates by `id` or non-blank `externalId`.
     */
    suspend fun importSessionsFromJson(rawJson: String): Result<Int> {
        return try {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("JSON masih kosong."))
            }
            val parsed = sessionListAdapter.fromJson(trimmed)
                ?: return Result.failure(IllegalArgumentException("Format JSON tidak valid."))

            var addedCount = 0
            context.workoutDataStore.edit { prefs ->
                val current = parseSessionsSafely(prefs[sessionsJsonKey])
                val existingIds = current.map { it.id }.toMutableSet()
                val existingExternalIds = current.mapNotNull { it.externalId?.takeIf(String::isNotBlank) }
                    .toMutableSet()

                val newItems = mutableListOf<WorkoutSession>()
                for (item in parsed) {
                    val hasIdDup = item.id in existingIds
                    val extId = item.externalId?.takeIf(String::isNotBlank)
                    val hasExtDup = extId != null && extId in existingExternalIds
                    if (!hasIdDup && !hasExtDup && item.durationMin > 0) {
                        val sanitized = item.copy(
                            rpe = item.rpe.coerceIn(1, 10),
                            durationMin = item.durationMin.coerceAtLeast(1)
                        )
                        newItems.add(sanitized)
                        existingIds.add(sanitized.id)
                        if (extId != null) existingExternalIds.add(extId)
                    }
                }
                addedCount = newItems.size
                if (newItems.isNotEmpty()) {
                    val merged = (newItems + current)
                        .sortedByDescending { DateTimeHelper.parseIsoToMillis(it.startedAtIso) ?: 0L }
                    prefs[sessionsJsonKey] = sessionListAdapter.toJson(merged)
                }
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException(
                    "Gagal membaca JSON. Pastikan format array WorkoutSession sudah benar."
                )
            )
        }
    }

    private fun parseSessionsSafely(rawJson: String?): List<WorkoutSession> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            sessionListAdapter.fromJson(rawJson)
                ?.sortedByDescending { DateTimeHelper.parseIsoToMillis(it.startedAtIso) ?: 0L }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
