package app.cinderblock.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.cinderblock.domain.ActionResult
import app.cinderblock.domain.ExerciseCategory
import app.cinderblock.domain.ProgramDocument
import app.cinderblock.domain.ProgramExercise
import app.cinderblock.domain.bumpVersion
import app.cinderblock.domain.categoryLabel
import app.cinderblock.domain.formatVersionLabel
import app.cinderblock.domain.isTimedHold
import app.cinderblock.domain.presetToExercise
import app.cinderblock.domain.searchPresets
import app.cinderblock.domain.validateProgram
import app.cinderblock.domain.workoutKeys
import app.cinderblock.ui.components.CardBlock
import app.cinderblock.ui.components.DisplayText
import app.cinderblock.ui.components.FeatureBar
import app.cinderblock.ui.components.Mono
import app.cinderblock.ui.components.OutlineButton
import app.cinderblock.ui.components.PrimaryButton
import app.cinderblock.ui.nav.goHome
import app.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    var draft by remember(state.program.version, state.planUpdatedAt) { mutableStateOf(state.program) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var pendingRemove by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dirty = draft != state.program
    val issue = if (dirty) validateProgram(draft) else null
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            FeatureBar(
                "EDIT PROGRAM",
                { nav.goHome() },
                end = {
                    TextButton(
                        {
                            if (dirty && !saving) {
                                scope.launch {
                                    saving = true
                                    error = null
                                    val result = vm.controller.saveProgram(draft, "Manual program edit", "manual")
                                    saving = false
                                    if (result is ActionResult.Error) error = result.message
                                }
                            }
                        },
                        enabled = dirty && !saving,
                    ) { Mono(if (saving) "Saving..." else "Save", color = if (dirty) colors.neonOrange else colors.mutedForeground) }
                },
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    draft.name,
                    { draft = draft.copy(name = it) },
                    label = { Mono("Program Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mono("Current version: ${formatVersionLabel(state.program.version)}", color = colors.foreground)
                    if (dirty) Mono("→ ${formatVersionLabel(bumpVersion(state.program.version))}", color = colors.neonYellow)
                }
                (error ?: issue?.message)?.let { Mono(it, color = colors.destructive) }
                workoutKeys(draft).forEach { key ->
                    val workout = draft.workouts[key]
                    if (workout != null) {
                    val open = key in expanded
                    CardBlock {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                expanded = if (open) expanded - key else expanded + key
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier.size(10.dp).background(
                                    if (key.contains("upper", ignoreCase = true)) colors.neonOrange else colors.neonAmber,
                                    CircleShape,
                                ),
                            )
                            Column(Modifier.weight(1f)) {
                                DisplayText(workout.name.ifBlank { key }, size = 16.sp)
                                Mono("${workout.exercises.size} exercises")
                            }
                            Mono(if (open) "–" else "+")
                        }
                        if (open) {
                            if (workout.exercises.isEmpty()) Mono("No exercises yet")
                            workout.exercises.forEachIndexed { index, exercise ->
                                val openExercise = editing == key to index
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton({ moveExercise(draft, key, index, -1)?.let { draft = it } }, enabled = index > 0) { Mono("↑") }
                                        TextButton({
                                            moveExercise(draft, key, index, 1)?.let { draft = it }
                                        }, enabled = index < workout.exercises.lastIndex) { Mono("↓") }
                                        Column(Modifier.weight(1f).clickable { editing = if (openExercise) null else key to index }) {
                                            Mono(exercise.name, color = colors.foreground)
                                            Mono(exerciseLine(exercise))
                                        }
                                        TextButton({ pendingRemove = key to index }) { Mono("Trash", color = colors.destructive) }
                                    }
                                    if (openExercise) {
                                        ExerciseFields(exercise) { updated ->
                                            draft = replaceExercise(draft, key, index, updated)
                                        }
                                    }
                                }
                            }
                            OutlineButton("Add Exercise", { adding = key })
                        }
                    }
                    }
                }
            }
        }
        if (dirty) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(colors.card, RoundedCornerShape(8.dp))
                    .border(1.dp, colors.neonYellow, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) { Mono("You have unsaved changes", color = colors.neonYellow) }
        }
        adding?.let { key ->
            AddExerciseSheet(
                onClose = { adding = null },
                onAdd = { exercise ->
                    val workout = draft.workouts[key] ?: return@AddExerciseSheet
                    val next = workout.copy(exercises = workout.exercises + exercise)
                    draft = draft.copy(workouts = draft.workouts.toMutableMap().also { it[key] = next })
                    adding = null
                },
            )
        }
    }
    pendingRemove?.let { (key, index) ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Mono("Remove this exercise?", color = colors.foreground) },
            confirmButton = {
                TextButton({
                    draft = removeExercise(draft, key, index)
                    if (editing?.first == key) editing = null
                    pendingRemove = null
                }) { Mono("Remove", color = colors.destructive) }
            },
            dismissButton = { TextButton({ pendingRemove = null }) { Mono("Cancel") } },
        )
    }
}

