package app.cinderblock.ui.gym

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.cinderblock.CinderViewModel
import app.cinderblock.domain.ActionResult
import app.cinderblock.domain.DayStatus
import app.cinderblock.domain.HapticKind
import app.cinderblock.domain.REST_KEY
import app.cinderblock.domain.colorGroup
import app.cinderblock.domain.dayStatus
import app.cinderblock.domain.daySummary
import app.cinderblock.domain.exerciseStatus
import app.cinderblock.domain.exerciseStatusLabel
import app.cinderblock.domain.formatLongDate
import app.cinderblock.domain.formatMediumDate
import app.cinderblock.domain.formatMonthYear
import app.cinderblock.domain.formatPlan
import app.cinderblock.domain.formatRunSummary
import app.cinderblock.domain.formatSetSummary
import app.cinderblock.domain.formatVersionLabel
import app.cinderblock.domain.isDayDone
import app.cinderblock.domain.isHttpUrl
import app.cinderblock.domain.isProgramKey
import app.cinderblock.domain.logLabel
import app.cinderblock.domain.monthCells
import app.cinderblock.domain.parseDate
import app.cinderblock.domain.planTotalMinutes
import app.cinderblock.domain.resolveProgram
import app.cinderblock.domain.runsForDate
import app.cinderblock.domain.scheduleHint
import app.cinderblock.ui.components.CardBlock
import app.cinderblock.ui.components.DisplayText
import app.cinderblock.ui.components.FeatureBar
import app.cinderblock.ui.components.Mono
import app.cinderblock.ui.components.OutlineButton
import app.cinderblock.ui.components.Pill
import app.cinderblock.ui.components.PrimaryButton
import app.cinderblock.ui.components.ScreenColumn
import app.cinderblock.ui.nav.Routes
import app.cinderblock.ui.nav.goHome
import app.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(dateArg: String?, nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val today = LocalDate.now()
    val parsed = parseDate(dateArg)
    LaunchedEffect(dateArg) { if (dateArg != null && parsed == null) nav.navigate(Routes.gym(null)) { launchSingleTop = true } }
    var month by remember { mutableStateOf(YearMonth.from(parsed ?: today)) }
    val selected = parsed ?: today
    val log = state.gymLog[selected.toString()]
    val (program, historical) = resolveProgram(state, log)
    val runs = runsForDate(state.runLog, selected.toString())
    val scope = rememberCoroutineScope()
    var revert by remember { mutableStateOf<app.cinderblock.domain.ProgramVersionRecord?>(null) }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        FeatureBar("TRAINING", { nav.goHome() }, selected.toString().substring(5))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ScreenColumn {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DisplayText("TRAINING LOG", size = 26.sp)
                    Mono(state.program.name)
                    OutlineButton("Explore workout", { nav.navigate(Routes.Explore) })
                    if (state.gymLog.isNotEmpty() || state.runLog.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stat("GYM", state.gymLog.size.toString())
                            Stat("COMPLETED", state.gymLog.values.count { isDayDone(it) }.toString())
                            if (state.runLog.isNotEmpty()) Stat("RUNS", state.runLog.size.toString())
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ month = month.minusMonths(1) }) { Mono("‹", color = colors.neonOrange, size = 22.sp) }
                        DisplayText(formatMonthYear(month), modifier = Modifier.weight(1f), align = TextAlign.Center, size = 16.sp)
                        TextButton({ month = month.plusMonths(1) }) { Mono("›", color = colors.neonOrange, size = 22.sp) }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                            Mono(it, modifier = Modifier.weight(1f), align = TextAlign.Center)
                        }
                    }
                    monthCells(month).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                val iso = day?.toString()
                                val dayLog = iso?.let { state.gymLog[it] }
                                val status = dayStatus(dayLog)
                                val selectedDay = day == selected
                                val isToday = day == today
                                Column(
                                    Modifier.weight(1f).clickable(enabled = day != null) {
                                        if (day != null) nav.navigate(Routes.gym(day.toString()))
                                    }.padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Mono(
                                        day?.dayOfMonth?.toString() ?: "",
                                        color = when {
                                            selectedDay -> colors.primaryForeground
                                            isToday -> colors.neonYellow
                                            else -> colors.foreground
                                        },
                                        modifier = if (selectedDay) Modifier.background(colors.neonOrange, CircleShape).padding(6.dp) else Modifier.padding(6.dp),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (status != DayStatus.Empty && dayLog != null) {
                                            val dot = when {
                                                dayLog.workoutKey == REST_KEY -> colors.mutedForeground
                                                colorGroup(state.program, dayLog.workoutKey) == "upper" -> colors.neonOrange
                                                else -> colors.neonAmber
                                            }
                                            Box(
                                                Modifier.size(7.dp).clip(CircleShape).background(dot).then(
                                                    if (isDayDone(dayLog)) Modifier.border(1.dp, colors.foreground, CircleShape) else Modifier,
                                                ),
                                            )
                                        }
                                        if (iso != null && state.runLog.any { it.date == iso }) {
                                            Box(Modifier.size(7.dp).clip(CircleShape).background(colors.neonYellow))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Legend("Upper", colors.neonOrange)
                        Legend("Lower", colors.neonAmber)
                        Legend("Rest", colors.mutedForeground)
                        Legend("Run", colors.neonYellow)
                        Legend("Done", colors.foreground)
                    }
                    CardBlock {
                        Mono(formatLongDate(selected))
                        when {
                            log == null || dayStatus(log) == DayStatus.Empty -> {
                                DisplayText("No workout logged", size = 16.sp)
                                OutlineButton("Log workout", { nav.navigate(Routes.select(selected.toString())) })
                            }
                            log.workoutKey == REST_KEY -> {
                                DisplayText(logLabel(program, log), size = 16.sp)
                                Mono("Rest day logged")
                                Pill("Rest", colors.mutedForeground)
                                OutlineButton("Change workout", { nav.navigate(Routes.select(selected.toString())) })
                            }
                            else -> {
                                DisplayText(logLabel(program, log), size = 18.sp)
                                if (historical && log.programVersion != null) Pill(formatVersionLabel(log.programVersion), colors.neonYellow)
                                Mono(daySummary(program, log), color = colors.foreground)
                                val workout = program.workouts[log.workoutKey]
                                (workout?.exercises?.map { it.name } ?: log.exercises.keys).forEach { name ->
                                    val exercise = log.exercises[name]
                                    val status = exerciseStatus(exercise)
                                    val summary = exercise?.let { formatSetSummary(it) }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Mono(name, color = colors.foreground, size = 13.sp)
                                            summary?.let { Mono(it) }
                                        }
                                        Mono(exerciseStatusLabel(status), color = colors.neonOrange)
                                    }
                                }
                                val label = if (dayStatus(log) == DayStatus.Complete) "Review workout" else "Continue workout"
                                PrimaryButton(label, { nav.navigate(Routes.workout(selected.toString())) })
                            }
                        }
                    }
                    runs.forEach { run ->
                        CardBlock {
                            Mono(formatMediumDate(selected))
                            Mono(formatPlan(run.plan), color = colors.foreground)
                            Mono("Warmup ${run.plan.warmupMinutes} · Run ${run.plan.runMinutes} · Cooldown ${run.plan.cooldownMinutes} · ${planTotalMinutes(run.plan)} min")
                            if (run.endedEarly) Mono(formatRunSummary(run))
                        }
                    }
                    VersionHistory(vm)
                    CardBlock {
                        DisplayText("PROGRAM GUIDE", size = 14.sp)
                        state.program.weeks.forEach { (range, tips) ->
                            Mono(range, color = colors.neonOrange)
                            tips.forEach { Mono("· $it") }
                        }
                    }
                    CardBlock {
                        DisplayText("WEEKLY SCHEDULE", size = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.program.schedule.entries().forEach { (day, label) ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Mono(day.removePrefix("day"), color = colors.neonOrange, align = TextAlign.Center)
                                    Mono(label, maxLines = 3, align = TextAlign.Center, size = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(16.dp)) {
            PrimaryButton("+ START TODAY'S WORKOUT", {
                vm.controller.haptic(HapticKind.Success)
                nav.navigate(Routes.select(today.toString()))
            })
        }
    }
    revert?.let { record ->
        AlertDialog(
            onDismissRequest = { revert = null },
            title = { Mono("Revert", color = colors.foreground) },
            text = { Mono("Revert to ${formatVersionLabel(record.version)}? This writes a new version.") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        val result = vm.controller.revertVersion(record)
                        revert = null
                        if (result is ActionResult.Error) vm.controller.effect(app.cinderblock.domain.UiEffect.Toast(result.message))
                    }
                }) { Mono("Revert", color = colors.destructive) }
            },
            dismissButton = { TextButton({ revert = null }) { Mono("Cancel") } },
        )
    }
}

