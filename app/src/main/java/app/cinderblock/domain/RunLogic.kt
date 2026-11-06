package app.cinderblock.domain

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

val PHASES = listOf(RunPhase.Warmup, RunPhase.Run, RunPhase.Cooldown)

fun phaseLabel(phase: RunPhase): String = when (phase) {
    RunPhase.Warmup -> "Warmup"
    RunPhase.Run -> "Run"
    RunPhase.Cooldown -> "Cooldown"
}

fun phaseHint(phase: RunPhase): String = when (phase) {
    RunPhase.Warmup -> "Easy pace · loosen up"
    RunPhase.Run -> "Steady effort · stay relaxed"
    RunPhase.Cooldown -> "Slow down · recover"
}

fun phaseSeconds(plan: RunningPlan, phase: RunPhase): Int = when (phase) {
    RunPhase.Warmup -> plan.warmupMinutes * 60
    RunPhase.Run -> plan.runMinutes * 60
    RunPhase.Cooldown -> plan.cooldownMinutes * 60
}

fun phaseSeconds(plan: RunningPlan, index: Int): Int =
    phaseSeconds(plan, PHASES[index.coerceIn(0, PHASES.lastIndex)])

fun planTotalSeconds(plan: RunningPlan): Int =
    (plan.warmupMinutes + plan.runMinutes + plan.cooldownMinutes) * 60

fun formatPlan(plan: RunningPlan): String =
    "${plan.warmupMinutes} · ${plan.runMinutes} · ${plan.cooldownMinutes}"

fun planTotalMinutes(plan: RunningPlan): Int =
    plan.warmupMinutes + plan.runMinutes + plan.cooldownMinutes

fun formatRunSummary(run: RunLog): String {
    val suffix = if (run.endedEarly) " · ended early" else ""
    return "Run ${formatPlan(run.plan)} · ${planTotalMinutes(run.plan)} min$suffix"
}

fun displayRemaining(run: ActiveRun, now: Long): Int {
    if (run.running && run.phaseEndsAt != null) {
        return ((run.phaseEndsAt - now) / 1000L).toInt().coerceAtLeast(0)
    }
    return run.remainingSeconds.coerceAtLeast(0)
}

data class ProjectedRun(val run: ActiveRun?, val finished: Boolean)

fun projectRun(run: ActiveRun, now: Long): ProjectedRun {
    if (!run.started) return ProjectedRun(run, false)
    if (!run.running || run.phaseEndsAt == null) return ProjectedRun(run, false)
    var index = run.phaseIndex.coerceIn(0, PHASES.lastIndex)
    var end = run.phaseEndsAt
    while (now >= end) {
        index += 1
        if (index >= PHASES.size) return ProjectedRun(null, true)
        end += phaseSeconds(run.plan, index) * 1000L
    }
    val remaining = ((end - now) / 1000L).toInt().coerceAtLeast(0)
    return ProjectedRun(
        run.copy(phaseIndex = index, phaseEndsAt = end, remainingSeconds = remaining, updatedAt = now),
        false,
    )
}

fun sessionProgress(run: ActiveRun, now: Long): Float {
    if (!run.started) return 0f
    val total = planTotalSeconds(run.plan).coerceAtLeast(1)
    var elapsed = 0
    for (index in 0 until run.phaseIndex.coerceAtLeast(0)) {
        elapsed += phaseSeconds(run.plan, index)
    }
    val phase = phaseSeconds(run.plan, run.phaseIndex)
    elapsed += (phase - displayRemaining(run, now)).coerceAtLeast(0)
    return (elapsed.toFloat() / total).coerceIn(0f, 1f)
}

fun buildLoggedRunPlan(plan: RunningPlan, phaseIndex: Int, remainingSeconds: Int): RunningPlan {
    val runIndex = PHASES.indexOf(RunPhase.Run)
    val runMinutes = when {
        phaseIndex > runIndex -> plan.runMinutes
        phaseIndex == runIndex -> {
            val elapsed = (phaseSeconds(plan, RunPhase.Run) - remainingSeconds).coerceAtLeast(0)
            if (elapsed > 0) max(1, (elapsed / 60.0).roundToInt()) else 0
        }
        else -> 0
    }
    return plan.copy(runMinutes = runMinutes)
}

fun runsForDate(runs: List<RunLog>, date: String): List<RunLog> =
    runs.filter { it.date == date }.sortedByDescending { it.completedAt }

fun currentWeekRuns(runs: List<RunLog>, today: LocalDate = LocalDate.now()): List<RunLog> {
    val (start, end) = weekRange(today)
    return runs.filter { dateInRange(it.date, start, end) }.sortedByDescending { it.completedAt }
}

val WARMUP_PRESETS = listOf(3, 5, 10, 15)
val RUN_PRESETS = listOf(15, 20, 30, 45, 60)
val COOLDOWN_PRESETS = listOf(3, 5, 10)
