package app.cinderblock.domain

enum class ExerciseCategory { Chest, Back, Shoulders, Arms, Legs, Core, Cardio }

data class ExercisePreset(
    val name: String,
    val category: ExerciseCategory,
    val defaultSets: Int,
    val reps: String? = null,
    val duration: String? = null,
    val muscles: List<String>,
    val notes: List<String>,
)

val EXERCISE_PRESETS: List<ExercisePreset> = listOf(
    preset("Chest Press Machine", ExerciseCategory.Chest, 3, "8-12", listOf("Chest", "Front delts", "Triceps"), listOf("Primary chest movement", "Shoulder blades pinned back", "Control the lowering phase")),
    preset("Pectoral Fly Machine", ExerciseCategory.Chest, 3, "10-15", listOf("Chest"), listOf("Slight bend in elbows", "Focus on chest contraction", "Use controlled tempo")),
    preset("Incline Chest Press", ExerciseCategory.Chest, 3, "8-12", listOf("Upper chest", "Front delts", "Triceps"), listOf("Target upper chest", "Keep shoulder blades retracted")),
    preset("Dumbbell Chest Press", ExerciseCategory.Chest, 3, "8-12", listOf("Chest", "Front delts", "Triceps"), listOf("Full range of motion", "Control the weight")),
    preset("Push-Ups", ExerciseCategory.Chest, 3, "Near failure", listOf("Chest", "Front delts", "Triceps", "Core"), listOf("Use full range of motion", "Chest close to floor", "Maintain straight body line")),
    preset("Lat Pulldown", ExerciseCategory.Back, 3, "8-12", listOf("Lats", "Biceps", "Rear delts"), listOf("Pull to upper chest", "Lead with elbows", "Avoid leaning back excessively")),
    preset("Row Machine", ExerciseCategory.Back, 3, "8-12", listOf("Lats", "Rhomboids", "Rear delts", "Biceps", "Mid traps"), listOf("Pull elbows toward hips", "Avoid shrugging shoulders", "Control the return")),
    preset("Cable Row", ExerciseCategory.Back, 3, "8-12", listOf("Lats", "Rhomboids", "Biceps"), listOf("Pull to lower chest", "Keep torso stable", "Squeeze shoulder blades")),
    preset("Pull-Ups", ExerciseCategory.Back, 3, "5-10", listOf("Lats", "Biceps", "Upper back"), listOf("Full range of motion", "Control the descent", "Use assistance if needed")),
    preset("T-Bar Row", ExerciseCategory.Back, 3, "8-12", listOf("Mid back", "Lats", "Rhomboids"), listOf("Pull to chest", "Keep core tight")),
    preset("Rear Delt Machine", ExerciseCategory.Back, 2, "12-15", listOf("Rear delts", "Rhomboids"), listOf("Move through full range", "Keep shoulders down")),
    preset("Shoulder Press Machine", ExerciseCategory.Shoulders, 3, "8-12", listOf("Front delts", "Side delts", "Triceps"), listOf("Keep core tight", "Press overhead smoothly", "Avoid locking out aggressively")),
    preset("Dumbbell Shoulder Press", ExerciseCategory.Shoulders, 3, "8-12", listOf("Front delts", "Side delts", "Triceps"), listOf("Press overhead", "Control the weight")),
    preset("Lateral Raises", ExerciseCategory.Shoulders, 3, "12-15", listOf("Side delts"), listOf("Slight bend in elbows", "Raise to shoulder height", "Control the descent")),
    preset("Front Raises", ExerciseCategory.Shoulders, 3, "12-15", listOf("Front delts"), listOf("Controlled movement", "Raise to eye level")),
    preset("Upright Rows", ExerciseCategory.Shoulders, 3, "10-12", listOf("Side delts", "Traps"), listOf("Pull elbows high", "Keep bar close to body")),
    preset("Bicep Curl Machine", ExerciseCategory.Arms, 3, "10-15", listOf("Biceps"), listOf("Isolation curl", "Control both phases", "Avoid swinging")),
    preset("Dumbbell Bicep Curls", ExerciseCategory.Arms, 3, "10-12", listOf("Biceps"), listOf("Keep elbows stationary", "Full range of motion")),
    preset("Hammer Curls", ExerciseCategory.Arms, 3, "10-12", listOf("Biceps", "Forearms"), listOf("Neutral grip", "Control the weight")),
    preset("Tricep Pushdowns", ExerciseCategory.Arms, 3, "10-15", listOf("Triceps"), listOf("Keep elbows at sides", "Full extension", "Squeeze at bottom")),
    preset("Overhead Tricep Extension", ExerciseCategory.Arms, 3, "10-12", listOf("Triceps"), listOf("Keep elbows close", "Full stretch at top")),
    preset("Dips", ExerciseCategory.Arms, 3, "8-12", listOf("Triceps", "Chest", "Front delts"), listOf("Lean forward for chest", "Stay upright for triceps", "Control the descent")),
    preset("Leg Press", ExerciseCategory.Legs, 4, "8-12", listOf("Quads", "Glutes"), listOf("Lower under control", "Push through full foot", "Avoid bouncing")),
    preset("Leg Extension", ExerciseCategory.Legs, 3, "12-15", listOf("Quads"), listOf("Avoid swinging", "Pause at top", "Control descent")),
    preset("Seated Leg Curl", ExerciseCategory.Legs, 3, "10-15", listOf("Hamstrings"), listOf("Pause briefly at contraction", "Control lowering phase")),
    preset("Romanian Deadlift", ExerciseCategory.Legs, 3, "8-12", listOf("Hamstrings", "Glutes", "Lower back", "Core"), listOf("Push hips backwards", "Maintain neutral spine", "Keep bar close to legs", "Feel stretch in hamstrings")),
    preset("Walking Lunges", ExerciseCategory.Legs, 3, "10-12 per leg", listOf("Quads", "Glutes", "Hamstrings"), listOf("Take controlled steps", "Keep torso upright")),
    preset("Bulgarian Split Squats", ExerciseCategory.Legs, 3, "8-12 per leg", listOf("Quads", "Glutes"), listOf("Back foot elevated", "Front knee tracking over toes")),
    preset("Leg Press Calf Raises", ExerciseCategory.Legs, 3, "12-20", listOf("Calves"), listOf("Use full stretch", "Pause at top")),
    preset("Calf Raises", ExerciseCategory.Legs, 3, "12-20", listOf("Calves"), listOf("Use full stretch", "Pause at top")),
    preset("Hip Thrusts", ExerciseCategory.Legs, 3, "10-15", listOf("Glutes", "Hamstrings"), listOf("Squeeze glutes at top", "Keep chin tucked")),
    preset("Plank", ExerciseCategory.Core, 3, null, listOf("Abs", "Obliques", "Lower back"), listOf("Brace core", "Do not let hips sag"), duration = "30-60 seconds"),
    preset("Crunches or Leg Raises", ExerciseCategory.Core, 3, "10-20", listOf("Abs", "Hip flexors"), listOf("Control movement", "Avoid using momentum")),
    preset("Russian Twists", ExerciseCategory.Core, 3, "15-20 per side", listOf("Obliques", "Abs"), listOf("Rotate torso", "Keep core engaged")),
    preset("Dead Bug", ExerciseCategory.Core, 3, "10-12 per side", listOf("Abs", "Core"), listOf("Keep lower back pressed to floor", "Move slowly and controlled")),
    preset("Cable Woodchops", ExerciseCategory.Core, 3, "12-15 per side", listOf("Obliques", "Core"), listOf("Rotate through core", "Keep arms relatively straight")),
    preset("Hanging Knee Raises", ExerciseCategory.Core, 3, "10-15", listOf("Lower abs", "Hip flexors"), listOf("Avoid swinging", "Control the movement")),
)

