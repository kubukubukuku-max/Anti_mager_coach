package com.example

import com.example.data.ExerciseGuides
import com.example.data.WorkoutTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseGuidesAndTemplatesTest {

    @Test
    fun exerciseGuides_containsAllRequiredBodyweightAndDumbbellExercisesWithoutPullUp() {
        assertEquals(5, ExerciseGuides.bodyweightBasic.size)
        assertEquals(10, ExerciseGuides.dumbbellGuides.size)
        assertEquals(15, ExerciseGuides.allGuides.size)

        // Ensure no pull-up in bodyweight basic
        assertTrue(
            ExerciseGuides.bodyweightBasic.none {
                it.name.contains("pull-up", ignoreCase = true) ||
                    it.name.contains("pull up", ignoreCase = true)
            }
        )

        // Every guide has 3-4 steps, safety tip, and non-blank YouTube query
        ExerciseGuides.allGuides.forEach { guide ->
            assertTrue("Steps should be 3..4 for ${guide.name}", guide.steps.size in 3..4)
            assertTrue(guide.targetMuscle.isNotBlank())
            assertTrue(guide.safetyTip.isNotBlank())
            assertTrue(guide.youtubeQuery.isNotBlank())
        }
    }

    @Test
    fun workoutTemplates_containsFourPresetsWithExpectedBodyweightValues() {
        assertEquals(4, WorkoutTemplates.allTemplates.size)

        val bodyweight = WorkoutTemplates.allTemplates.first { it.id == "bodyweight_basic" }
        assertEquals(5, bodyweight.items.size)
        assertTrue(bodyweight.items.all { it.weightKg == 0.0 })

        val plank = bodyweight.items.first { it.exerciseName == "Plank" }
        assertEquals(3, plank.sets)
        assertEquals(30, plank.reps)
        assertTrue(ExerciseGuides.isTimedExercise(plank.exerciseName))
        assertFalse(ExerciseGuides.isTimedExercise("Goblet Squat"))

        // Every template movement resolves cleanly to a guide
        WorkoutTemplates.allTemplates.flatMap { it.items }.forEach { item ->
            val guide = ExerciseGuides.findByName(item.exerciseName)
            assertTrue(guide.steps.size in 3..4)
            assertTrue(guide.youtubeQuery.isNotBlank())
        }
    }
}
