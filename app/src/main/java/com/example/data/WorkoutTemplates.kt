package com.example.data

import com.example.model.DumbbellItem

data class WorkoutTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val items: List<DumbbellItem>
)

object WorkoutTemplates {

    val allTemplates: List<WorkoutTemplate> = listOf(
        WorkoutTemplate(
            id = "bodyweight_basic",
            title = "Bodyweight Dasar (Tanpa Alat)",
            subtitle = "5 gerakan ramah pemula, tanpa pull-up",
            badge = "Tanpa Alat",
            items = listOf(
                DumbbellItem(
                    exerciseName = "Push-up (atau Knee Push-up)",
                    weightKg = 0.0,
                    sets = 3,
                    reps = 8
                ),
                DumbbellItem(
                    exerciseName = "Plank",
                    weightKg = 0.0,
                    sets = 3,
                    reps = 30
                ),
                DumbbellItem(
                    exerciseName = "Bodyweight Squat",
                    weightKg = 0.0,
                    sets = 3,
                    reps = 12
                ),
                DumbbellItem(
                    exerciseName = "Glute Bridge",
                    weightKg = 0.0,
                    sets = 3,
                    reps = 12
                ),
                DumbbellItem(
                    exerciseName = "Bird-Dog",
                    weightKg = 0.0,
                    sets = 2,
                    reps = 10
                )
            )
        ),
        WorkoutTemplate(
            id = "upper_core_dumbbell",
            title = "Upper & Core (Dumbbell)",
            subtitle = "Fokus tubuh atas & stabilitas inti",
            badge = "Dumbbell",
            items = listOf(
                DumbbellItem(
                    exerciseName = "Dumbbell Floor Press",
                    weightKg = 5.0,
                    sets = 3,
                    reps = 10
                ),
                DumbbellItem(
                    exerciseName = "Bent-Over Row",
                    weightKg = 5.0,
                    sets = 3,
                    reps = 10
                ),
                DumbbellItem(
                    exerciseName = "Overhead Press",
                    weightKg = 4.0,
                    sets = 3,
                    reps = 10
                ),
                DumbbellItem(
                    exerciseName = "Russian Twist",
                    weightKg = 3.0,
                    sets = 3,
                    reps = 14
                )
            )
        ),
        WorkoutTemplate(
            id = "lower_stability_dumbbell",
            title = "Lower & Stability (Dumbbell)",
            subtitle = "Kaki kuat, pinggul & keseimbangan stabil",
            badge = "Dumbbell",
            items = listOf(
                DumbbellItem(
                    exerciseName = "Goblet Squat",
                    weightKg = 6.0,
                    sets = 3,
                    reps = 10
                ),
                DumbbellItem(
                    exerciseName = "Romanian Deadlift",
                    weightKg = 6.0,
                    sets = 3,
                    reps = 10
                ),
                DumbbellItem(
                    exerciseName = "Reverse Lunge",
                    weightKg = 4.0,
                    sets = 3,
                    reps = 10
                ),
                DumbbellItem(
                    exerciseName = "Calf Raise",
                    weightKg = 5.0,
                    sets = 3,
                    reps = 15
                )
            )
        ),
        WorkoutTemplate(
            id = "full_body_santai_dumbbell",
            title = "Full Body Santai (Dumbbell)",
            subtitle = "4 gerakan seimbang seluruh tubuh",
            badge = "Dumbbell",
            items = listOf(
                DumbbellItem(
                    exerciseName = "Goblet Squat",
                    weightKg = 5.0,
                    sets = 3,
                    reps = 10
                ),
                DumbbellItem(
                    exerciseName = "Dumbbell Row",
                    weightKg = 5.0,
                    sets = 3,
                    reps = 10
                ),
                DumbbellItem(
                    exerciseName = "Dumbbell Floor Press",
                    weightKg = 5.0,
                    sets = 3,
                    reps = 10
                ),
                DumbbellItem(
                    exerciseName = "Bicep Curl",
                    weightKg = 4.0,
                    sets = 3,
                    reps = 10
                )
            )
        )
    )
}
