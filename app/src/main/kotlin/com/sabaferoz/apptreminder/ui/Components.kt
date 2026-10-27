package com.sabaferoz.apptreminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sabaferoz.apptreminder.core.FixedZoneChoice
import com.sabaferoz.apptreminder.core.MessageContext
import com.sabaferoz.apptreminder.core.MessageRenderer
import com.sabaferoz.apptreminder.core.ReminderKind
import com.sabaferoz.apptreminder.core.ReminderRule
import com.sabaferoz.apptreminder.core.ReminderStatus
import com.sabaferoz.apptreminder.core.ScheduleMode
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
fun <T> RadioGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        options.forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(value) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == value, { onSelect(value) })
                Text(label)
            }
        }
    }
}

@Composable
fun <T> PickerDialog(title: String, items: List<T>, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, items) { if (q.isBlank()) items else items.filter { label(it).contains(q.trim(), ignoreCase = true) || label(it).replace(' ', '_').contains(q.trim().replace(' ', '_'), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(shown) { item ->
                        Text(label(item), Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 10.dp))
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

private fun fmtClock(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
private fun parseClock(s: String): Int? {
    val m = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(s.trim()) ?: return null
    val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) h * 60 + min else null
}

@Composable
fun TimeField(label: String, minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(fmtClock(minutes)) }
    LaunchedEffect(minutes) { if (parseClock(text) != minutes) text = fmtClock(minutes) }
    OutlinedTextField(
        text, { text = it; parseClock(it)?.let(onChange) },
        label = { Text(label) }, singleLine = true, isError = parseClock(text) == null,
        supportingText = { Text("24-hour HH:mm") }, modifier = modifier,
    )
}

@Composable
fun NumberField(label: String, value: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (text.toLongOrNull() != value) text = value.toString() }
    OutlinedTextField(
        text, { text = it.filter(Char::isDigit); text.toLongOrNull()?.let(onChange) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

fun describeMinutes(m: Long): String = when {
    m % 1440 == 0L && m >= 1440 -> "${m / 1440} day(s)"
    m >= 60 && m % 60 == 0L -> "${m / 60} hour(s)"
    m >= 60 -> "${m / 60} h ${m % 60} min"
    else -> "$m min"
}

/** Used for global rules and per-patient overrides. */
@Composable
fun RuleEditor(kind: ReminderKind, rule: ReminderRule, scheduleZoneId: String, onChange: (ReminderRule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LabeledSwitch("${kind.label} enabled", rule.enabled) { onChange(rule.copy(enabled = it)) }
        if (!rule.enabled) return@Column
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(rule.mode == ScheduleMode.RELATIVE, { onChange(rule.copy(mode = ScheduleMode.RELATIVE)) }, { Text("Time before") })
            FilterChip(rule.mode == ScheduleMode.FIXED_TIME, { onChange(rule.copy(mode = ScheduleMode.FIXED_TIME)) }, { Text("Fixed clock time") })
        }
        if (rule.mode == ScheduleMode.RELATIVE) {
            val presets = if (kind == ReminderKind.A) listOf(720L, 1200L, 1440L, 5L) else listOf(30L, 60L, 120L, 180L, 5L)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                presets.forEach { p ->
                    FilterChip(rule.offsetMinutes == p, { onChange(rule.copy(offsetMinutes = p)) }, { Text(if (p == 5L) "5 min (test)" else describeMinutes(p)) })
                }
            }
            NumberField("Minutes before appointment", rule.offsetMinutes, { onChange(rule.copy(offsetMinutes = it)) }, Modifier.fillMaxWidth())
            Text("= ${describeMinutes(rule.offsetMinutes)} before the appointment", style = MaterialTheme.typography.bodySmall)
        } else {
            TimeField(
                if (kind == ReminderKind.A) "Clock time on the day BEFORE the appointment" else "Clock time on the day OF the appointment",
                rule.clockMinutes, { onChange(rule.copy(clockMinutes = it)) }, Modifier.fillMaxWidth(),
            )
            RadioGroup(
                listOf(
                    FixedZoneChoice.SCHEDULE_ZONE to "Manager's scheduling time ($scheduleZoneId)",
                    FixedZoneChoice.PATIENT_LOCAL to "Patient's local time",
                ),
                rule.fixedZone,
            ) { onChange(rule.copy(fixedZone = it)) }
        }
    }
}

@Composable
fun TemplateEditor(
    label: String, text: String, onChange: (String) -> Unit, zoneId: String, patientName: String = "Sample patient",
) {
    val problems = remember(text) { MessageRenderer.validateTemplate(text) }
    val preview = remember(text, zoneId) {
        val start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(15, ChronoUnit.HOURS)
        MessageRenderer.render(text, MessageContext(patientName, start, ZoneId.of(zoneId), "https://meet.google.com/abc-defg-hij")).text
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(text, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = 5)
        Text("Placeholders: " + MessageRenderer.PLACEHOLDERS.joinToString(" ") { "{$it}" }, style = MaterialTheme.typography.bodySmall)
        problems.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text("Preview (sample data):", style = MaterialTheme.typography.labelMedium)
        Text(preview, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun StatusLabel(status: ReminderStatus) {
    val tone = when (status) {
        ReminderStatus.READY -> Tone.GOOD
        ReminderStatus.WHATSAPP_OPENED -> Tone.INFO
        ReminderStatus.NEEDS_REVIEW -> Tone.BAD
        ReminderStatus.SENT_CONFIRMED -> Tone.GOOD
        ReminderStatus.SCHEDULED -> Tone.NEUTRAL
        ReminderStatus.SKIPPED -> Tone.WARN
    }
    Pill(status.label, tone)
}

fun fmtWhen(i: Instant, zoneId: String): String {
    val z = ZoneId.of(zoneId)
    return "${MessageRenderer.formatDate(i, z).substringBefore(',').take(3)} ${i.atZone(z).dayOfMonth} ${i.atZone(z).month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}, ${MessageRenderer.formatTime(i, z)}"
}

/** "Tue 3 Nov, 3:00 PM · Europe/Helsinki, UTC+02:00" */
fun fmtWithZone(i: Instant, zoneId: String): String = "${fmtWhen(i, zoneId)} · ${MessageRenderer.zoneLabel(i, ZoneId.of(zoneId))}"

// ---- readable time helpers & small building blocks --------------------------------------------------------------

fun fmtTime(i: Instant, zoneId: String): String = MessageRenderer.formatTime(i, ZoneId.of(zoneId))

/** "Tue 3 Nov" */
fun fmtDay(i: Instant, zoneId: String): String {
    val z = ZoneId.of(zoneId).let { i.atZone(it) }
    val dow = z.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val mon = z.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$dow ${z.dayOfMonth} $mon"
}

/** "Helsinki (UTC+2)" – short and unambiguous, offset is the one in force on that date. */
fun zoneShort(i: Instant, zoneId: String): String {
    val secs = ZoneId.of(zoneId).rules.getOffset(i).totalSeconds
    val a = kotlin.math.abs(secs)
    val h = a / 3600
    val m = (a % 3600) / 60
    val off = "UTC" + (if (secs < 0) "-" else "+") + h + (if (m != 0) ":%02d".format(m) else "")
    return zoneId.substringAfterLast('/').replace('_', ' ') + " ($off)"
}

@Composable
fun Pill(text: String, tone: Tone = Tone.NEUTRAL) {
    val c = toneColors(tone)
    Surface(color = c.bg, shape = RoundedCornerShape(50)) {
        Text(text, color = c.fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary)
}

/** Two clearly separated times: the patient's (what goes in the message) and yours. */
@Composable
fun TimePair(start: Instant, patientZone: String?, managerZone: String) {
    if (patientZone != null) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fmtTime(start, patientZone), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("patient · ${zoneShort(start, patientZone)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(
        "You · ${fmtTime(start, managerZone)} ${zoneShort(start, managerZone)}",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
