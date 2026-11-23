package com.example

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.example.health.HealthConnectManager
import com.example.model.SportType
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectMappingTest {

    @Test
    fun mapExerciseTypeToSportType_mapsCoreWorkoutsAccurately() {
        assertEquals(
            SportType.RUNNING,
            HealthConnectManager.mapExerciseTypeToSportType(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING)
        )
        assertEquals(
            SportType.RUNNING,
            HealthConnectManager.mapExerciseTypeToSportType(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)
        )
        assertEquals(
            SportType.BADMINTON,
            HealthConnectManager.mapExerciseTypeToSportType(ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON)
        )
        assertEquals(
            SportType.WALKING,
            HealthConnectManager.mapExerciseTypeToSportType(ExerciseSessionRecord.EXERCISE_TYPE_WALKING)
        )
        assertEquals(
            SportType.DUMBBELL,
            HealthConnectManager.mapExerciseTypeToSportType(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING)
        )
        assertEquals(
            SportType.DUMBBELL,
            HealthConnectManager.mapExerciseTypeToSportType(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING)
        )
        assertEquals(
            SportType.DUMBBELL,
            HealthConnectManager.mapExerciseTypeToSportType(ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS)
        )
        assertEquals(
            SportType.WALKING,
            HealthConnectManager.mapExerciseTypeToSportType(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT)
        )
    }
}
