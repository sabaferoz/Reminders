package com.sabaferoz.apptreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sabaferoz.apptreminder.core.PhoneValidator
import com.sabaferoz.apptreminder.core.ReminderStatus

@Composable
fun RemindersScreen(vm: MainViewModel) {
    val rows by vm.reminderRows.collectAsState()
    val settings by vm.settings.collectAsState()
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<ReminderRow?>(null) }
    var notice by remember { mutableStateOf<ReminderRow?>(null) }
    val zone = settings.reminder.scheduleZoneId

    val order = listOf(
        ReminderStatus.READY to "Ready to send",
        ReminderStatus.WHATSAPP_OPENED to "WhatsApp opened – confirm whether you sent it",
        ReminderStatus.NEEDS_REVIEW to "Needs review",
        ReminderStatus.SCHEDULED to "Upcoming",
    )
    fun open(r: ReminderRow) {
        if (!settings.whatsAppNoticeAcknowledged) notice = r else vm.openWhatsApp(ctx, r.state.key)
    }

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Reminders", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp)) }
        if (rows.none { it.state.status in order.map { o -> o.first } }) item { Text("No reminders yet. They appear once appointments match consenting patients.") }
        order.forEach { (status, title) ->
            val list = rows.filter { it.state.status == status }.sortedBy { it.state.dueAt }
            if (list.isNotEmpty()) {
                item { Text("$title (${list.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(list, key = { "${it.state.key}" }) { r -> ReminderCard(r, zone, vm, onOpen = { open(r) }, onEdit = { editing = r }) }
            }
        }
        item { Text("", Modifier.padding(24.dp)) }
    }

    editing?.let { r ->
        var text by remember(r.state.key) { mutableStateOf(r.preview.removePrefix("⚠ ")) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit this message") },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, minLines = 6, modifier = Modifier.fillMaxWidth())
                    Text("Only this reminder is changed. Check the time and the Meet link before sending.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton({ vm.saveDraft(r.state.key, text); editing = null }) { Text("Save draft") } },
            dismissButton = {
                Row {
                    TextButton({ vm.saveDraft(r.state.key, null); editing = null }) { Text("Discard draft") }
                    TextButton({ editing = null }) { Text("Cancel") }
                }
            },
        )
    }

    notice?.let { r ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("Before you send") },
            text = {
                Text(
                    "WhatsApp will open with the reminder and the Google Meet link prefilled for the patient's number. " +
                        "Sending shares that information with the recipient (via WhatsApp). Check the number, then press Send yourself.",
                )
            },
            confirmButton = {
                TextButton({
                    vm.updateSettings { it.copy(whatsAppNoticeAcknowledged = true) }
                    notice = null
                    vm.openWhatsApp(ctx, r.state.key)
                }) { Text("I understand") }
            },
            dismissButton = { TextButton({ notice = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReminderCard(r: ReminderRow, managerZone: String, vm: MainViewModel, onOpen: () -> Unit, onEdit: () -> Unit) {
    val s = r.state
    var expanded by remember { mutableStateOf(false) }
    Section(r.patient?.alias ?: "Unknown patient") {
        val appt = r.appt
        val pz = r.patient?.timeZoneId
        if (appt != null && pz != null) {
            Text("${s.key.kind.label} · ${fmtWithZone(appt.start, pz)}")
            Text("Manager time: ${fmtWhen(appt.start, managerZone)}", style = MaterialTheme.typography.bodySmall)
        }
        s.dueAt?.let { Text("Ready at: ${fmtWhen(it, managerZone)} ($managerZone)", style = MaterialTheme.typography.bodySmall) }
        r.patient?.let { p ->
            PhoneValidator.normalize(p.phone)?.let { Text("To: ${PhoneValidator.masked(it)}", style = MaterialTheme.typography.bodySmall) }
        }
        StatusLabel(s.status)
        s.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (s.draft != null) Text("Edited draft", style = MaterialTheme.typography.labelSmall)
        Text(
            if (expanded) r.preview else r.preview.lines().take(3).joinToString("\n") + if (r.preview.lines().size > 3) " …" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide message" else "Show full message") }
        when (s.status) {
            ReminderStatus.READY, ReminderStatus.WHATSAPP_OPENED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onOpen) { Text(if (s.status == ReminderStatus.READY) "Open WhatsApp" else "Open again") }
                OutlinedButton(onEdit) { Text("Edit") }
            }
            ReminderStatus.SCHEDULED, ReminderStatus.NEEDS_REVIEW -> OutlinedButton(onEdit) { Text("Edit draft") }
            else -> Unit
        }
        if (s.status == ReminderStatus.WHATSAPP_OPENED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.confirmSent(s.key) }) { Text("Confirm sent") }
                OutlinedButton({ vm.notSent(s.key) }) { Text("Not sent") }
            }
        }
        if (s.status == ReminderStatus.READY || s.status == ReminderStatus.NEEDS_REVIEW || s.status == ReminderStatus.SCHEDULED) {
            if (s.status == ReminderStatus.READY) Button({ vm.confirmSent(s.key) }) { Text("Confirm sent") }
            TextButton({ vm.skip(s.key) }) { Text("Skip") }
        }
    }
}