private fun preset(
    name: String,
    category: ExerciseCategory,
    sets: Int,
    reps: String?,
    muscles: List<String>,
    notes: List<String>,
    duration: String? = null,
) = ExercisePreset(name, category, sets, reps, duration, muscles, notes)

fun categoryLabel(category: ExerciseCategory): String = when (category) {
    ExerciseCategory.Chest -> "Chest"
    ExerciseCategory.Back -> "Back"
    ExerciseCategory.Shoulders -> "Shoulders"
    ExerciseCategory.Arms -> "Arms"
    ExerciseCategory.Legs -> "Legs"
    ExerciseCategory.Core -> "Core"
    ExerciseCategory.Cardio -> "Cardio"
}

fun searchPresets(query: String, category: ExerciseCategory?): List<ExercisePreset> {
    val filtered = if (category == null) EXERCISE_PRESETS else EXERCISE_PRESETS.filter { it.category == category }
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return filtered
    return filtered.filter { preset ->
        preset.name.lowercase().contains(needle) ||
            preset.muscles.any { it.lowercase().contains(needle) } ||
            preset.category.name.lowercase().contains(needle)
    }
}

fun presetToExercise(preset: ExercisePreset, sets: Int = preset.defaultSets): ProgramExercise =
    ProgramExercise(
        name = preset.name,
        sets = sets,
        reps = preset.reps,
        duration = preset.duration,
        seconds = if (preset.duration != null) "" else null,
        muscles = preset.muscles,
        notes = preset.notes,
    )
