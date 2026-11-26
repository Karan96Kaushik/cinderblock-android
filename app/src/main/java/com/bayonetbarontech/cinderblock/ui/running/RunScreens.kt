package com.bayonetbarontech.cinderblock.ui.running

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bayonetbarontech.cinderblock.CinderViewModel
import com.bayonetbarontech.cinderblock.data.acknowledgeRun
import com.bayonetbarontech.cinderblock.data.discardRun
import com.bayonetbarontech.cinderblock.data.endRunEarly
import com.bayonetbarontech.cinderblock.data.openRun
import com.bayonetbarontech.cinderblock.data.pauseRun
import com.bayonetbarontech.cinderblock.data.resumeRun
import com.bayonetbarontech.cinderblock.data.saveRunPlan
import com.bayonetbarontech.cinderblock.data.skipPhase
import com.bayonetbarontech.cinderblock.data.startRunClock
import com.bayonetbarontech.cinderblock.domain.PHASES
import com.bayonetbarontech.cinderblock.domain.RunPhase
import com.bayonetbarontech.cinderblock.domain.RUN_PRESETS
import com.bayonetbarontech.cinderblock.domain.WARMUP_PRESETS
import com.bayonetbarontech.cinderblock.domain.COOLDOWN_PRESETS
import com.bayonetbarontech.cinderblock.domain.displayRemaining
import com.bayonetbarontech.cinderblock.domain.formatClock
import com.bayonetbarontech.cinderblock.domain.formatMediumDate
import com.bayonetbarontech.cinderblock.domain.formatPlan
import com.bayonetbarontech.cinderblock.domain.parseDate
import com.bayonetbarontech.cinderblock.domain.phaseHint
import com.bayonetbarontech.cinderblock.domain.phaseLabel
import com.bayonetbarontech.cinderblock.domain.planTotalMinutes
import com.bayonetbarontech.cinderblock.domain.sessionProgress
import com.bayonetbarontech.cinderblock.ui.components.CardBlock
import com.bayonetbarontech.cinderblock.ui.components.WorkoutCover
import com.bayonetbarontech.cinderblock.ui.components.ConfirmBlock
import com.bayonetbarontech.cinderblock.ui.components.DisplayText
import com.bayonetbarontech.cinderblock.ui.components.FeatureBar
import com.bayonetbarontech.cinderblock.ui.components.Mono
import com.bayonetbarontech.cinderblock.ui.components.OutlineButton
import com.bayonetbarontech.cinderblock.ui.components.Pill
import com.bayonetbarontech.cinderblock.ui.components.PrimaryButton
import com.bayonetbarontech.cinderblock.ui.components.rememberNow
import com.bayonetbarontech.cinderblock.ui.gym.AwakeAndMedia
import com.bayonetbarontech.cinderblock.ui.nav.Routes
import com.bayonetbarontech.cinderblock.ui.nav.goHome
import com.bayonetbarontech.cinderblock.ui.nav.openFromHome
import com.bayonetbarontech.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch

