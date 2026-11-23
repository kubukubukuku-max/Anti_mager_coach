package com.example.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.WorkoutSession
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE
}

class HealthConnectManager(private val context: Context) {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    fun checkAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }
    }

    fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    fun createInstallOrUpdateIntent(): Intent {
        val uriString = "market://details?id=$PROVIDER_PACKAGE_NAME&url=healthconnect%3A%2F%2Fonboarding"
        return Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse(uriString)
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
    }

    private fun getClientOrNull(): HealthConnectClient? {
        return if (checkAvailability() == HealthConnectAvailability.AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = getClientOrNull() ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reads [ExerciseSessionRecord] entries from the last 14 days via Android Health Connect,
     * enriches each session with aggregated distance (km) and average heart rate (bpm) when available,
     * and maps them to [WorkoutSession] with `record.metadata.id` as `externalId`.
     */
    suspend fun syncLast14DaysWorkouts(): Result<List<WorkoutSession>> {
        val client = getClientOrNull()
            ?: return Result.failure(
                IllegalStateException("Health Connect tidak tersedia di perangkat ini.")
            )

        return try {
            val endTime = Instant.now()
            val startTime = endTime.minus(14, ChronoUnit.DAYS)

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            val mappedSessions = response.records.mapNotNull { record ->
                val sportType = mapExerciseTypeToSportType(record.exerciseType) ?: return@mapNotNull null
                val durationMin = Duration.between(record.startTime, record.endTime)
                    .toMinutes()
                    .toInt()
                    .coerceAtLeast(1)

                val distanceKm = readDistanceKmForSession(client, record.startTime, record.endTime)
                val avgHr = readAvgHeartRateForSession(client, record.startTime, record.endTime)

                val externalId = record.metadata.id.takeIf { it.isNotBlank() }
                    ?: "hc_${record.startTime.toEpochMilli()}_${record.exerciseType}"

                val startedAtIso = record.startTime
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                val notes = listOfNotNull(
                    record.title?.trim()?.takeIf { it.isNotEmpty() },
                    record.notes?.trim()?.takeIf { it.isNotEmpty() }
                ).distinct().joinToString(" — ").takeIf { it.isNotEmpty() }

                WorkoutSession(
                    id = "hc_$externalId",
                    externalId = externalId,
                    sportType = sportType,
                    source = SessionSource.HEALTH,
                    startedAtIso = startedAtIso,
                    durationMin = durationMin,
                    distanceKm = distanceKm,
                    avgHr = avgHr,
                    rpe = DEFAULT_SYNCED_RPE,
                    notes = notes
                )
            }

            Result.success(mappedSessions)
        } catch (e: SecurityException) {
            Result.failure(
                SecurityException("Izin Health Connect belum diberikan.", e)
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Gagal menyinkronkan data Health Connect: ${e.localizedMessage ?: "Kesalahan tidak diketahui"}",
                    e
                )
            )
        }
    }

    private suspend fun readDistanceKmForSession(
        client: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ): Double? {
        return runCatching {
            val aggregate = client.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            aggregate[DistanceRecord.DISTANCE_TOTAL]
                ?.inKilometers
                ?.takeIf { it > 0.05 }
        }.getOrNull()
    }

    private suspend fun readAvgHeartRateForSession(
        client: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ): Int? {
        return runCatching {
            val aggregate = client.aggregate(
                AggregateRequest(
                    metrics = setOf(HeartRateRecord.BPM_AVG),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            aggregate[HeartRateRecord.BPM_AVG]
                ?.toInt()
                ?.takeIf { it in 35..230 }
        }.getOrNull()
    }

    companion object {
        const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
        const val DEFAULT_SYNCED_RPE = 5

        fun mapExerciseTypeToSportType(exerciseType: Int): SportType? {
            return when (exerciseType) {
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> SportType.RUNNING

                ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> SportType.BADMINTON

                ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> SportType.WALKING

                ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
                ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
                ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
                ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> SportType.DUMBBELL

                ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING,
                ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
                ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> SportType.REST

                else -> SportType.WALKING
            }
        }
    }
}