@Composable
private fun ExerciseFields(exercise: ProgramExercise, onChange: (ProgramExercise) -> Unit) {
    var muscles by remember(exercise) { mutableStateOf(exercise.muscles.joinToString(", ")) }
    var notes by remember(exercise) { mutableStateOf(exercise.notes.joinToString(", ")) }
    OutlinedTextField(
        exercise.sets.toString(),
        { raw -> raw.toIntOrNull()?.let { onChange(exercise.copy(sets = it.coerceIn(1, 10))) } },
        label = { Mono("Sets") },
        modifier = Modifier.fillMaxWidth(),
    )
    if (isTimedHold(exercise)) {
        OutlinedTextField(
            exercise.duration.orEmpty(),
            { onChange(exercise.copy(duration = it, reps = null)) },
            label = { Mono("Duration") },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OutlinedTextField(
            exercise.reps.orEmpty(),
            { onChange(exercise.copy(reps = it, duration = null)) },
            label = { Mono("Reps") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        muscles,
        {
            muscles = it
            onChange(exercise.copy(muscles = splitList(it)))
        },
        label = { Mono("Muscles") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        notes,
        {
            notes = it
            onChange(exercise.copy(notes = splitList(it)))
        },
        label = { Mono("Notes") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        exercise.refVideo.orEmpty(),
        { onChange(exercise.copy(refVideo = it.ifBlank { null })) },
        label = { Mono("Reference video URL") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExerciseSheet(onClose: () -> Unit, onAdd: (ProgramExercise) -> Unit) {
    val colors = LocalColors.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ExerciseCategory?>(null) }
    var custom by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("8-12") }
    var timed by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("30-60 seconds") }
    var muscles by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.72f)).clickable { onClose() })
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .background(colors.card, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DisplayText(if (custom) "Create Custom Exercise" else "Add Exercise", size = 18.sp)
                TextButton(onClose) { Mono("Close") }
            }
            if (!custom) {
                OutlinedTextField(query, { query = it }, placeholder = { Mono("Search exercises...") }, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton({ category = null }) { Mono("All", color = if (category == null) colors.neonOrange else colors.mutedForeground) }
                    ExerciseCategory.entries.forEach { item ->
                        TextButton({ category = item }) {
                            Mono(categoryLabel(item), color = if (category == item) colors.neonOrange else colors.mutedForeground)
                        }
                    }
                }
                searchPresets(query, category).forEach { preset ->
                    CardBlock(Modifier.clickable { onAdd(presetToExercise(preset)) }) {
                        Mono(preset.name, color = colors.foreground)
                        Mono(preset.reps ?: preset.duration ?: "")
                    }
                }
                if (category == ExerciseCategory.Cardio && searchPresets(query, category).isEmpty()) {
                    Mono("No cardio presets yet. Create a custom exercise.")
                }
                OutlineButton("Create custom", { custom = true })
            } else {
                OutlinedTextField(name, { name = it }, label = { Mono("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sets, { sets = it }, label = { Mono("Sets") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Mono("Timed hold", color = colors.foreground)
                    Switch(timed, { timed = it })
                }
                if (timed) {
                    OutlinedTextField(duration, { duration = it }, label = { Mono("Duration") }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(reps, { reps = it }, label = { Mono("Reps") }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(muscles, { muscles = it }, label = { Mono("Muscles") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Mono("Notes") }, modifier = Modifier.fillMaxWidth())
                PrimaryButton("Add", {
                    if (name.isNotBlank()) {
                        onAdd(
                            ProgramExercise(
                                name = name.trim(),
                                sets = (sets.toIntOrNull() ?: 1).coerceIn(1, 10),
                                reps = if (timed) null else reps,
                                duration = if (timed) duration else null,
                                muscles = splitList(muscles),
                                notes = splitList(notes),
                            ),
                        )
                    }
                })
            }
        }
    }
}

private fun splitList(value: String): List<String> =
    value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

private fun exerciseLine(exercise: ProgramExercise): String =
    if (isTimedHold(exercise)) "${exercise.sets} × ${exercise.duration}" else "${exercise.sets} × ${exercise.reps ?: "—"}"

private fun replaceExercise(program: ProgramDocument, key: String, index: Int, exercise: ProgramExercise): ProgramDocument {
    val workout = program.workouts[key] ?: return program
    val next = workout.exercises.toMutableList()
    if (index !in next.indices) return program
    next[index] = exercise
    return program.copy(workouts = program.workouts.toMutableMap().also { it[key] = workout.copy(exercises = next) })
}

private fun removeExercise(program: ProgramDocument, key: String, index: Int): ProgramDocument {
    val workout = program.workouts[key] ?: return program
    val next = workout.exercises.filterIndexed { position, _ -> position != index }
    return program.copy(workouts = program.workouts.toMutableMap().also { it[key] = workout.copy(exercises = next) })
}

private fun moveExercise(program: ProgramDocument, key: String, index: Int, delta: Int): ProgramDocument? {
    val workout = program.workouts[key] ?: return null
    val target = index + delta
    if (target !in workout.exercises.indices) return null
    val next = workout.exercises.toMutableList()
    val item = next.removeAt(index)
    next.add(target, item)
    return program.copy(workouts = program.workouts.toMutableMap().also { it[key] = workout.copy(exercises = next) })
}
