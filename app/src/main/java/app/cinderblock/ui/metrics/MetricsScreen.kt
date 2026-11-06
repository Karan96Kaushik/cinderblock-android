package app.cinderblock.ui.metrics

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.cinderblock.CinderViewModel
import app.cinderblock.data.deleteMetric
import app.cinderblock.data.saveMetric
import app.cinderblock.domain.METRIC_FIELDS
import app.cinderblock.domain.MetricEntry
import app.cinderblock.domain.decreaseIsGood
import app.cinderblock.domain.formatDelta
import app.cinderblock.domain.formatMediumDate
import app.cinderblock.domain.hasContent
import app.cinderblock.domain.parseDate
import app.cinderblock.domain.parseMetric
import app.cinderblock.domain.previousMetric
import app.cinderblock.domain.valueOf
import app.cinderblock.domain.withValue
import app.cinderblock.ui.components.CardBlock
import app.cinderblock.ui.components.DisplayText
import app.cinderblock.ui.components.FeatureBar
import app.cinderblock.ui.components.Mono
import app.cinderblock.ui.components.Pill
import app.cinderblock.ui.components.PrimaryButton
import app.cinderblock.ui.nav.goHome
import app.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricsScreen(nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now().toString()
    var selected by remember { mutableStateOf(today) }
    var form by remember { mutableStateOf(state.bodyMetrics.firstOrNull { it.date == today } ?: MetricEntry(date = today)) }
    var historyOpen by remember { mutableStateOf(true) }
    val latest = state.bodyMetrics.maxByOrNull { it.date }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        FeatureBar("BODY METRICS", { nav.goHome() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DisplayText("Body measurements", size = 22.sp)
            Mono("Log weekly. Same measurements, same conditions, make the trend readable.")
            if (latest != null && latest.hasContent()) {
                DisplayText("Latest readings", size = 16.sp)
                METRIC_FIELDS.forEach { field ->
                    val value = latest.valueOf(field.key)
                    if (value.isNotBlank()) {
                    val previous = previousMetric(state.bodyMetrics, latest.date)?.valueOf(field.key)
                    val delta = formatDelta(value, previous, field.unit)
                    val currentNumber = parseMetric(value)
                    val previousNumber = parseMetric(previous)
                    val improved = if (currentNumber != null && previousNumber != null) {
                        val change = currentNumber - previousNumber
                        if (decreaseIsGood(field.key)) change < 0 else change > 0
                    } else {
                        false
                    }
                    CardBlock {
                        Mono(field.label)
                        DisplayText("$value ${field.unit}", size = 22.sp, color = if (improved) colors.neonOrange else colors.foreground)
                        if (delta != null) Mono(delta, color = if (improved) colors.neonOrange else colors.mutedForeground)
                        parseDate(latest.date)?.let { Mono(formatMediumDate(it)) }
                    }
                    }
                }
            }
            DisplayText("Log entry", size = 16.sp)
            OutlinedTextField(
                selected,
                { raw ->
                    val parsed = parseDate(raw)
                    if (parsed != null && !parsed.isAfter(LocalDate.now())) {
                        selected = parsed.toString()
                        form = state.bodyMetrics.firstOrNull { it.date == selected } ?: MetricEntry(date = selected)
                    } else if (raw.length <= 10) {
                        selected = raw
                    }
                },
                label = { Mono("Date") },
                modifier = Modifier.fillMaxWidth(),
            )
            Mono("Tap a history entry below to edit a past date")
            val previous = previousMetric(state.bodyMetrics, form.date)
            METRIC_FIELDS.forEach { field ->
                OutlinedTextField(
                    form.valueOf(field.key),
                    { form = form.withValue(field.key, it) },
                    label = { Mono("${field.label} (${field.unit})") },
                    modifier = Modifier.fillMaxWidth(),
                )
                previous?.valueOf(field.key)?.takeIf { it.isNotBlank() }?.let { prior ->
                    Mono("vs last $prior ${field.unit}")
                }
            }
            OutlinedTextField(
                form.notes,
                { form = form.copy(notes = it) },
                label = { Mono("Notes") },
                placeholder = { Mono("Sleep, energy, diet notes...") },
                modifier = Modifier.fillMaxWidth(),
            )
            DisplayText("History (${state.bodyMetrics.size})", size = 16.sp, modifier = Modifier.clickable { historyOpen = !historyOpen })
            if (historyOpen) {
                if (state.bodyMetrics.isEmpty()) {
                    Mono("No entries yet. Log your first measurements above.")
                } else {
                    state.bodyMetrics.sortedByDescending { it.date }.forEach { entry ->
                        val outlined = entry.date == selected
                        CardBlock(
                            Modifier
                                .border(if (outlined) 1.dp else 0.dp, colors.neonOrange, RoundedCornerShape(10.dp))
                                .clickable {
                                    selected = entry.date
                                    form = entry
                                },
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Mono(parseDate(entry.date)?.let { formatMediumDate(it) } ?: entry.date, color = colors.foreground)
                                    if (entry.date == today) Pill("TODAY", colors.neonOrange)
                                }
                                TextButton({ scope.launch { vm.controller.deleteMetric(entry.date) } }) {
                                    Mono("Delete", color = colors.destructive)
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                METRIC_FIELDS.forEach { field ->
                                    val value = entry.valueOf(field.key)
                                    if (value.isNotBlank()) Pill("${field.label} $value", colors.neonAmber)
                                }
                            }
                            if (entry.notes.isNotBlank()) Mono(entry.notes, color = colors.foreground)
                        }
                    }
                }
            }
            CardBlock {
                DisplayText("Track weekly", size = 16.sp)
                Mono("Weigh and measure at the same time each week.")
                Mono("Measure waist at the navel, relaxed.")
                Mono("A stable or slightly increasing weight supports muscle gain.")
            }
        }
        Box(Modifier.padding(16.dp)) {
            val exists = state.bodyMetrics.any { it.date == form.date }
            PrimaryButton(
                if (exists) "UPDATE ENTRY" else "SAVE ENTRY",
                { scope.launch { vm.controller.saveMetric(form.copy(date = form.date.ifBlank { selected })) } },
                enabled = form.hasContent() && parseDate(form.date) != null,
            )
        }
    }
}