@Composable
private fun VersionHistory(vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<app.cinderblock.domain.ProgramVersionRecord?>(null) }
    CardBlock {
        Row(Modifier.fillMaxWidth().clickable { scope.launch { vm.controller.toggleVersionHistory() } }, horizontalArrangement = Arrangement.SpaceBetween) {
            DisplayText("Program Version History", size = 14.sp)
            Mono(if (state.versionHistory.expanded) "Hide" else "Show")
        }
        if (state.versionHistory.expanded) {
            when {
                state.auth == null || !state.cloud.configured -> Mono("No version history available")
                state.versionHistory.loading -> Mono("Loading…")
                state.versionHistory.error != null -> Mono(state.versionHistory.error ?: "", color = colors.destructive)
                state.versionHistory.rows.isEmpty() -> Mono("No version history available")
                else -> state.versionHistory.rows.forEach { row ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Mono(formatVersionLabel(row.version), color = colors.foreground)
                            if (row.version == state.program.version) Pill("Current", colors.neonOrange)
                        }
                        Mono(row.createdAt)
                        row.note?.let { Mono(it) }
                        if (row.version != state.program.version) {
                            TextButton({ pending = row }) { Mono("Revert", color = colors.destructive) }
                        }
                    }
                }
            }
        }
    }
    pending?.let { record ->
        AlertDialog(
            onDismissRequest = { pending = null },
            confirmButton = {
                TextButton({
                    scope.launch {
                        val result = vm.controller.revertVersion(record)
                        pending = null
                        if (result is ActionResult.Error) vm.controller.effect(app.cinderblock.domain.UiEffect.Toast("Failed to revert version"))
                    }
                }) { Mono("Revert", color = colors.destructive) }
            },
            dismissButton = { TextButton({ pending = null }) { Mono("Cancel") } },
            title = { Mono("Revert this version?") },
            text = { Mono("Reverted from ${formatVersionLabel(record.version)}") },
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val colors = LocalColors.current
    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DisplayText(value, size = 18.sp, color = colors.neonOrange)
        Mono(label)
    }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Mono(" $label", size = 10.sp)
    }
}

