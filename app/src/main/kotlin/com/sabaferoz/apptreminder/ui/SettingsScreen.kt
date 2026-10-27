package com.sabaferoz.apptreminder.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sabaferoz.apptreminder.calendar.AuthOutcome
import com.sabaferoz.apptreminder.core.CalendarListEntry
import com.sabaferoz.apptreminder.core.Emails
import com.sabaferoz.apptreminder.core.MessageTemplates
import com.sabaferoz.apptreminder.core.QuietHours
import com.sabaferoz.apptreminder.core.ReminderKind
import com.sabaferoz.apptreminder.core.TimeZoneResolver
import com.sabaferoz.apptreminder.core.WhatsAppApp
import com.sabaferoz.apptreminder.whatsapp.WhatsAppHandoff
import com.sabaferoz.apptreminder.work.Notifier
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val patients by vm.patients.collectAsState()
    val calendars by vm.calendars.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rs = s.reminder
    var pickCalendar by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmGoogle by remember { mutableStateOf(false) }
    var pickZone by remember { mutableStateOf(false) }
    val zones = remember { vm.zones.allIanaZones() }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.onConsentResult(it.data) }
    LaunchedEffect(s.googleConnected) { if (s.googleConnected) vm.loadCalendarsSilently() }

    val connect: () -> Unit = {
        scope.launch {
            when (val o = vm.beginAuth()) {
                is AuthOutcome.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(o.pendingIntent.intentSender).build())
                is AuthOutcome.Token -> vm.finishConnect(o.accessToken)
                is AuthOutcome.Failed -> vm.toast(o.reason)
            }
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Section("Data source") {
            LabeledSwitch("Demo mode (fictional data, no Google)", s.demoMode) { on -> if (!on) vm.setDemoMode(false) else vm.setDemoMode(true) }
            if (s.demoMode) {
                var number by remember { mutableStateOf("") }
                Text("Demo patients have no phone number so nothing can be sent to a stranger. To test the WhatsApp hand-off, enter ONLY your own number:", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(number, { number = it }, label = { Text("My test number (+country code)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.setDemoPhone(number) }) { Text("Apply to demo patients") }
                    OutlinedButton({ vm.regenerateDemo() }) { Text("Regenerate demo data") }
                }
                val demoPatients = patients.filter { it.isDemo }
                var minutes by remember { mutableStateOf("10") }
                var who by remember { mutableStateOf<String?>(null) }
                var pickWho by remember { mutableStateOf(false) }
                OutlinedButton({ pickWho = true }, Modifier.fillMaxWidth()) { Text(demoPatients.firstOrNull { it.email == who }?.alias ?: "Test appointment for… (choose demo patient)") }
                NumberField("Test appointment starts in (minutes)", minutes.toLongOrNull() ?: 10, { minutes = it.toString() }, Modifier.fillMaxWidth())
                Button({ who?.let { vm.addTestAppointment(it, minutes.toLongOrNull() ?: 10) } }, enabled = who != null) { Text("Add test appointment") }
                Text("Tip: set Reminder B to “5 min (test)” below, then add a test appointment 10 minutes ahead.", style = MaterialTheme.typography.bodySmall)
                if (pickWho) PickerDialog("Demo patient", demoPatients, { it.alias }, { who = it.email; pickWho = false }, { pickWho = false })
            }
        }

        Section("Google Calendar (read-only)") {
            Text(
                if (s.googleConnected) "Connected. Calendar: ${s.calendarName ?: "primary"}" else "Not connected.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "The app only requests read-only access to events on calendars you own, plus the read-only calendar list. " +
                    "Google scopes apply to your whole account – choosing one calendar here limits what this app asks for, not what the permission technically allows. " +
                    "The app never creates, edits or deletes events.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!s.googleConnected) Button({ if (s.demoMode) confirmGoogle = true else connect() }) { Text("Connect Google Calendar") }
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ pickCalendar = true }) { Text("Select calendar") }
                    OutlinedButton({ connect() }) { Text("Reconnect") }
                    TextButton({ confirmDisconnect = true }) { Text("Disconnect") }
                }
            }
        }

        Section("Reminders (global defaults)") {
            LabeledSwitch("Reminders enabled", rs.remindersEnabled) { v -> vm.updateSettings { it.copy(reminder = it.reminder.copy(remindersEnabled = v)) } }
            OutlinedButton({ pickZone = true }, Modifier.fillMaxWidth()) { Text("Scheduling time zone for fixed times: ${rs.scheduleZoneId}") }
        }
        Section("Reminder A – day before") {
            RuleEditor(ReminderKind.A, rs.ruleA, rs.scheduleZoneId) { r -> vm.updateSettings { it.copy(reminder = it.reminder.copy(ruleA = r)) } }
        }
        Section("Reminder B – same day") {
            RuleEditor(ReminderKind.B, rs.ruleB, rs.scheduleZoneId) { r -> vm.updateSettings { it.copy(reminder = it.reminder.copy(ruleB = r)) } }
        }

        Section("Patient-local quiet hours") {
            val q = rs.quietHours
            LabeledSwitch("Avoid night-time reminders", q.enabled) { v -> vm.updateSettings { it.copy(reminder = it.reminder.copy(quietHours = q.copy(enabled = v))) } }
            if (q.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("From", q.startMinutes, { v -> vm.updateSettings { it.copy(reminder = it.reminder.copy(quietHours = q.copy(startMinutes = v))) } }, Modifier.weight(1f))
                    TimeField("Until", q.endMinutes, { v -> vm.updateSettings { it.copy(reminder = it.reminder.copy(quietHours = q.copy(endMinutes = v))) } }, Modifier.weight(1f))
                }
                Text("A reminder inside this window (in the patient's time) moves to the end of the window if that is still before the appointment; otherwise it is skipped and the reason is recorded.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Section("Default messages") {
            var a by remember(rs.templateA) { mutableStateOf(rs.templateA) }
            TemplateEditor("Reminder A template", a, { a = it; if (MessageTemplatesOk(it)) vm.updateSettings { s0 -> s0.copy(reminder = s0.reminder.copy(templateA = it)) } }, "Europe/Helsinki")
            TextButton({ a = MessageTemplates.DEFAULT_A; vm.updateSettings { it.copy(reminder = it.reminder.copy(templateA = MessageTemplates.DEFAULT_A)) } }) { Text("Restore default A") }
            var b by remember(rs.templateB) { mutableStateOf(rs.templateB) }
            TemplateEditor("Reminder B template", b, { b = it; if (MessageTemplatesOk(it)) vm.updateSettings { s0 -> s0.copy(reminder = s0.reminder.copy(templateB = it)) } }, "Europe/Helsinki")
            TextButton({ b = MessageTemplates.DEFAULT_B; vm.updateSettings { it.copy(reminder = it.reminder.copy(templateB = MessageTemplates.DEFAULT_B)) } }) { Text("Restore default B") }
            Text("Do not put diagnoses or therapy details in messages. WhatsApp sends the text to the patient's number.", style = MaterialTheme.typography.bodySmall)
        }

        Section("WhatsApp") {
            RadioGroup(
                WhatsAppApp.entries.map { it to (it.label + if (WhatsAppHandoff.isInstalled(ctx, it)) " (installed)" else " (not installed)") },
                s.whatsApp,
            ) { app -> vm.updateSettings { it.copy(whatsApp = app) } }
            Text("The app opens the chat with the message prefilled. You always press Send yourself.", style = MaterialTheme.typography.bodySmall)
        }

        Section("Staff emails") {
            var text by remember(s.staffEmails) { mutableStateOf(s.staffEmails.joinToString(", ")) }
            OutlinedTextField(text, { text = it }, label = { Text("Your / the psychiatrist's emails (never treated as patients)") }, modifier = Modifier.fillMaxWidth())
            Button({ vm.updateSettings { it.copy(staffEmails = text.split(',', ';', ' ').map(Emails::normalize).filter(Emails::isValid)) } }) { Text("Save") }
        }

        Section("Notifications & battery") {
            Text(if (Notifier.canNotify(ctx)) "Notifications are allowed." else "Notifications are blocked: reminders will only show inside the app.")
            OutlinedButton({
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }) { Text("Open notification settings") }
            Text(
                "Android may delay background work to save battery. If reminders arrive late, exclude this app from battery optimisation. " +
                    "Opening the app always refreshes everything.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Battery optimisation settings") }
        }

        Section("Privacy") {
            Text("Everything stays on this phone (encrypted). Only Google Calendar requests and the WhatsApp hand-off you start leave the device.", style = MaterialTheme.typography.bodySmall)
            TextButton({ confirmWipe = true }) { Text("Delete all local data", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (pickCalendar) PickerDialog("Appointment calendar", calendars, { it.summary ?: it.id }, { c: CalendarListEntry -> vm.selectCalendar(c); pickCalendar = false }, { pickCalendar = false })
    if (pickZone) PickerDialog("Scheduling time zone", zones, { it }, { z: String -> vm.updateSettings { it.copy(reminder = it.reminder.copy(scheduleZoneId = z)) }; pickZone = false }, { pickZone = false })

    if (confirmGoogle) AlertDialog(
        onDismissRequest = { confirmGoogle = false },
        title = { Text("Leave demo mode?") },
        text = { Text("Fictional demo patients and appointments will be removed. Real patients you added stay.") },
        confirmButton = { TextButton({ confirmGoogle = false; connect() }) { Text("Continue") } },
        dismissButton = { TextButton({ confirmGoogle = false }) { Text("Cancel") } },
    )
    if (confirmDisconnect) AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        title = { Text("Disconnect Google?") },
        text = { Text("Access is revoked at Google and synced appointments are deleted from this phone. Patients stay.") },
        confirmButton = { TextButton({ confirmDisconnect = false; vm.disconnectGoogle() }) { Text("Disconnect") } },
        dismissButton = { TextButton({ confirmDisconnect = false }) { Text("Cancel") } },
    )
    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Delete all data?") },
        text = { Text("Patients, appointments, reminders, history and settings are erased from this phone. Google access is not revoked (use Disconnect first).") },
        confirmButton = { TextButton({ confirmWipe = false; vm.wipeEverything() }) { Text("Delete everything") } },
        dismissButton = { TextButton({ confirmWipe = false }) { Text("Cancel") } },
    )
}

/** Only persist templates without unknown placeholders so a typo never reaches the send path. */
private fun MessageTemplatesOk(t: String) =
    com.sabaferoz.apptreminder.core.MessageRenderer.validateTemplate(t).none { it.startsWith("Unknown") || it.startsWith("Template is empty") }
