package com.bayonetbarontech.cinderblock.data

import com.bayonetbarontech.cinderblock.domain.AppState
import com.bayonetbarontech.cinderblock.domain.DayLog
import com.bayonetbarontech.cinderblock.domain.ExerciseLog
import com.bayonetbarontech.cinderblock.domain.REST_KEY
import com.bayonetbarontech.cinderblock.domain.REST_LABEL
import com.bayonetbarontech.cinderblock.domain.RestTimer
import com.bayonetbarontech.cinderblock.domain.hasSetData
import com.bayonetbarontech.cinderblock.domain.initExercises
import com.bayonetbarontech.cinderblock.domain.isSetComplete
import com.bayonetbarontech.cinderblock.domain.isTimedHold
import com.bayonetbarontech.cinderblock.domain.resolveProgram

internal suspend fun Controller.selectWorkout(date: String, key: String) {
    edit(sync = true) { state ->
        val existing = state.gymLog[date]
        val now = System.currentTimeMillis()
        val day = when {
            key == REST_KEY -> DayLog(REST_KEY, emptyMap(), state.program.version, REST_LABEL, now)
            existing?.workoutKey == key && existing != null -> existing.copy(updatedAt = now)
            else -> {
                val workout = state.program.workouts[key] ?: return@edit state
                DayLog(key, initExercises(workout), state.program.version, workout.name, now)
            }
        }
        state.copy(gymLog = state.gymLog + (date to day))
    }
}

internal suspend fun Controller.updateSet(
    date: String,
    exercise: String,
    index: Int,
    weight: String? = null,
    reps: String? = null,
    seconds: String? = null,
) {
    edit(sync = true) { state ->
        val day = state.gymLog[date] ?: return@edit state
        val log = day.exercises[exercise] ?: return@edit state
        if (log.completed || log.skipped) return@edit state
        if (index !in log.sets.indices) return@edit state
        val program = resolveProgram(state, day).first
        val definition = program.workouts[day.workoutKey]?.exercises?.firstOrNull { it.name == exercise }
        val timed = definition?.let(::isTimedHold) ?: false
        val current = log.sets[index]
        val nextSet = current.copy(
            weight = weight ?: current.weight,
            reps = reps ?: current.reps,
            seconds = seconds ?: current.seconds,
        )
        val sets = log.sets.toMutableList()
        sets[index] = nextSet
        val wasComplete = isSetComplete(current, timed)
        val nowComplete = isSetComplete(nextSet, timed)
        val rest = if (!wasComplete && nowComplete) state.rest.autoStartForSet(state, exercise, date, index) else state.rest
        val updated = day.copy(
            exercises = day.exercises + (exercise to log.copy(sets = sets)),
            updatedAt = System.currentTimeMillis(),
        )
        state.copy(gymLog = state.gymLog + (date to updated), rest = rest)
    }
}

internal suspend fun Controller.focusExercise(date: String, exercise: String) {
    if (snapshot().sessionExercise[date] == exercise) return
    val cutoff = java.time.LocalDate.now().minusDays(14).toString()
    edit(persist = true, sync = false) { state ->
        if (state.sessionExercise[date] == exercise) return@edit state
        val kept = state.sessionExercise.filterKeys { it >= cutoff }
        state.copy(sessionExercise = kept + (date to exercise))
    }
}

internal suspend fun Controller.focusSet(date: String, exercise: String, index: Int) {
    if (index <= 0) return
    edit(sync = true) { state ->
        val day = state.gymLog[date] ?: return@edit state
        val log = day.exercises[exercise] ?: return@edit state
        if (log.completed || log.skipped || index !in log.sets.indices) return@edit state
        val program = resolveProgram(state, day).first
        val definition = program.workouts[day.workoutKey]?.exercises?.firstOrNull { it.name == exercise }
        val timed = definition?.let(::isTimedHold) ?: false
        val sets = log.sets.toMutableList()
        val before = sets[index]
        val filled = if (hasSetData(before)) before else sets[index - 1].copy()
        sets[index] = filled
        val completedNow = !isSetComplete(before, timed) && isSetComplete(filled, timed)
        val rest = if (state.rest.running) {
            state.rest
        } else if (completedNow) {
            state.rest.autoStartForSet(state, exercise, date, index)
        } else if (isSetComplete(sets[index - 1], timed)) {
            state.rest.autoStartForSet(state, exercise, date, index - 1)
        } else {
            state.rest
        }
        if (filled == before && rest == state.rest) return@edit state
        val updated = if (filled == before) {
            day
        } else {
            day.copy(
                exercises = day.exercises + (exercise to log.copy(sets = sets)),
                updatedAt = System.currentTimeMillis(),
            )
        }
        state.copy(gymLog = state.gymLog + (date to updated), rest = rest)
    }
}

internal suspend fun Controller.markExercise(date: String, exercise: String, done: Boolean, skipped: Boolean) {
    edit(sync = true) { state ->
        val day = state.gymLog[date] ?: return@edit state
        val log = day.exercises[exercise] ?: return@edit state
        val updatedLog = log.copy(completed = done, skipped = skipped)
        val updated = day.copy(
            exercises = day.exercises + (exercise to updatedLog),
            updatedAt = System.currentTimeMillis(),
        )
        state.copy(gymLog = state.gymLog + (date to updated))
    }
}