@Composable
fun PickerScreen(date: String, nav: NavHostController, vm: CinderViewModel) {
    val parsed = parseDate(date)
    LaunchedEffect(date) { if (parsed == null) nav.navigate(Routes.gym(null)) }
    if (parsed == null) return
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val existing = state.gymLog[date]
    var selected by remember(date) { mutableStateOf(existing?.workoutKey) }
    val last = app.cinderblock.domain.lastNonRest(state.gymLog)
    Column(Modifier.fillMaxSize().background(colors.background)) {
        FeatureBar("TRAINING", { nav.goHome() }, date.substring(5))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton({ nav.navigate(Routes.gym(date)) }) { Mono("‹ Back", color = colors.neonOrange) }
            DisplayText("What's today's workout?", size = 22.sp)
            Mono(formatLongDate(parsed))
            state.program.workouts.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (key, workout) ->
                        val on = selected == key
                        CardBlock(Modifier.weight(1f).clickable {
                            vm.controller.haptic(HapticKind.Selection)
                            selected = key
                        }) {
                            if (on) Mono("●", color = if (colorGroup(state.program, key) == "upper") colors.neonOrange else colors.neonAmber)
                            DisplayText(workout.name.uppercase(), size = 14.sp)
                            Mono(scheduleHint(state.program, key))
                            Mono("${workout.exercises.size} exercises")
                        }
                    }
                }
            }
            val restOn = selected == REST_KEY
            CardBlock(Modifier.clickable { selected = REST_KEY }) {
                DisplayText(if (restOn) "REST DAY" else "REST DAY", size = 16.sp, color = if (restOn) colors.neonOrange else colors.mutedForeground)
            }
            last?.let { (lastDate, lastLog) ->
                DisplayText("Last workout", size = 14.sp)
                CardBlock {
                    Mono(formatMediumDate(parseDate(lastDate) ?: parsed))
                    Mono(daySummary(state.program, lastLog), color = colors.foreground)
                    val review = dayStatus(lastLog) == DayStatus.Complete
                    OutlineButton(if (review) "Review" else "Continue", { nav.navigate(Routes.workout(lastDate)) })
                }
            }
            CardBlock {
                DisplayText("GLOBAL NOTES", size = 12.sp)
                state.program.globalNotes.take(3).forEach { Mono("› $it") }
            }
        }
        val resume = selected != null && selected != REST_KEY && existing?.workoutKey == selected
        val count = selected?.let { state.program.workouts[it]?.exercises?.size } ?: 0
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            selected?.let {
                Mono(
                    when {
                        it == REST_KEY -> "Log rest day"
                        resume -> "$count exercises · Resume session"
                        else -> "$count exercises · Start fresh"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    align = TextAlign.Center,
                )
            }
            PrimaryButton(
                when {
                    selected == null -> "SELECT A WORKOUT"
                    selected == REST_KEY -> "LOG REST DAY"
                    resume -> "RESUME WORKOUT →"
                    else -> "START WORKOUT →"
                },
                enabled = selected != null,
                onClick = {
                    val key = selected ?: return@PrimaryButton
                    scope.launch {
                        vm.controller.haptic(HapticKind.Success)
                        vm.controller.selectWorkout(date, key)
                        if (key == REST_KEY) nav.navigate(Routes.gym(date)) else nav.navigate(Routes.workout(date))
                    }
                },
            )
        }
    }
}

