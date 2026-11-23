package com.example.data

import com.example.AiProvider
import com.example.domain.CoachingEngine
import com.example.domain.DateTimeHelper
import com.example.model.DumbbellItem
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.TodayStatus
import com.example.model.WorkoutSession
import com.example.ui.theme.AppThemeMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class BackupPreferences(
    val themeMode: AppThemeMode = AppThemeMode.WARM_CREAM,
    val reminderSettings: ReminderSettings = ReminderSettings(),
    val selectedAiProvider: AiProvider = AiProvider.GROQ,
    val todayStatus: TodayStatus = TodayStatus.FRESH
)

data class BackupProfileSummary(
    val displayName: String? = null,
    val activeDaysLast7: Int = 0,
    val consecutiveActiveDays: Int = 0,
    val totalActiveMinutesLast7: Int = 0,
    val weeklyGoalHint: String = "Ritme santai & konsisten tanpa tekanan"
)

data class AppBackupSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appName: String = "Anti-Mager Coach",
    val appVersion: String = "",
    val exportedAtIso: String = DateTimeHelper.formatNowIso(),
    val profileSummary: BackupProfileSummary = BackupProfileSummary(),
    val preferences: BackupPreferences = BackupPreferences(),
    val workoutSessions: List<WorkoutSession> = emptyList(),
    val hasPreferencesSection: Boolean = true
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * Codec backup & pemulihan JSON lengkap untuk Anti-Mager Coach (berbasis Moshi).
 * - Menggabungkan riwayat latihan, ringkasan profil/streak/target, dan preferensi (tema, pengingat, penyedia AI).
 * - Secara tegas TIDAK pernah menyertakan API Key AI (Groq / Gemini) demi keamanan.
 * - Mendukung format backup lengkap (JSON Object) maupun format ekspor lama (JSON Array WorkoutSession).
 */
object AppBackupCodec {

