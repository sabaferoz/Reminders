package com.sabaferoz.apptreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val rows by vm.reminderRows.collectAsState()
    val zone = vm.settings.collectAsState().value.reminder.scheduleZoneId
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp)) }
        item { Text("Kept for 30 days after the appointment, then removed automatically. Opening WhatsApp is not proof of sending.", style = MaterialTheme.typography.bodySmall) }
        items(rows.sortedByDescending { it.state.createdAt }, key = { "${it.state.key}" }) { r ->
            val s = r.state
            Section("${r.patient?.alias ?: "Deleted patient"} · ${s.key.kind.label}") {
                r.appt?.let { a -> r.patient?.let { p -> Text("Appointment: ${fmtWithZone(a.start, p.timeZoneId)}", style = MaterialTheme.typography.bodySmall) } }
                StatusLabel(s.status)
                Text("Created: ${fmtWhen(s.createdAt, zone)}", style = MaterialTheme.typography.bodySmall)
                s.readyAt?.let { Text("Became ready: ${fmtWhen(it, zone)}", style = MaterialTheme.typography.bodySmall) }
                s.openedAt?.let { Text("WhatsApp opened: ${fmtWhen(it, zone)}", style = MaterialTheme.typography.bodySmall) }
                s.confirmedAt?.let { Text("Manually confirmed sent: ${fmtWhen(it, zone)}", style = MaterialTheme.typography.bodySmall) }
                s.skippedAt?.let { Text("Skipped: ${fmtWhen(it, zone)}", style = MaterialTheme.typography.bodySmall) }
                s.reason?.let { Text("Note: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
