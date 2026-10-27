package com.sabaferoz.apptreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sabaferoz.apptreminder.core.ReminderStatus
import com.sabaferoz.apptreminder.work.Notifier
import java.time.Duration
import java.time.ZoneId

@Composable
fun DashboardScreen(vm: MainViewModel, onOpenReminders: () -> Unit) {
    val rows by vm.apptRows.collectAsState()
    val reminders by vm.reminderRows.collectAsState()
    val settings by vm.settings.collectAsState()
    val now by vm.now.collectAsState()
    val busy by vm.busy.collectAsState()
    val ctx = LocalContext.current
    val zone = settings.reminder.scheduleZoneId
    val today = now.atZone(ZoneId.of(zone)).toLocalDate()

    val live = rows.filter { !it.appt.cancelled && it.appt.end.isAfter(now) }
    val todays = live.filter { it.appt.start.atZone(ZoneId.of(zone)).toLocalDate() == today }
    val upcoming = live.filter { it !in todays && it.appt.start.isBefore(now.plus(Duration.ofDays(8))) }
    val attention = rows.filter { (it.reviewNote != null || it.appt.changeNote != null) && it.appt.end.isAfter(now) }
    val ready = reminders.count { it.state.status == ReminderStatus.READY }
    val review = reminders.count { it.state.status == ReminderStatus.NEEDS_REVIEW }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
            Button({ vm.refresh() }, enabled = !busy) { Icon(Icons.Filled.Refresh, null); Text(" Refresh") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            settings.lastSyncStatus,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!Notifier.canNotify(ctx)) {
            Section("Notifications are off") {
                Text("You will not be alerted when reminders are ready. Open Settings → Notifications.")
            }
        }
        if (!settings.googleConnected) {
            Section("Connect your calendar") {
                Text("Open Settings → Connect Google Calendar to load appointments.")
            }
        }
        Section("Reminders") {
            Text("$ready ready to send" + if (review > 0) " · $review need review" else "")
            OutlinedButton(onOpenReminders) { Text("Open reminder queue") }
        }
        Section("Today's appointments (${zone})") {
            if (todays.isEmpty()) Text("None.") else todays.forEach { ApptLine(it, zone) }
        }
        Section("Upcoming (next 8 days)") {
            if (upcoming.isEmpty()) Text("None.") else upcoming.forEach { ApptLine(it, zone) }
        }
        Section("Needs attention") {
            if (attention.isEmpty()) Text("Nothing to review.") else attention.forEach { ApptLine(it, zone, showNotes = true) }
        }
    }
}

@Composable
fun ApptLine(r: ApptRow, managerZone: String, showNotes: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (r.people.isEmpty()) {
            Text("Unmatched patient", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Text("Manager time: ${fmtWhen(r.appt.start, managerZone)}", style = MaterialTheme.typography.bodySmall)
        }
        r.people.forEach { p ->
            Text(p.alias, style = MaterialTheme.typography.titleSmall)
            Text("Patient time: ${fmtWithZone(r.appt.start, p.timeZoneId)}", style = MaterialTheme.typography.bodySmall)
            Text("Manager time: ${fmtWhen(r.appt.start, managerZone)}", style = MaterialTheme.typography.bodySmall)
        }
        Text(if (r.appt.meetLink != null) "Meet link: yes" else "Meet link: MISSING", style = MaterialTheme.typography.bodySmall)
        if (r.appt.cancelled) Text("Cancelled", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (showNotes) {
            r.appt.changeNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            r.reviewNote?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
