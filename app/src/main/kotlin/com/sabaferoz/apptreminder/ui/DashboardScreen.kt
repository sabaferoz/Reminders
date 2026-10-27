package com.sabaferoz.apptreminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(fmtDay(now, zone) + " · " + zone.substringAfterLast('/').replace('_', ' ') + " time", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ vm.refresh() }, enabled = !busy) { Icon(Icons.Filled.Refresh, "Refresh calendar") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (settings.lastSyncStatus.isNotBlank()) {
            Text(settings.lastSyncStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (!settings.googleConnected) {
            Banner("Connect your calendar", "Open Settings → Connect Google Calendar to load appointments.", warn = false)
        }
        if (!Notifier.canNotify(ctx)) {
            Banner("Notifications are off", "You won't be alerted when reminders are ready. Open Settings → Notifications.", warn = true)
        }

        // Big, tappable summary
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard("Ready to send", ready, highlight = ready > 0, modifier = Modifier.weight(1f), onClick = onOpenReminders)
            SummaryCard("Need review", review + attention.size, highlight = review + attention.size > 0, warn = true, modifier = Modifier.weight(1f), onClick = onOpenReminders)
        }

        SectionHeader("Today (${todays.size})")
        if (todays.isEmpty()) EmptyNote("No appointments today.") else todays.sortedBy { it.appt.start }.forEach { ApptCard(it, zone) }

        SectionHeader("Next 8 days (${upcoming.size})")
        if (upcoming.isEmpty()) EmptyNote("Nothing coming up.")
        else upcoming.sortedBy { it.appt.start }.groupBy { fmtDay(it.appt.start, zone) }.forEach { (day, list) ->
            Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            list.forEach { ApptCard(it, zone) }
        }

        if (attention.isNotEmpty()) {
            SectionHeader("Needs attention (${attention.size})")
            attention.sortedBy { it.appt.start }.forEach { ApptCard(it, zone) }
        }
    }
}

@Composable
private fun EmptyNote(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun Banner(title: String, body: String, warn: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SummaryCard(label: String, count: Int, highlight: Boolean, modifier: Modifier, onClick: () -> Unit, warn: Boolean = false) {
    val container = when {
        !highlight -> MaterialTheme.colorScheme.surfaceVariant
        warn -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = modifier.clickable { onClick() }) {
        Column(Modifier.padding(14.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ApptCard(r: ApptRow, managerZone: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (r.people.isEmpty()) "Unmatched attendee" else r.people.joinToString(" & ") { it.alias },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (r.people.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (r.people.isEmpty()) TimePair(r.appt.start, null, managerZone)
            r.people.forEach { p -> TimePair(r.appt.start, p.timeZoneId, managerZone) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (r.appt.cancelled) Pill("Cancelled", warn = true)
                else if (r.appt.meetLink == null) Pill("No Meet link", warn = true) else Pill("Meet link ✓")
                r.appt.changeNote?.let { Pill(it.substringBefore(" (")) }
            }
            r.reviewNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}
