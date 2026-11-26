package com.bayonetbarontech.cinderblock.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bayonetbarontech.cinderblock.CinderViewModel
import com.bayonetbarontech.cinderblock.domain.DayStatus
import com.bayonetbarontech.cinderblock.domain.HapticKind
import com.bayonetbarontech.cinderblock.domain.PHASES
import com.bayonetbarontech.cinderblock.domain.bodyTrend
import com.bayonetbarontech.cinderblock.domain.countStat
import com.bayonetbarontech.cinderblock.domain.currentWeekRuns
import com.bayonetbarontech.cinderblock.R
import com.bayonetbarontech.cinderblock.domain.colorGroup
import com.bayonetbarontech.cinderblock.domain.dateInRange
import com.bayonetbarontech.cinderblock.domain.dayStatus
import com.bayonetbarontech.cinderblock.domain.daySummary
import com.bayonetbarontech.cinderblock.domain.displayRemaining
import com.bayonetbarontech.cinderblock.domain.formatClock
import com.bayonetbarontech.cinderblock.domain.formatMediumDate
import com.bayonetbarontech.cinderblock.domain.formatNumber
import com.bayonetbarontech.cinderblock.domain.formatPlan
import com.bayonetbarontech.cinderblock.domain.formatRunSummary
import com.bayonetbarontech.cinderblock.domain.formatWeekRange
import com.bayonetbarontech.cinderblock.domain.goalProgress
import com.bayonetbarontech.cinderblock.domain.homePill
import com.bayonetbarontech.cinderblock.domain.isDayDone
import com.bayonetbarontech.cinderblock.domain.logLabel
import com.bayonetbarontech.cinderblock.domain.parseDate
import com.bayonetbarontech.cinderblock.domain.phaseLabel
import com.bayonetbarontech.cinderblock.domain.planTotalMinutes
import com.bayonetbarontech.cinderblock.domain.scheduleHint
import com.bayonetbarontech.cinderblock.domain.scheduleLabelFor
import com.bayonetbarontech.cinderblock.domain.scheduleSectionLabel
import com.bayonetbarontech.cinderblock.domain.unfinishedSession
import com.bayonetbarontech.cinderblock.domain.weekRange
import com.bayonetbarontech.cinderblock.domain.workoutKeyForSchedule
import com.bayonetbarontech.cinderblock.ui.components.CardBlock
import com.bayonetbarontech.cinderblock.ui.components.CoachMark
import com.bayonetbarontech.cinderblock.ui.components.WorkoutCover
import com.bayonetbarontech.cinderblock.ui.components.DisplayText
import com.bayonetbarontech.cinderblock.ui.components.Mono
import com.bayonetbarontech.cinderblock.ui.components.OutlineButton
import com.bayonetbarontech.cinderblock.ui.components.Pill
import com.bayonetbarontech.cinderblock.ui.components.PrimaryButton
import com.bayonetbarontech.cinderblock.ui.components.PulseDot
import com.bayonetbarontech.cinderblock.ui.components.ScreenColumn
import com.bayonetbarontech.cinderblock.ui.components.rememberNow
import com.bayonetbarontech.cinderblock.ui.nav.MenuButton
import com.bayonetbarontech.cinderblock.ui.nav.PageLink
import com.bayonetbarontech.cinderblock.ui.nav.RegisterPageLinks
import com.bayonetbarontech.cinderblock.ui.nav.Routes
import com.bayonetbarontech.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val programReq = remember { BringIntoViewRequester() }
    val scheduleReq = remember { BringIntoViewRequester() }
    val progressionReq = remember { BringIntoViewRequester() }
    val now = rememberNow()
    val today = LocalDate.now()
    val program = state.program
    val unfinished = unfinishedSession(state.gymLog)
    val active = state.activeRun?.takeIf { it.started && state.runCompletion == null }
    val (weekStart, weekEnd) = weekRange(today)
    val weekGym = state.gymLog.filter { dateInRange(it.key, weekStart, weekEnd) }
    val weekRuns = currentWeekRuns(state.runLog, today)
    val weight = bodyTrend(state.bodyMetrics, "weight")
    val waist = bodyTrend(state.bodyMetrics, "waist")
    val sessionDates = state.gymLog.keys.toList()
    val completedDates = state.gymLog.filter { isDayDone(it.value) }.keys.toList()
    val sessions = countStat(sessionDates, today)
    val completed = countStat(completedDates, today)
    val runStat = countStat(state.runLog.map { it.date }, today)
    val planned = program.schedule.entries().count { it.second.isNotBlank() && !it.second.equals("rest", ignoreCase = true) }
    val weekDone = weekGym.count { dayStatus(it.value) == DayStatus.Complete }
    val todayLabel = scheduleLabelFor(program, today)
    val todayKey = workoutKeyForSchedule(program, todayLabel)
    val todayWorkout = todayKey?.let { program.workouts[it] }
    val pageLinks = remember {
        listOf(
            PageLink("Goals") { scope.launch { programReq.bringIntoView() } },
            PageLink("Schedule") { scope.launch { scheduleReq.bringIntoView() } },
            PageLink("Progression") { scope.launch { progressionReq.bringIntoView() } },
        )
    }
    RegisterPageLinks(pageLinks)

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            ScreenColumn(768.dp) {
                Row(
                    Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoachMark()
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Mono(greeting(LocalTime.now().hour), size = 13.sp)
                        DisplayText(program.name, size = 18.sp, maxLines = 1)
                    }
                    IconButton({
                        vm.controller.haptic(HapticKind.Selection)
                        nav.navigate(Routes.ai("discuss"))
                    }) {
                        Icon(Icons.Default.AutoAwesome, "Open coach", tint = colors.foreground)
                    }
                    MenuButton()
                }

                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!state.profile.completed) {
                        CardBlock {
                            DisplayText("Set up your plan", size = 18.sp)
                            Mono("Answer a few questions and Cinderblock can build a program for you.")
                            PrimaryButton("Run setup guide", { nav.navigate(Routes.Setup) })
                        }
                    }
                    HeroCard(program.name, todayLabel.ifBlank { "Show up. Log the work." })

                    unfinished?.let { (date, log) ->
                        LiveBanner(
                            Icons.Default.FitnessCenter,
                            "Continue ${logLabel(program, log)}",
                            "Unfinished · ${parseDate(date)?.let(::formatMediumDate) ?: date}",
                            colors.primary,
                        ) {
                            vm.controller.haptic(HapticKind.Success)
                            nav.navigate(Routes.workout(date, state.sessionExercise[date]))
                        }
                    }
                    active?.let { run ->
                        val phase = phaseLabel(PHASES[run.phaseIndex.coerceIn(0, PHASES.lastIndex)])
                        val left = formatClock(displayRemaining(run, now))
                        LiveBanner(
                            Icons.AutoMirrored.Filled.DirectionsRun,
                            "Run · $phase",
                            if (run.running) "$left left in this phase" else "Paused · $left left",
                            colors.neonYellow,
                            pulsing = run.running,
                        ) {
                            vm.controller.haptic(HapticKind.Success)
                            nav.navigate(Routes.RunSession)
                        }
                    }

                    CardBlock {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Mono("Today's plan", size = 12.sp)
                                DisplayText(
                                    unfinished?.let { logLabel(program, it.second) }
                                        ?: todayLabel.ifBlank { "Open training" },
                                    size = 20.sp,
                                    maxLines = 2,
                                )
                                Mono(todayMeta(todayWorkout?.exercises?.size, todayLabel, planTotalMinutes(state.runningPlan)))
                            }
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary)
                                    .clickable {
                                        vm.controller.haptic(HapticKind.Success)
                                        when {
                                            unfinished != null -> nav.navigate(Routes.workout(unfinished.first, state.sessionExercise[unfinished.first]))
                                            active != null -> nav.navigate(Routes.RunSession)
                                            else -> nav.navigate(Routes.gym(null))
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.PlayArrow, "Start", tint = colors.primaryForeground, modifier = Modifier.size(28.dp))
                            }
                        }
                    }

                    if (planned > 0 || weekDone > 0) {
                        CardBlock {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                WeekRing(weekDone, planned.coerceAtLeast(weekDone).coerceAtLeast(1))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DisplayText("This week", size = 18.sp)
                                    Mono(formatWeekRange(weekStart, weekEnd))
                                    Mono(
                                        "$weekDone workout${if (weekDone == 1) "" else "s"} · ${weekRuns.size} run${if (weekRuns.size == 1) "" else "s"}",
                                        color = colors.foreground,
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(Icons.Default.LocalFireDepartment, sessions.thisWeek.toString(), "Sessions this week", Modifier.weight(1f))
                        MetricTile(Icons.AutoMirrored.Filled.DirectionsRun, runStat.thisWeek.toString(), "Runs this week", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(
                            Icons.Default.FitnessCenter,
                            completed.total.toString(),
                            "Workouts finished",
                            Modifier.weight(1f),
                        )
                        MetricTile(
                            Icons.Default.MonitorWeight,
                            weight?.let { "${formatNumber(it.value)} kg" } ?: "—",
                            waist?.let { "Waist ${formatNumber(it.value)} cm" } ?: "Latest weight",
                            Modifier.weight(1f),
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        DisplayText("Your activity", size = 18.sp)
                        Mono(
                            "View all",
                            color = colors.primary,
                            size = 13.sp,
                            weight = FontWeight.Bold,
                            modifier = Modifier.clickable { nav.navigate(Routes.gym(null)) }.padding(8.dp),
                        )
                    }
                    CardBlock {
                        if (weekGym.isEmpty() && weekRuns.isEmpty()) {
                            Mono("Nothing logged yet this week. Start today's plan when you're ready.")
                            PrimaryButton("Open training log", { nav.navigate(Routes.gym(null)) })
                        } else {
                            weekGym.keys.sorted().forEach { date ->
                                val log = weekGym.getValue(date)
                                val status = dayStatus(log)
                                ActivityRow(
                                    parseDate(date)?.let(::formatMediumDate) ?: date,
                                    daySummary(program, log),
                                    homePill(status),
                                    if (status == DayStatus.Complete) colors.success else colors.primary,
                                ) {
                                    vm.controller.haptic(HapticKind.Selection)
                                    nav.navigate(Routes.workout(date))
                                }
                            }
                            weekRuns.sortedBy { it.completedAt }.forEach { run ->
                                ActivityRow(
                                    parseDate(run.date)?.let(::formatMediumDate) ?: run.date,
                                    formatRunSummary(run),
                                    "Run",
                                    colors.neonYellow,
                                ) {}
                            }
                        }
                    }

                    state.runLog.firstOrNull()?.let { run ->
                        DisplayText("Last run", size = 18.sp)
                        CardBlock {
                            Mono(parseDate(run.date)?.let(::formatMediumDate) ?: run.date)
                            DisplayText(formatPlan(run.plan), size = 18.sp)
                            Mono("Warmup ${run.plan.warmupMinutes}m · Run ${run.plan.runMinutes}m · Cooldown ${run.plan.cooldownMinutes}m")
                            OutlineButton("Running", { nav.navigate(Routes.Running) })
                        }
                    }

                    if (active == null && unfinished == null) {
                        OutlineButton("Start a run", {
                            vm.controller.haptic(HapticKind.Selection)
                            nav.navigate(Routes.Running)
                        })
                    }

                    Column(Modifier.bringIntoViewRequester(programReq), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DisplayText("Goals", size = 18.sp)
                        program.goal.forEach { goal ->
                            val progress = goalProgress(goal, program, state.gymLog, state.runLog, weight, waist, today)
                            CardBlock {
                                DisplayText(goal, size = 15.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Mono(progress.metricLabel)
                                    Mono(progress.valueLabel + (progress.trendText?.let { " $it" } ?: ""), color = colors.foreground)
                                }
                                progress.ratio?.let { ratio ->
                                    LinearProgressIndicator(
                                        progress = { ratio.coerceIn(0f, 1f) },
                                        color = if (ratio >= 1f) colors.success else colors.primary,
                                        trackColor = colors.muted,
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)),
                                    )
                                }
                            }
                        }
                    }

                    Column(Modifier.bringIntoViewRequester(scheduleReq)) {
                        DisplayText("Weekly schedule", size = 18.sp)
                        CardBlock {
                            Mono(scheduleSectionLabel(program), color = colors.primary)
                            program.schedule.entries().forEach { (day, label) ->
                                val isToday = day == "day${today.dayOfWeek.value}"
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isToday) colors.primary.copy(alpha = 0.12f) else colors.card)
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Mono(day.replace("day", "Day "), color = if (isToday) colors.primary else colors.mutedForeground, modifier = Modifier.weight(0.35f))
                                    Mono(label, color = colors.foreground, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    DisplayText("Workouts", size = 18.sp)
                    program.workouts.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (key, workout) ->
                                CardBlock(Modifier.weight(1f).clickable { nav.navigate(Routes.explore(key)) }) {
                                    WorkoutCover(colorGroup(program, key), workout.name)
                                    DisplayText(workout.name, size = 16.sp, maxLines = 2)
                                    Mono(scheduleHint(program, key), maxLines = 2)
                                    Mono("${workout.exercises.size} exercises", color = colors.foreground)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    OutlineButton("Browse all workouts", { nav.navigate(Routes.Explore) })

                    Column(Modifier.bringIntoViewRequester(progressionReq)) {
                        DisplayText("Progression", size = 18.sp)
                        CardBlock {
                            DisplayText(program.progression.method, size = 16.sp)
                            Mono("Hit the top of the rep range on every set, then add the smallest weight you can. Logging each session is what makes the next one obvious.")
                            Mono("${program.progression.example.exercise} — ${program.progression.example.nextStep}", color = colors.primary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private fun todayMeta(exercises: Int?, label: String, runMinutes: Int): String {
    val parts = mutableListOf<String>()
    if (exercises != null && exercises > 0) parts += "$exercises exercises"
    if (label.contains("run", ignoreCase = true)) parts += "$runMinutes min run"
    if (label.contains("rest", ignoreCase = true) && exercises == null) parts += "Recovery day"
    return parts.joinToString(" · ").ifBlank { "Log it when you train" }
}

@Composable
private fun HeroCard(program: String, caption: String) {
    val colors = LocalColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        Image(
            painterResource(R.drawable.home_hero),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        0.45f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f),
                        1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Mono(program, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), size = 13.sp, maxLines = 1)
            DisplayText("Small steps.", size = 30.sp, color = androidx.compose.ui.graphics.Color.White)
            DisplayText("Big results.", size = 30.sp, color = colors.primary)
            Mono(caption, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f), size = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun WeekRing(done: Int, planned: Int) {
    val colors = LocalColors.current
    val ratio = (done.toFloat() / planned.toFloat()).coerceIn(0f, 1f)
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(colors.muted, -90f, 360f, false, topLeft = Offset.Zero, size = Size(size.width, size.height), style = stroke)
            if (ratio > 0f) {
                drawArc(colors.primary, -90f, 360f * ratio, false, topLeft = Offset.Zero, size = Size(size.width, size.height), style = stroke)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DisplayText("$done/$planned", size = 16.sp)
            Mono("done", size = 10.sp)
        }
    }
}

@Composable
private fun MetricTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    val colors = LocalColors.current
    Column(
        modifier
            .height(108.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Column {
            DisplayText(value, size = 20.sp, maxLines = 1)
            Mono(label, maxLines = 2)
        }
    }
}

@Composable
private fun LiveBanner(icon: ImageVector, title: String, subtitle: String, accent: androidx.compose.ui.graphics.Color, pulsing: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DisplayText(title, size = 15.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (pulsing) {
                    Spacer(Modifier.size(8.dp))
                    PulseDot(accent)
                }
            }
            Mono(subtitle, color = accent, maxLines = 1)
        }
        Icon(Icons.Default.ChevronRight, null, tint = accent)
    }
}

@Composable
private fun ActivityRow(date: String, summary: String, pill: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Mono(date)
                Pill(pill, color)
            }
            DisplayText(summary, size = 15.sp, maxLines = 2)
        }
        Icon(Icons.Default.ChevronRight, null, tint = LocalColors.current.mutedForeground)
    }
}