@Composable
fun ExploreScreen(key: String?, nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now().toString()
    Column(Modifier.fillMaxSize().background(colors.background)) {
        FeatureBar("EXPLORE", { nav.goHome() }, trailing = "Preview")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (key == null) {
                TextButton({ nav.navigate(Routes.gym(null)) }) { Mono("‹ Training log", color = colors.neonOrange) }
                DisplayText("EXPLORE WORKOUT", size = 24.sp)
                Mono(state.program.name)
                OutlineButton("Edit", { nav.navigate(Routes.Editor) })
                state.program.workouts.forEach { (workoutKey, workout) ->
                    CardBlock(Modifier.clickable { nav.navigate(Routes.explore(workoutKey)) }) {
                        DisplayText(workout.name, size = 16.sp)
                        Mono(scheduleHint(state.program, workoutKey))
                        Mono("${workout.exercises.size} exercises")
                    }
                }
            } else if (!isProgramKey(state.program, key)) {
                Mono("That workout is not in the active program.")
                OutlineButton("All workouts", { nav.navigate(Routes.Explore) })
            } else {
                val workout = state.program.workouts.getValue(key)
                TextButton({ nav.navigate(Routes.Explore) }) { Mono("‹ All workouts", color = colors.neonOrange) }
                Mono("Explore", color = colors.neonOrange)
                DisplayText(workout.name, size = 24.sp)
                Mono(scheduleHint(state.program, key))
                Mono("${workout.exercises.size} exercises")
                workout.exercises.forEachIndexed { index, exercise ->
                    var url by remember(exercise.name) { mutableStateOf(state.videos[exercise.name].orEmpty()) }
                    var urlError by remember(exercise.name) { mutableStateOf<String?>(null) }
                    CardBlock {
                        Mono("%02d".format(index + 1), color = colors.neonOrange)
                        DisplayText(exercise.name, size = 16.sp)
                        Mono(buildString {
                            append("${exercise.sets} sets")
                            exercise.reps?.let { append(" · $it") }
                            exercise.duration?.let { append(" · $it") }
                        })
                        if (exercise.muscles.isNotEmpty()) Mono(exercise.muscles.joinToString(" · "))
                        exercise.notes.forEach { Mono("· $it") }
                        exercise.refVideo?.let { video ->
                            TextButton({
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video)))
                            }) { Mono("Program video", color = colors.neonOrange) }
                        }
                        OutlinedTextField(url, { url = it; urlError = null }, placeholder = { Mono("https://youtube.com/shorts/...") }, label = { Mono("Your video") }, modifier = Modifier.fillMaxWidth())
                        urlError?.let { Mono(it, color = colors.destructive) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                OutlineButton("Save", {
                                    if (url.isBlank() || isHttpUrl(url)) {
                                        scope.launch { vm.controller.setVideo(exercise.name, url.ifBlank { null }) }
                                        urlError = null
                                    } else {
                                        urlError = "Enter a valid http or https link"
                                    }
                                })
                            }
                            Box(Modifier.weight(1f)) {
                                OutlineButton("Clear", {
                                    url = ""
                                    scope.launch { vm.controller.setVideo(exercise.name, null) }
                                })
                            }
                        }
                    }
                }
                Mono("Preview only. No sets are logged.")
                PrimaryButton("Start training session", { nav.navigate(Routes.select(today)) })
            }
        }
    }
}