@Composable
fun RunPlanScreen(nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val active = state.activeRun
    val resumable = active?.started == true
    var editing by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val plan = state.runningPlan
    Column(Modifier.fillMaxSize().background(colors.background)) {
        FeatureBar("RUNNING", { nav.goHome() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WorkoutCover("Run", "Run")
            DisplayText("Run session", size = 22.sp)
            Mono("Warmup · run · cooldown — ${formatPlan(plan)}")
            if (resumable && active != null) {
                CardBlock {
                    DisplayText("Session in progress", size = 16.sp)
                    Mono(formatPlan(active.plan))
                    Mono(phaseLabel(PHASES[active.phaseIndex.coerceIn(0, PHASES.lastIndex)]))
                    Mono(
                        if (active.running) {
                            "Timer was running — will catch up from last timestamp"
                        } else {
                            "${formatClock(displayRemaining(active, now))} remaining · paused"
                        },
                        color = colors.foreground,
                    )
                    if (discard) {
                        ConfirmBlock(
                            "Discard this run? Progress will not be saved.",
                            "Discard",
                            {
                                scope.launch {
                                    vm.controller.discardRun()
                                    discard = false
                                }
                            },
                            { discard = false },
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                PrimaryButton("Continue run", {
                                    scope.launch {
                                        vm.controller.openRun(false)
                                        nav.navigate(Routes.RunSession)
                                    }
                                })
                            }
                            Box(Modifier.weight(1f)) { OutlineButton("Discard", { discard = true }) }
                        }
                    }
                }
            }
            CardBlock {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Mono("Run preset")
                        DisplayText("${formatPlan(plan)}", size = 18.sp)
                        Mono("${planTotalMinutes(plan)} min total")
                    }
                    Mono("✎", color = colors.neonOrange, size = 18.sp, modifier = Modifier.clickable { editing = !editing })
                }
                if (editing) {
                    DisplayText("Edit run preset", size = 16.sp)
                    PresetRow("Warmup", WARMUP_PRESETS, plan.warmupMinutes) { minutes ->
                        scope.launch { vm.controller.saveRunPlan(plan.copy(warmupMinutes = minutes)) }
                    }
                    PresetRow("Run", RUN_PRESETS, plan.runMinutes) { minutes ->
                        scope.launch { vm.controller.saveRunPlan(plan.copy(runMinutes = minutes)) }
                    }
                    PresetRow("Cooldown", COOLDOWN_PRESETS, plan.cooldownMinutes) { minutes ->
                        scope.launch { vm.controller.saveRunPlan(plan.copy(cooldownMinutes = minutes)) }
                    }
                    Mono("Session total · ${planTotalMinutes(plan)} min", color = colors.foreground)
                    OutlineButton("Done", { editing = false })
                }
            }
            PrimaryButton(if (resumable) "Start new session" else "Start session", {
                scope.launch {
                    vm.controller.openRun(resumable)
                    nav.navigate(Routes.RunSession)
                }
            })
            DisplayText("Recent runs", size = 16.sp)
            val recent = state.runLog.sortedByDescending { it.completedAt }.take(3)
            if (recent.isEmpty()) {
                Mono("No runs logged yet.")
            } else {
                recent.forEach { run ->
                    val date = parseDate(run.date)?.let { formatMediumDate(it) } ?: run.date
                    CardBlock {
                        Mono(date, color = colors.foreground)
                        Mono("${run.plan.warmupMinutes} · ${run.plan.runMinutes} · ${run.plan.cooldownMinutes} min")
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetRow(label: String, choices: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    val colors = LocalColors.current
    Mono(label)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { minutes ->
            val on = minutes == selected
            Mono(
                "${minutes}m",
                color = if (on) colors.background else colors.neonOrange,
                modifier = Modifier
                    .background(if (on) colors.neonOrange else colors.background, CircleShape)
                    .border(1.dp, colors.neonOrange, CircleShape)
                    .clickable { onPick(minutes) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun RunSessionScreen(nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val completion = state.runCompletion
    LaunchedEffect(state.activeRun, completion) {
        if (state.activeRun == null && completion == null) vm.controller.openRun(false)
    }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        FeatureBar("RUNNING", { nav.openFromHome(Routes.Running) })
        val run = state.activeRun
        if (completion != null) {
            val log = completion.log
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DisplayText(if (completion.endedEarly) "RUN ENDED" else "RUN COMPLETE", size = 28.sp, color = colors.neonOrange)
                if (completion.endedEarly) Mono("Run ended early · warmup and cooldown from your session plan", color = colors.foreground)
                Mono("Warmup ${log.plan.warmupMinutes} min")
                Mono("Run ${log.plan.runMinutes} min")
                Mono("Cooldown ${log.plan.cooldownMinutes} min")
                PrimaryButton("Done", {
                    scope.launch {
                        vm.controller.acknowledgeRun()
                        nav.openFromHome(Routes.Running)
                    }
                })
            }
        } else if (run != null) {
        val phase = PHASES[run.phaseIndex.coerceIn(0, PHASES.lastIndex)]
        val remaining = displayRemaining(run, now)
        var endConfirm by remember { mutableStateOf(false) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (now < state.runRestoredUntil) {
                Mono("Session restored", color = colors.neonYellow)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PHASES.forEachIndexed { index, item ->
                    val color = when {
                        index < run.phaseIndex -> colors.neonOrange
                        index == run.phaseIndex -> colors.neonYellow
                        else -> colors.border
                    }
                    Box(Modifier.size(14.dp).background(color, CircleShape))
                    if (index < PHASES.lastIndex) Box(Modifier.size(width = 24.dp, height = 2.dp).background(colors.border))
                }
            }
            if (run.started) {
                LinearProgressIndicator(
                    progress = { sessionProgress(run, now) },
                    color = colors.neonOrange,
                    trackColor = colors.muted,
                    modifier = Modifier.fillMaxWidth(),
                )
                Mono("${(sessionProgress(run, now) * 100).toInt()}%")
            }
            DisplayText(phaseLabel(phase), size = 28.sp)
            Mono(phaseHint(phase), color = colors.foreground)
            if (run.runEndedEarly && phase == RunPhase.Cooldown) {
                Pill("Run ended early — cooldown", colors.neonYellow)
            }
            DisplayText(formatClock(remaining), size = 56.sp)
            Mono(
                when {
                    !run.started -> "Ready"
                    run.running -> "In progress"
                    else -> "Paused"
                },
                color = colors.foreground,
            )
            if (!run.started) {
                PrimaryButton("Start run", { scope.launch { vm.controller.startRunClock() } })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlineButton(if (run.running) "Pause" else "Resume", {
                            scope.launch { if (run.running) vm.controller.pauseRun() else vm.controller.resumeRun() }
                        })
                    }
                    Box(Modifier.weight(1f)) { OutlineButton("Skip", { scope.launch { vm.controller.skipPhase() } }) }
                }
                if (phase == RunPhase.Run && !run.runEndedEarly) {
                    if (endConfirm) {
                        ConfirmBlock(
                            "End run now and go to cooldown?",
                            "End run",
                            {
                                scope.launch { vm.controller.endRunEarly() }
                                endConfirm = false
                            },
                            { endConfirm = false },
                        )
                    } else {
                        OutlineButton("End run", { endConfirm = true })
                    }
                }
            }
            AwakeAndMedia(vm)
            OutlineButton("Back to plan", { nav.openFromHome(Routes.Running) })
        }
        }
    }
}