    private val FILE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    ).indent("  ")

    private val listAdapter = moshi.adapter<List<Any?>>(
        Types.newParameterizedType(List::class.java, Any::class.java)
    )

    fun defaultBackupFileName(date: LocalDate = LocalDate.now()): String {
        return "anti-mager-backup-${date.format(FILE_DATE_FORMATTER)}.json"
    }

    fun createSnapshot(
        sessions: List<WorkoutSession>,
        themeMode: AppThemeMode,
        reminderSettings: ReminderSettings,
        selectedAiProvider: AiProvider,
        todayStatus: TodayStatus,
        displayName: String? = null,
        appVersion: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): AppBackupSnapshot {
        val rhythm = CoachingEngine.computeWeeklyRhythm(sessions, nowMillis)
        val insights = CoachingEngine.analyzeInsights(sessions, nowMillis)
        val sortedSessions = sessions.sortedByDescending {
            DateTimeHelper.parseIsoToMillis(it.startedAtIso) ?: 0L
        }
        return AppBackupSnapshot(
            schemaVersion = AppBackupSnapshot.CURRENT_SCHEMA_VERSION,
            appName = "Anti-Mager Coach",
            appVersion = appVersion,
            exportedAtIso = DateTimeHelper.formatNowIso(nowMillis),
            profileSummary = BackupProfileSummary(
                displayName = displayName?.takeIf { it.isNotBlank() },
                activeDaysLast7 = rhythm.activeDaysCount,
                consecutiveActiveDays = insights.consecutiveActiveDays,
                totalActiveMinutesLast7 = rhythm.totalActiveMinutes
            ),
            preferences = BackupPreferences(
                themeMode = themeMode,
                reminderSettings = reminderSettings,
                selectedAiProvider = selectedAiProvider,
                todayStatus = todayStatus
            ),
            workoutSessions = sortedSessions,
            hasPreferencesSection = true
        )
    }

    fun encodeToJson(snapshot: AppBackupSnapshot): String {
        val profileMap = linkedMapOf<String, Any?>().apply {
            if (!snapshot.profileSummary.displayName.isNullOrBlank()) {
                put("displayName", snapshot.profileSummary.displayName)
            }
            put("activeDaysLast7", snapshot.profileSummary.activeDaysLast7)
            put("consecutiveActiveDays", snapshot.profileSummary.consecutiveActiveDays)
            put("totalActiveMinutesLast7", snapshot.profileSummary.totalActiveMinutesLast7)
            put("weeklyGoalHint", snapshot.profileSummary.weeklyGoalHint)
        }

        val reminderMap = linkedMapOf<String, Any?>(
            "enabled" to snapshot.preferences.reminderSettings.enabled,
            "hour" to snapshot.preferences.reminderSettings.hour.coerceIn(0, 23),
            "minute" to snapshot.preferences.reminderSettings.minute.coerceIn(0, 59)
        )

        val prefsMap = linkedMapOf<String, Any?>(
            "themeMode" to snapshot.preferences.themeMode.storageKey,
            "todayStatus" to snapshot.preferences.todayStatus.name,
            "selectedAiProvider" to snapshot.preferences.selectedAiProvider.name,
            "dailyReminder" to reminderMap
        )

        val sessionsList = snapshot.workoutSessions.map { encodeSessionMap(it) }

        val root = linkedMapOf<String, Any?>().apply {
            put("schemaVersion", snapshot.schemaVersion)
            put("appName", snapshot.appName)
            if (snapshot.appVersion.isNotBlank()) {
                put("appVersion", snapshot.appVersion)
            }
            put("exportedAtIso", snapshot.exportedAtIso)
            put("profileAndTargets", profileMap)
            put("preferences", prefsMap)
            put("workoutSessions", sessionsList)
        }

        return mapAdapter.toJson(root)
    }

    fun decodeFromJson(rawJson: String): Result<AppBackupSnapshot> {
        return try {
            val trimmed = rawJson.trim().removePrefix("\uFEFF")
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("File backup kosong."))
            }

            when {
                trimmed.startsWith("{") -> parseFullBackupObject(trimmed)
                trimmed.startsWith("[") -> parseLegacySessionsArray(trimmed)
                else -> Result.failure(
                    IllegalArgumentException(
                        "Format file tidak dikenali. Pilih file backup .json dari Anti-Mager Coach."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException(
                    "File JSON rusak atau format tidak sesuai: ${e.message?.take(80) ?: "tidak dapat dibaca"}."
                )
            )
        }
    }

    private fun parseFullBackupObject(trimmed: String): Result<AppBackupSnapshot> {
        val root = mapAdapter.fromJson(trimmed)
            ?: return Result.failure(IllegalArgumentException("File JSON tidak valid."))

        val rawSessions = (root["workoutSessions"] as? List<*>)
            ?: (root["sessions"] as? List<*>)
            ?: return Result.failure(
                IllegalArgumentException(
                    "File JSON tidak memiliki data 'workoutSessions'. Pastikan memilih file backup Anti-Mager Coach."
                )
            )

        val sessions = parseSessionsList(rawSessions)
        val prefsMap = root["preferences"] as? Map<*, *>
        val hasPrefs = prefsMap != null

        val preferences = if (prefsMap != null) {
            val reminderMap = prefsMap["dailyReminder"] as? Map<*, *>
            val reminder = if (reminderMap != null) {
                ReminderSettings(
                    enabled = (reminderMap["enabled"] as? Boolean) ?: false,
                    hour = numberToInt(reminderMap["hour"], 7).coerceIn(0, 23),
                    minute = numberToInt(reminderMap["minute"], 30).coerceIn(0, 59)
                )
            } else {
                ReminderSettings()
            }
            val theme = AppThemeMode.fromStorageKey(prefsMap["themeMode"] as? String)
            val provider = AiProvider.fromStoredName(prefsMap["selectedAiProvider"] as? String)
            val statusName = (prefsMap["todayStatus"] as? String).orEmpty()
            val status = TodayStatus.entries.firstOrNull {
                it.name.equals(statusName, ignoreCase = true)
            } ?: TodayStatus.FRESH

            BackupPreferences(
                themeMode = theme,
                reminderSettings = reminder,
                selectedAiProvider = provider,
                todayStatus = status
            )
        } else {
            BackupPreferences()
        }

        val profileMap = root["profileAndTargets"] as? Map<*, *>
        val profile = BackupProfileSummary(
            displayName = (profileMap?.get("displayName") as? String)?.trim()?.takeIf { it.isNotEmpty() },
            activeDaysLast7 = numberToInt(profileMap?.get("activeDaysLast7"), 0).coerceAtLeast(0),
            consecutiveActiveDays = numberToInt(profileMap?.get("consecutiveActiveDays"), 0).coerceAtLeast(0),
            totalActiveMinutesLast7 = numberToInt(profileMap?.get("totalActiveMinutesLast7"), 0).coerceAtLeast(0),
            weeklyGoalHint = (profileMap?.get("weeklyGoalHint") as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Ritme santai & konsisten tanpa tekanan"
        )

        return Result.success(
            AppBackupSnapshot(
                schemaVersion = numberToInt(root["schemaVersion"], AppBackupSnapshot.CURRENT_SCHEMA_VERSION),
                appName = (root["appName"] as? String)?.ifBlank { "Anti-Mager Coach" } ?: "Anti-Mager Coach",
                appVersion = (root["appVersion"] as? String).orEmpty(),
                exportedAtIso = (root["exportedAtIso"] as? String).orEmpty(),
                profileSummary = profile,
                preferences = preferences,
                workoutSessions = sessions,
                hasPreferencesSection = hasPrefs
            )
        )
    }

    private fun parseLegacySessionsArray(trimmed: String): Result<AppBackupSnapshot> {
        val rawList = listAdapter.fromJson(trimmed)
            ?: return Result.failure(IllegalArgumentException("Format array JSON tidak valid."))
        val sessions = parseSessionsList(rawList)
        if (rawList.isNotEmpty() && sessions.isEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "Isi array JSON tidak memiliki sesi latihan yang valid."
                )
            )
        }
        return Result.success(
            AppBackupSnapshot(
                schemaVersion = 0,
                exportedAtIso = "",
                workoutSessions = sessions,
                hasPreferencesSection = false
            )
        )
    }

    private fun encodeSessionMap(session: WorkoutSession): Map<String, Any?> {
        return linkedMapOf<String, Any?>().apply {
            put("id", session.id)
            if (!session.externalId.isNullOrBlank()) {
                put("externalId", session.externalId)
            }
            put("sportType", session.sportType.name)
            put("source", session.source.name)
            put("startedAtIso", session.startedAtIso)
            put("durationMin", session.durationMin)
            if (session.distanceKm != null) {
                put("distanceKm", session.distanceKm)
            }
            if (session.avgHr != null) {
                put("avgHr", session.avgHr)
            }
            put("rpe", session.rpe)
            if (!session.notes.isNullOrBlank()) {
                put("notes", session.notes)
            }
            if (session.dumbbellItems.isNotEmpty()) {
                put(
                    "dumbbellItems",
                    session.dumbbellItems.map { item ->
                        linkedMapOf<String, Any?>(
                            "exerciseName" to item.exerciseName,
                            "weightKg" to item.weightKg,
                            "sets" to item.sets,
                            "reps" to item.reps
                        )
                    }
                )
            }
        }
    }

    private fun parseSessionsList(rawList: List<*>): List<WorkoutSession> {
        val result = mutableListOf<WorkoutSession>()
        val seenIds = mutableSetOf<String>()

        for (entry in rawList) {
            val obj = entry as? Map<*, *> ?: continue
            val durationMin = numberToInt(obj["durationMin"], 0)
            if (durationMin <= 0) continue

            val rawId = (obj["id"] as? String)?.trim().orEmpty().ifEmpty { UUID.randomUUID().toString() }
            val uniqueId = if (seenIds.add(rawId)) {
                rawId
            } else {
                UUID.randomUUID().toString().also { seenIds.add(it) }
            }

            val sportRaw = (obj["sportType"] as? String)?.trim().orEmpty()
            val sportType = SportType.entries.firstOrNull {
                it.name.equals(sportRaw, ignoreCase = true)
            } ?: SportType.WALKING

            val sourceRaw = (obj["source"] as? String)?.trim().orEmpty()
            val source = SessionSource.entries.firstOrNull {
                it.name.equals(sourceRaw, ignoreCase = true)
            } ?: SessionSource.MANUAL

            val startedAtIso = (obj["startedAtIso"] as? String)?.trim().orEmpty().ifEmpty {
                DateTimeHelper.formatNowIso()
            }

            val externalId = (obj["externalId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

            val distanceKm = numberToDoubleOrNull(obj["distanceKm"])
                ?.takeIf { !it.isNaN() && it > 0.0 && it <= 500.0 }

            val avgHr = numberToIntOrNull(obj["avgHr"])?.takeIf { it in 30..240 }
            val rpe = numberToInt(obj["rpe"], 5).coerceIn(1, 10)
            val notes = (obj["notes"] as? String)?.trim()?.take(500)?.takeIf { it.isNotEmpty() }

            val dumbbellItems = mutableListOf<DumbbellItem>()
            val rawItems = obj["dumbbellItems"] as? List<*>
            if (rawItems != null) {
                for (itemEntry in rawItems.take(30)) {
                    val itemObj = itemEntry as? Map<*, *> ?: continue
                    val name = (itemObj["exerciseName"] as? String)?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    val weight = (numberToDoubleOrNull(itemObj["weightKg"]) ?: 0.0)
                        .let { if (it.isNaN() || it < 0.0) 0.0 else it.coerceAtMost(500.0) }
                    val sets = numberToInt(itemObj["sets"], 1).coerceIn(1, 50)
                    val reps = numberToInt(itemObj["reps"], 1).coerceIn(1, 500)
                    dumbbellItems.add(
                        DumbbellItem(
                            exerciseName = name,
                            weightKg = weight,
                            sets = sets,
                            reps = reps
                        )
                    )
                }
            }

            result.add(
                WorkoutSession(
                    id = uniqueId,
                    externalId = externalId,
                    sportType = sportType,
                    source = source,
                    startedAtIso = startedAtIso,
                    durationMin = durationMin.coerceIn(1, 1440),
                    distanceKm = distanceKm,
                    avgHr = avgHr,
                    rpe = rpe,
                    notes = notes,
                    dumbbellItems = dumbbellItems
                )
            )
        }

        return result.sortedByDescending { DateTimeHelper.parseIsoToMillis(it.startedAtIso) ?: 0L }
    }

    private fun numberToInt(value: Any?, defaultValue: Int): Int {
        return numberToIntOrNull(value) ?: defaultValue
    }

    private fun numberToIntOrNull(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }

    private fun numberToDoubleOrNull(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }
}
