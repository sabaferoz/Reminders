package com.sabaferoz.apptreminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.sabaferoz.apptreminder.core.Country
import com.sabaferoz.apptreminder.core.MessageRenderer
import com.sabaferoz.apptreminder.core.PhoneValidator
import com.sabaferoz.apptreminder.core.RegionOption
import com.sabaferoz.apptreminder.core.ReminderKind
import com.sabaferoz.apptreminder.core.ReminderRule
import com.sabaferoz.apptreminder.core.MessageTemplates
import com.sabaferoz.apptreminder.core.TimeZoneResolver
import com.sabaferoz.apptreminder.core.ZoneResolution
import com.sabaferoz.apptreminder.data.AppJson
import com.sabaferoz.apptreminder.data.PatientEntity
import com.sabaferoz.apptreminder.data.ruleToJson
import java.time.Instant
import java.time.ZoneId

@Composable
fun PatientsScreen(vm: MainViewModel, onEdit: (Long) -> Unit) {
    val patients by vm.patients.collectAsState()
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Patients", style = MaterialTheme.typography.headlineSmall)
                Button({ onEdit(-1) }) { Icon(Icons.Filled.Add, null); Text(" Add") }
            }
        }
        item { Text("Stored only on this phone, encrypted. The phone's contacts are never accessed.", style = MaterialTheme.typography.bodySmall) }
        items(patients, key = { it.id }) { p ->
            Section(p.alias, Modifier.clickable { onEdit(p.id) }) {
                Text("${p.timeZoneId} · ${if (p.phone.isBlank()) "no number" else PhoneValidator.masked(p.phone)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (p.consent) "Reminder consent recorded" else "No consent – reminders are off",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (p.consent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
                if (p.overrideAJson != null || p.overrideBJson != null || p.templateA != null || p.templateB != null) {
                    Text("Has custom reminder settings", style = MaterialTheme.typography.bodySmall)
                }
                if (p.isDemo) Text("Demo patient", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private enum class OverrideMode { GLOBAL, CUSTOM, DISABLED }

@Composable
fun PatientEditScreen(vm: MainViewModel, id: Long, onDone: () -> Unit) {
    val patients by vm.patients.collectAsState()
    val settings by vm.settings.collectAsState()
    val existing = remember(id, patients.size) { patients.firstOrNull { it.id == id } }
    val rs = settings.reminder

    var alias by remember(existing?.id) { mutableStateOf(existing?.alias ?: "") }
    var email by remember(existing?.id) { mutableStateOf(existing?.email ?: "") }
    var phone by remember(existing?.id) { mutableStateOf(existing?.phone ?: "") }
    var iso by remember(existing?.id) { mutableStateOf(existing?.countryIso ?: "") }
    var region by remember(existing?.id) { mutableStateOf(existing?.region ?: "") }
    var tz by remember(existing?.id) { mutableStateOf(existing?.timeZoneId ?: "") }
    var consent by remember(existing?.id) { mutableStateOf(existing?.consent ?: false) }
    var ovA by remember(existing?.id) { mutableStateOf(existing?.overrideAJson?.let { AppJson.decodeFromString<ReminderRule>(it) }) }
    var ovB by remember(existing?.id) { mutableStateOf(existing?.overrideBJson?.let { AppJson.decodeFromString<ReminderRule>(it) }) }
    var tA by remember(existing?.id) { mutableStateOf(existing?.templateA) }
    var tB by remember(existing?.id) { mutableStateOf(existing?.templateB) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    var pickCountry by remember { mutableStateOf(false) }
    var pickRegion by remember { mutableStateOf<List<RegionOption>?>(null) }
    var pickIana by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val countries = remember { vm.zones.countries() }
    val iana = remember { vm.zones.allIanaZones() }
    val resolution = remember(iso) { if (iso.isBlank()) null else vm.zones.resolve(iso) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (existing == null) "Add patient" else "Edit patient", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(alias, { alias = it }, label = { Text("Name or alias") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email, { email = it }, label = { Text("Google Calendar attendee email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            phone, { phone = it }, label = { Text("WhatsApp number with country code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("+358 40 123 4567") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = phone.isNotBlank() && PhoneValidator.normalize(phone) == null,
        )

        Section("Location & time zone") {
            OutlinedButton({ pickCountry = true }, Modifier.fillMaxWidth()) {
                Text(countries.firstOrNull { it.iso == iso }?.name ?: "Select country")
            }
            if (resolution is ZoneResolution.NeedsRegion) {
                OutlinedButton({ pickRegion = resolution.options }, Modifier.fillMaxWidth()) {
                    Text(if (region.isBlank()) "This country has several time zones – select state / region / city" else region)
                }
            }
            if (resolution is ZoneResolution.Unknown) Text("No built-in data for this country: search the time zone below.", style = MaterialTheme.typography.bodySmall)
            if (tz.isNotBlank() && TimeZoneResolver.valid(tz)) {
                Text("Time zone: " + MessageRenderer.zoneLabel(Instant.now(), ZoneId.of(tz)) + " (now)", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton({ pickIana = true }) { Text("Search all time zones…") }
            Text("Never guessed from the phone number.", style = MaterialTheme.typography.bodySmall)
        }

        Section("Consent") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consent, { consent = it })
                Text("The patient agreed to receive appointment reminders on WhatsApp.", Modifier.weight(1f))
            }
            Text("Reminders stay off until consent is recorded.", style = MaterialTheme.typography.bodySmall)
        }

        for (kind in ReminderKind.entries) {
            val ov = if (kind == ReminderKind.A) ovA else ovB
            val setOv = { r: ReminderRule? -> if (kind == ReminderKind.A) ovA = r else ovB = r }
            val mode = when { ov == null -> OverrideMode.GLOBAL; !ov.enabled -> OverrideMode.DISABLED; else -> OverrideMode.CUSTOM }
            Section("${kind.label} for this patient") {
                RadioGroup(
                    listOf(OverrideMode.GLOBAL to "Use global defaults", OverrideMode.CUSTOM to "Custom for this patient", OverrideMode.DISABLED to "Disabled for this patient"),
                    mode,
                ) { m ->
                    setOv(when (m) { OverrideMode.GLOBAL -> null; OverrideMode.DISABLED -> ReminderRule(enabled = false); OverrideMode.CUSTOM -> if (ov != null && ov.enabled) ov else rs.rule(kind).copy(enabled = true) })
                }
                if (mode == OverrideMode.CUSTOM && ov != null) {
                    RuleEditor(kind, ov, rs.scheduleZoneId) { setOv(it) }
                    TextButton({ setOv(null) }) { Text("Reset to default") }
                }
            }
        }

        for (kind in ReminderKind.entries) {
            val t = if (kind == ReminderKind.A) tA else tB
            val set = { v: String? -> if (kind == ReminderKind.A) tA = v else tB = v }
            Section("${kind.label} message") {
                if (t == null) {
                    Text("Using the global template.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton({ set(rs.template(kind)) }) { Text("Customize for this patient") }
                } else {
                    TemplateEditor("Custom ${kind.label} message", t, { set(it) }, tz.ifBlank { "Europe/Helsinki" }, alias.ifBlank { "Sample patient" })
                    TextButton({ set(null) }) { Text("Restore global template") }
                }
            }
        }

        if (errors.isNotEmpty()) errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                val draft = PatientEntity(
                    id = existing?.id ?: 0, alias = alias, email = email, phone = phone, countryIso = iso, region = region, timeZoneId = tz,
                    consent = consent, consentAtMs = if (consent) (existing?.consentAtMs ?: System.currentTimeMillis()) else null,
                    overrideAJson = ruleToJson(ovA), overrideBJson = ruleToJson(ovB),
                    templateA = tA, templateB = tB, isDemo = existing?.isDemo ?: false,
                )
                errors = vm.validate(draft)
                if (errors.isEmpty()) vm.savePatient(draft, onDone)
            }) { Text("Save") }
            OutlinedButton(onDone) { Text("Cancel") }
            if (existing != null) TextButton({ confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (pickCountry) PickerDialog("Country", countries, { it.name }, { c: Country ->
        pickCountry = false
        iso = c.iso; region = ""; tz = ""
        when (val r = vm.zones.resolve(c.iso)) {
            is ZoneResolution.Single -> tz = r.zoneId
            is ZoneResolution.NeedsRegion -> pickRegion = r.options
            ZoneResolution.Unknown -> pickIana = true
        }
    }, { pickCountry = false })

    pickRegion?.let { opts ->
        PickerDialog("State / region / city", opts, { it.label }, { o: RegionOption -> region = o.label; tz = o.zoneId; pickRegion = null }, { pickRegion = null })
    }
    if (pickIana) PickerDialog("Time zone", iana, { it }, { z: String -> tz = z; pickIana = false }, { pickIana = false })

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this patient?") },
        text = { Text("The record and all of their reminders and history are removed from this phone.") },
        confirmButton = { TextButton({ existing?.let { vm.deletePatient(it.id) }; confirmDelete = false; onDone() }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}