internal suspend fun Controller.ensureSets(date: String) {
    edit(sync = false) { state ->
        val day = state.gymLog[date] ?: return@edit state
        if (day.workoutKey == REST_KEY) return@edit state
        val program = resolveProgram(state, day).first
        val workout = program.workouts[day.workoutKey] ?: return@edit state
        var changed = false
        val exercises = day.exercises.toMutableMap()
        for (exercise in workout.exercises) {
            val log = exercises[exercise.name]
            if (log == null) {
                exercises[exercise.name] = ExerciseLog(sets = List(exercise.sets.coerceAtLeast(1)) { com.bayonetbarontech.cinderblock.domain.SetLog() })
                changed = true
            } else if (log.sets.size < exercise.sets) {
                exercises[exercise.name] = log.copy(
                    sets = log.sets + List(exercise.sets - log.sets.size) { com.bayonetbarontech.cinderblock.domain.SetLog() },
                )
                changed = true
            }
        }
        if (!changed) state else state.copy(gymLog = state.gymLog + (date to day.copy(exercises = exercises)))
    }
}

internal suspend fun Controller.deleteDay(date: String) {
    edit(sync = true) { it.copy(gymLog = it.gymLog - date) }
}

internal suspend fun Controller.deleteOldWorkouts() {
    val cutoff = java.time.LocalDate.now().minusDays(30).toString()
    edit(sync = true) { state ->
        state.copy(gymLog = state.gymLog.filterKeys { it >= cutoff })
    }
}

internal suspend fun Controller.clearWorkouts() {
    edit(sync = true) { it.copy(gymLog = emptyMap()) }
}

internal suspend fun Controller.setVideo(exercise: String, url: String?) {
    edit(sync = false) { state ->
        val next = state.videos.toMutableMap()
        if (url.isNullOrBlank()) next.remove(exercise) else next[exercise] = url.trim()
        state.copy(videos = next)
    }
}

internal suspend fun Controller.startRest(date: String, exercise: String, seconds: Int) {
    edit(persist = false) { state ->
        state.copy(rest = state.rest.start(exercise, date, seconds))
    }
}

internal suspend fun Controller.pauseRest() {
    val now = System.currentTimeMillis()
    edit(persist = false) { state ->
        val rest = state.rest
        if (!rest.running) return@edit state
        state.copy(rest = rest.copy(running = false, endsAt = null, remainingSeconds = rest.displayRemaining(now)))
    }
}

internal suspend fun Controller.resumeRest() {
    val now = System.currentTimeMillis()
    edit(persist = false) { state ->
        val rest = state.rest
        if (rest.remainingSeconds <= 0) return@edit state
        state.copy(
            rest = rest.copy(
                running = true,
                announced = false,
                endsAt = now + rest.remainingSeconds * 1000L,
            ),
        )
    }
}

internal suspend fun Controller.adjustRest(deltaSeconds: Int) {
    val now = System.currentTimeMillis()
    edit(persist = false) { state ->
        val rest = state.rest
        if (rest.ownerExercise == null || rest.announced) return@edit state
        val remaining = (rest.displayRemaining(now) + deltaSeconds).coerceAtLeast(1)
        state.copy(
            rest = rest.copy(
                remainingSeconds = remaining,
                durationSeconds = maxOf(rest.durationSeconds + deltaSeconds, remaining),
                endsAt = if (rest.running) now + remaining * 1000L else null,
            ),
        )
    }
}

internal suspend fun Controller.resetRest() {
    edit(persist = false) { state ->
        state.copy(
            rest = RestTimer(autoStarted = state.rest.autoStarted),
        )
    }
}

internal suspend fun Controller.completeRest() {
    val state = snapshot()
    if (!state.rest.running || state.rest.announced) return
    edit(persist = false) { current ->
        current.copy(
            rest = current.rest.copy(running = false, endsAt = null, remainingSeconds = 0, announced = true),
        )
    }
    playSound("timer")
    haptic(com.bayonetbarontech.cinderblock.domain.HapticKind.Success)
}

internal suspend fun Controller.handleTimerAction(action: String?) {
    when (action) {
        ACTION_RUN_PAUSE -> pauseRun()
        ACTION_RUN_RESUME -> resumeRun()
        ACTION_RUN_SKIP -> skipPhase()
        ACTION_REST_START, ACTION_EXERCISE_DONE -> {
            val session = sessionNotice(snapshot(), System.currentTimeMillis()) ?: return
            val current = session.current ?: return
            if (action == ACTION_REST_START) {
                startRest(session.date, current, session.defaultRestSeconds)
            } else {
                haptic(com.bayonetbarontech.cinderblock.domain.HapticKind.Success)
                markExercise(session.date, current, done = true, skipped = false)
            }
        }
        ACTION_REST_PAUSE -> pauseRest()
        ACTION_REST_RESUME -> resumeRest()
        ACTION_REST_ADD -> adjustRest(15)
        ACTION_REST_SKIP -> resetRest()
        ACTION_REST_AGAIN -> {
            val rest = snapshot().rest
            val owner = rest.ownerExercise ?: return
            val date = rest.ownerDate ?: return
            startRest(date, owner, rest.durationSeconds.coerceAtLeast(1))
        }
    }
}

private fun RestTimer.autoStartForSet(state: AppState, exercise: String, date: String, index: Int): RestTimer {
    if (!state.settings.autoStartRestTimer || index < 0) return this
    val started = autoStarted[exercise].orEmpty()
    if (index in started) return this
    val duration = (state.settings.restTimerMinutes * 60).toInt().coerceAtLeast(1)
    return start(exercise, date, duration).copy(autoStarted = autoStarted + (exercise to (started + index)))
}

private fun RestTimer.start(exercise: String, date: String, seconds: Int): RestTimer {
    val now = System.currentTimeMillis()
    return copy(
        ownerExercise = exercise,
        ownerDate = date,
        endsAt = now + seconds * 1000L,
        remainingSeconds = seconds,
        running = true,
        durationSeconds = seconds,
        announced = false,
    )
}
