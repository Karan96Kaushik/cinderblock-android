package app.cinderblock.ui.running

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
import app.cinderblock.CinderViewModel
import app.cinderblock.data.acknowledgeRun
import app.cinderblock.data.discardRun
import app.cinderblock.data.endRunEarly
import app.cinderblock.data.openRun
import app.cinderblock.data.pauseRun
import app.cinderblock.data.resumeRun
import app.cinderblock.data.saveRunPlan
import app.cinderblock.data.skipPhase
import app.cinderblock.data.startRunClock
import app.cinderblock.domain.PHASES
import app.cinderblock.domain.RunPhase
import app.cinderblock.domain.RUN_PRESETS
import app.cinderblock.domain.WARMUP_PRESETS
import app.cinderblock.domain.COOLDOWN_PRESETS
import app.cinderblock.domain.displayRemaining
import app.cinderblock.domain.formatClock
import app.cinderblock.domain.formatMediumDate
import app.cinderblock.domain.formatPlan
import app.cinderblock.domain.parseDate
import app.cinderblock.domain.phaseHint
import app.cinderblock.domain.phaseLabel
import app.cinderblock.domain.planTotalMinutes
import app.cinderblock.domain.sessionProgress
import app.cinderblock.ui.components.CardBlock
import app.cinderblock.ui.components.ConfirmBlock
import app.cinderblock.ui.components.DisplayText
import app.cinderblock.ui.components.FeatureBar
import app.cinderblock.ui.components.Mono
import app.cinderblock.ui.components.OutlineButton
import app.cinderblock.ui.components.Pill
import app.cinderblock.ui.components.PrimaryButton
import app.cinderblock.ui.components.rememberNow
import app.cinderblock.ui.gym.AwakeAndMedia
import app.cinderblock.ui.nav.Routes
import app.cinderblock.ui.nav.goHome
import app.cinderblock.ui.nav.openFromHome
import app.cinderblock.ui.theme.LocalColors
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
