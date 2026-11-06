package app.cinderblock.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.cinderblock.BuildConfig
import app.cinderblock.CinderViewModel
import app.cinderblock.data.clearSkip
import app.cinderblock.data.signOut
import app.cinderblock.data.syncNow
import app.cinderblock.domain.DayStatus
import app.cinderblock.domain.HapticKind
import app.cinderblock.domain.bodyTrend
import app.cinderblock.domain.countStat
import app.cinderblock.domain.currentWeekRuns
import app.cinderblock.domain.dateInRange
import app.cinderblock.domain.dayStatus
import app.cinderblock.domain.daySummary
import app.cinderblock.domain.displayRemaining
import app.cinderblock.domain.formatClock
import app.cinderblock.domain.formatLongDate
import app.cinderblock.domain.formatMediumDate
import app.cinderblock.domain.formatNumber
import app.cinderblock.domain.formatPlan
import app.cinderblock.domain.formatRunSummary
import app.cinderblock.domain.formatVersionLabel
import app.cinderblock.domain.formatWeekRange
import app.cinderblock.domain.goalProgress
import app.cinderblock.domain.homePill
import app.cinderblock.domain.isDayDone
import app.cinderblock.domain.logLabel
import app.cinderblock.domain.parseDate
import app.cinderblock.domain.phaseLabel
import app.cinderblock.domain.PHASES
import app.cinderblock.domain.planTotalMinutes
import app.cinderblock.domain.scheduleHint
import app.cinderblock.domain.scheduleSectionLabel
import app.cinderblock.domain.unfinishedSession
import app.cinderblock.domain.weekRange
import app.cinderblock.ui.components.CardBlock
import app.cinderblock.ui.components.DisplayText
import app.cinderblock.ui.components.Mono
import app.cinderblock.ui.components.OutlineButton
import app.cinderblock.ui.components.Pill
import app.cinderblock.ui.components.PrimaryButton
import app.cinderblock.ui.components.PulseDot
import app.cinderblock.ui.components.ScreenColumn
import app.cinderblock.ui.components.SectionHeader
import app.cinderblock.ui.components.rememberNow
import app.cinderblock.ui.nav.Routes
import app.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val menuOpen = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
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
    val showStats = sessions.total > 0 || completed.total > 0 || runStat.total > 0 || weight != null || waist != null
    val wide = false

    BackHandler(menuOpen.value) { menuOpen.value = false }
    Box(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = !menuOpen.value).navigationBarsPadding()) {
            ScreenColumn(768.dp) {
                Header(wide, menuOpen.value, { menuOpen.value = !menuOpen.value }, {
                    vm.controller.haptic(HapticKind.Selection)
                    menuOpen.value = false
                    nav.navigate(Routes.ai("discuss"))
                })
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SectionHeader("Today")
                    Mono(formatLongDate(today), size = 12.sp)
                    DisplayText(program.name, size = 22.sp)
                    Mono(formatVersionLabel(program.version), size = 11.sp)
                    unfinished?.let { (date, log) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(colors.neonOrange)
                            Mono(
                                "  ${logLabel(program, log)} from ${parseDate(date)?.let(::formatMediumDate)} — unfinished",
                                color = colors.foreground,
                                size = 12.sp,
                            )
                        }
                    }
                    active?.let { run ->
                        val phase = phaseLabel(PHASES[run.phaseIndex.coerceIn(0, 2)])
                        val suffix = if (run.running) " — in progress" else " · ${formatClock(displayRemaining(run, now))} remaining · paused"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(colors.neonYellow)
                            Mono("  ${formatPlan(run.plan)} · $phase$suffix", color = colors.foreground, size = 12.sp)
                        }
                    }
                    if (unfinished != null) {
                        PrimaryButton("Continue ${logLabel(program, unfinished.second)}", {
                            vm.controller.haptic(HapticKind.Success)
                            nav.navigate(Routes.workout(unfinished.first))
                        })
                    } else {
                        PrimaryButton("Start training", {
                            vm.controller.haptic(HapticKind.Success)
                            nav.navigate(Routes.gym(null))
                        })
                    }
                    if (active != null) {
                        PrimaryButton("Continue run", {
                            vm.controller.haptic(HapticKind.Success)
                            nav.navigate(Routes.RunSession)
                        })
                    } else {
                        OutlineButton("Start run", {
                            vm.controller.haptic(HapticKind.Selection)
                            nav.navigate(Routes.Running)
                        })
                    }
                    OutlineButton("Metrics", {
                        vm.controller.haptic(HapticKind.Selection)
                        nav.navigate(Routes.Metrics)
                    })

                    SectionHeader("This week")
                    CardBlock {
                        DisplayText(formatWeekRange(weekStart, weekEnd), size = 14.sp)
                        if (weekGym.isEmpty() && weekRuns.isEmpty()) {
                            Mono("No training logged yet this week.")
                        } else {
                            val done = weekGym.count { dayStatus(it.value) == DayStatus.Complete }
                            val rest = weekGym.count { dayStatus(it.value) == DayStatus.Rest }
                            val restBit = if (rest > 0) " · $rest rest day${if (rest == 1) "" else "s"}" else ""
                            Mono("$done workout${if (done == 1) "" else "s"} done$restBit · ${weekRuns.size} run${if (weekRuns.size == 1) "" else "s"} logged", color = colors.foreground)
                            (weekGym.keys.sorted().map { it to "gym" } + weekRuns.map { it.date to "run:${it.id}" })
                                .sortedBy { it.first }
                                .forEach { (date, kind) ->
                                    if (kind == "gym") {
                                        val log = weekGym.getValue(date)
                                        val status = dayStatus(log)
                                        Column(Modifier.fillMaxWidth().clickable {
                                            vm.controller.haptic(HapticKind.Selection)
                                            nav.navigate(Routes.workout(date))
                                        }.padding(vertical = 8.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Mono(parseDate(date)?.let(::formatMediumDate) ?: date)
                                                Pill(homePill(status), if (status == DayStatus.Complete) colors.success else colors.neonOrange)
                                            }
                                            Mono(daySummary(program, log), color = colors.foreground, size = 13.sp)
                                        }
                                    }
                                }
                            weekRuns.sortedBy { it.completedAt }.forEach { run ->
                                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Mono(parseDate(run.date)?.let(::formatMediumDate) ?: run.date)
                                            Pill("Run", colors.neonYellow)
                                        }
                                        Mono(formatRunSummary(run), color = colors.foreground, size = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                    OutlineButton("Open training log", { nav.navigate(Routes.gym(null)) })

                    state.runLog.firstOrNull()?.let { run ->
                        SectionHeader("Last run")
                        CardBlock {
                            Mono(parseDate(run.date)?.let(::formatMediumDate) ?: run.date)
                            Mono(formatPlan(run.plan), color = colors.foreground, size = 16.sp)
                            Mono("Warmup ${run.plan.warmupMinutes}m · Run ${run.plan.runMinutes}m · Cooldown ${run.plan.cooldownMinutes}m · ${planTotalMinutes(run.plan)} min")
                        }
                    }

                    if (showStats) {
                        SectionHeader("Stats")
                        if (sessions.total > 0) StatCard("Sessions", sessions.total.toString(), "${sessions.thisWeek} this wk · ${sessions.lastWeek} last")
                        if (completed.total > 0) StatCard("Completed", completed.total.toString(), "${completed.thisWeek} this wk · ${completed.lastWeek} last")
                        if (runStat.total > 0) StatCard("Runs", runStat.total.toString(), "${runStat.thisWeek} this wk · ${runStat.lastWeek} last")
                        weight?.let { StatCard("Weight", "${formatNumber(it.value)} kg", it.delta?.let { d -> "${formatNumber(kotlin.math.abs(d))} kg ${it.referenceLabel.orEmpty()}" } ?: "First entry") }
                        waist?.let { StatCard("Waist", "${formatNumber(it.value)} cm", it.delta?.let { d -> "${formatNumber(kotlin.math.abs(d))} cm ${it.referenceLabel.orEmpty()}" } ?: "First entry") }
                    }

                    Column(Modifier.bringIntoViewRequester(programReq)) {
                        SectionHeader("Program goals")
                        program.goal.forEachIndexed { index, goal ->
                            val progress = goalProgress(goal, program, state.gymLog, state.runLog, weight, waist, today)
                            CardBlock {
                                Row {
                                    Mono("%02d".format(index + 1), color = colors.neonOrange)
                                    Spacer(Modifier.padding(4.dp))
                                    DisplayText(goal, size = 14.sp, modifier = Modifier.weight(1f))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Mono(progress.metricLabel)
                                    Mono(progress.valueLabel + (progress.trendText?.let { " $it" } ?: ""), color = colors.foreground)
                                }
                                progress.ratio?.let { ratio ->
                                    LinearProgressIndicator(
                                        progress = { ratio.coerceIn(0f, 1f) },
                                        color = if (ratio >= 1f) colors.success else colors.neonOrange,
                                        trackColor = colors.muted,
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                    )
                                }
                            }
                        }
                    }

                    Column(Modifier.bringIntoViewRequester(scheduleReq)) {
                        SectionHeader("Weekly schedule")
                        CardBlock {
                            Mono(scheduleSectionLabel(program), color = colors.neonOrange)
                            program.schedule.entries().forEachIndexed { index, (day, label) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Mono("%02d".format(index + 1), color = colors.neonOrange)
                                    Mono(day.replace("day", "Day "), modifier = Modifier.weight(0.4f))
                                    Mono(label, color = colors.foreground, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    SectionHeader("Explore workout")
                    CardBlock {
                        DisplayText("Preview your program", size = 16.sp)
                        Mono("View exercises, sets, reps, and coaching notes from ${program.name} without starting a session.")
                        OutlineButton("Browse all workouts", { nav.navigate(Routes.Explore) })
                    }
                    program.workouts.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (key, workout) ->
                                CardBlock(Modifier.weight(1f).clickable { nav.navigate(Routes.explore(key)) }) {
                                    DisplayText(workout.name.uppercase(), size = 13.sp, color = if (key.lowercase().contains("upper")) colors.neonOrange else colors.neonAmber)
                                    Mono(scheduleHint(program, key), maxLines = 2)
                                    Mono("${workout.exercises.size} exercises")
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }

                    Column(Modifier.bringIntoViewRequester(progressionReq)) {
                        SectionHeader("Progression")
                        CardBlock {
                            DisplayText(program.progression.method, size = 16.sp)
                            Mono("Hit the top of the rep range on all sets, then increase weight by the smallest increment. Track every session to see progress clearly.")
                            Mono("${program.progression.example.exercise} — ${program.progression.example.nextStep}", color = colors.neonOrange)
                            Mono("W1 → W2 → W3: add reps, then add weight")
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        if (menuOpen.value) {
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.7f)).clickable { menuOpen.value = false })
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 64.dp)
                    .fillMaxWidth()
                    .background(colors.card)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Mono("NAVIGATION MATRIX", color = colors.neonOrange)
                Mono("On this page", color = colors.mutedForeground)
                MenuLink("Program", "Goals & structure") { menuOpen.value = false; scope.launch { programReq.bringIntoView() } }
                MenuLink("Schedule", "Weekly split") { menuOpen.value = false; scope.launch { scheduleReq.bringIntoView() } }
                MenuLink("Progression", "Load & rep rules") { menuOpen.value = false; scope.launch { progressionReq.bringIntoView() } }
                Mono("App", color = colors.mutedForeground, modifier = Modifier.padding(top = 12.dp))
                MenuLink("Training", "Log workouts & sets") { menuOpen.value = false; nav.navigate(Routes.gym(null)) }
                MenuLink("Running", "Warmup · run · cooldown") { menuOpen.value = false; nav.navigate(Routes.Running) }
                MenuLink("Metrics", "Weight & measurements") { menuOpen.value = false; nav.navigate(Routes.Metrics) }
                MenuLink("Discuss with AI", "Ask about or edit your program") { menuOpen.value = false; nav.navigate(Routes.ai("discuss")) }
                MenuLink("Settings", "Fonts, history, alerts") { menuOpen.value = false; nav.navigate(Routes.Settings) }
                Spacer(Modifier.height(12.dp))
                AuthFooter(nav, vm) { menuOpen.value = false }
                Mono("FOUNDATION STRENGTH · v${BuildConfig.VERSION_NAME}")
            }
        }
    }
}

@Composable
private fun Header(wide: Boolean, open: Boolean, onMenu: () -> Unit, onAi: () -> Unit) {
    val colors = LocalColors.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showOnline = maxWidth >= 600.dp
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp).background(colors.neonOrange, RoundedCornerShape(6.dp)).padding(8.dp)) {
                    Mono("C", color = colors.primaryForeground, weight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                DisplayText("CINDERBLOCK", size = 16.sp, maxLines = 1)
            }
            if (showOnline) {
                Pill("Online", colors.neonYellow)
            }
            IconButton(onAi) { Icon(Icons.Default.AutoAwesome, "Discuss with AI", tint = colors.mutedForeground) }
            IconButton(onMenu) { Icon(if (open) Icons.Default.Close else Icons.Default.Menu, if (open) "Close menu" else "Open menu", tint = colors.foreground) }
        }
    }
}

@Composable
private fun MenuLink(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalColors.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        DisplayText(title, size = 16.sp)
        Mono(subtitle)
    }
}

@Composable
private fun StatCard(label: String, value: String, trend: String) {
    CardBlock {
        DisplayText(value, size = 22.sp, align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Mono(label, align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Mono(trend, align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AuthFooter(nav: NavHostController, vm: CinderViewModel, close: () -> Unit) {
    val state by vm.controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = LocalColors.current
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        when {
            !state.cloud.configured -> if (wide) Pill("Local only", colors.mutedForeground)
            state.auth != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wide) {
                    val label = when (state.syncPhase) {
                        app.cinderblock.domain.SyncPhase.Syncing -> "Syncing"
                        app.cinderblock.domain.SyncPhase.Error -> "Sync err"
                        else -> "Synced"
                    }
                    OutlineButton(label, {
                        if (state.syncPhase != app.cinderblock.domain.SyncPhase.Syncing) scope.launch { vm.controller.syncNow() }
                    })
                }
                OutlineButton("Out", { scope.launch { vm.controller.signOut() } })
            }
            else -> OutlineButton("In", {
                scope.launch { vm.controller.clearSkip() }
                close()
                nav.navigate(Routes.Login)
            })
        }
    }
}
