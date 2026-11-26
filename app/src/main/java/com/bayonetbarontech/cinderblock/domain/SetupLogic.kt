package com.bayonetbarontech.cinderblock.domain

const val ATHLETE_PROFILE_MARKER = "Athlete profile:"
const val SETUP_ASSUMED_DAYS = 4
const val SETUP_QUESTION_COUNT = 6

fun landingRoute(profile: TrainingProfile): String = if (profile.completed) "home" else "setup"

fun hasSetupAnswers(profile: TrainingProfile): Boolean =
    profile.daysPerWeek != null ||
        profile.age != null ||
        !profile.sex.isNullOrBlank() ||
        !profile.experience.isNullOrBlank() ||
        !profile.equipment.isNullOrBlank() ||
        !profile.notes.isNullOrBlank()

fun mergeAthleteSummary(current: String, brief: String): String {
    if (brief.isBlank()) return current
    if (current.contains(ATHLETE_PROFILE_MARKER)) return current
    return listOf(brief.trim(), current.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
}

fun setupBrief(profile: TrainingProfile): String {
    val days = profile.daysPerWeek
    val age = profile.age
    val sex = profile.sex
    val experience = profile.experience
    val equipment = profile.equipment
    val notes = profile.notes?.trim().orEmpty()
    val lines = mutableListOf(ATHLETE_PROFILE_MARKER)
    lines += if (days != null) {
        "- Training days per week: $days"
    } else {
        "- Training days per week: $SETUP_ASSUMED_DAYS (assumed; skipped)"
    }
    lines += if (age != null) {
        "- Age: $age"
    } else {
        "- Age: healthy adult, no age-specific limits (assumed; skipped)"
    }
    lines += when (sex) {
        "female" -> "- Sex: female"
        "male" -> "- Sex: male"
        "unspecified" -> "- Sex: not specified; no sex-specific adjustments"
        else -> "- Sex: no sex-specific adjustments (assumed; skipped)"
    }
    lines += when (experience) {
        "beginner" -> "- Experience: new to lifting"
        "experienced" -> "- Experience: experienced"
        "some" -> "- Experience: some experience"
        else -> "- Experience: some experience (assumed; skipped)"
    }
    lines += when (equipment) {
        "machines" -> "- Equipment: machines only"
        "free-weights" -> "- Equipment: dumbbells, floor work, and free weights"
        "both" -> "- Equipment: machines, dumbbells, and floor work"
        else -> "- Equipment: machines, dumbbells, and floor work (assumed; skipped)"
    }
    lines += if (notes.isNotEmpty()) {
        "- Extra notes: $notes"
    } else {
        "- Extra notes: none"
    }
    return lines.joinToString("\n")
}

fun setupGenerationPrompt(profile: TrainingProfile): String {
    val brief = setupBrief(profile)
    val days = profile.daysPerWeek ?: SETUP_ASSUMED_DAYS
    return """
Create a complete new gym program for me from scratch. Do not edit an existing plan.

$brief

Requirements:
- Write a full program in the usual plaintext shape: name, goals, weekly schedule for all 7 days, global notes, progression, and workouts with sets × reps and muscles.
- Schedule exactly $days lifting days. Remaining days are Rest.
- Choose exercises that match the equipment. Machines only means machine and cable work. Dumbbells and floor work means dumbbells, bodyweight, floor, and free-weight movements. Both means a mix.
- Match volume, complexity, and rest to the stated experience.
- Apply age or sex adjustments only when they are stated in the profile.
- Honour extra notes when present.
- Reply with the full plan in a plan block and mark it ready to save. Do not wait for another confirmation.
    """.trimIndent()
}
