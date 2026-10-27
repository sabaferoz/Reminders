package com.sabaferoz.apptreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
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

    Column(Modifier.fillMaxWidth()) {
        Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Text(fmtDay(now, zone) + " · " + zone.substringAfterLast('/').replace('_', ' ') + " time", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
                IconButton({ vm.refresh() }, enabled = !busy) { Icon(Icons.Filled.Refresh, "Refresh calendar", tint = MaterialTheme.colorScheme.onPrimary) }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            SummaryCard("Ready to send", ready, tone = if (ready > 0) Tone.GOOD else Tone.NEUTRAL, modifier = Modifier.weight(1f), onClick = onOpenReminders)
            SummaryCard("Need review", review + attention.size, tone = if (review + attention.size > 0) Tone.WARN else Tone.NEUTRAL, modifier = Modifier.weight(1f), onClick = onOpenReminders)
        }

        SectionHeader("Today (${todays.size})")
        if (todays.isEmpty()) EmptyNote("No appointments today.") else todays.sortedBy { it.appt.start }.forEach { ApptCard(it, zone) }

        SectionHeader("Next 8 days (${upcoming.size})")
        if (upcoming.isEmpty()) EmptyNote("Nothing coming up.")
        else upcoming.sortedBy { it.appt.start }.groupBy { fmtDay(it.appt.start, zone) }.forEach { (day, list) ->
            Text(day, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp))
            list.forEach { ApptCard(it, zone) }
        }

        if (attention.isNotEmpty()) {
            SectionHeader("Needs attention (${attention.size})")
            attention.sortedBy { it.appt.start }.forEach { ApptCard(it, zone) }
        }
    }
    }
}

@Composable
private fun EmptyNote(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun Banner(title: String, body: String, warn: Boolean) {
    val c = toneColors(if (warn) Tone.BAD else Tone.INFO)
    Card(colors = CardDefaults.cardColors(containerColor = c.bg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.fg)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.fg)
        }
    }
}

@Composable
private fun SummaryCard(label: String, count: Int, tone: Tone, modifier: Modifier, onClick: () -> Unit) {
    val c = toneColors(tone)
    Card(colors = CardDefaults.cardColors(containerColor = c.bg), modifier = modifier.clickable { onClick() }) {
        Column(Modifier.padding(14.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = c.fg)
            Text(label, style = MaterialTheme.typography.titleSmall, color = c.fg)
        }
    }
}

@Composable
fun ApptCard(r: ApptRow, managerZone: String) {
    val problem = r.appt.cancelled || r.people.isEmpty() || r.appt.meetLink == null
    val accent = when {
        problem -> toneColors(Tone.BAD).fg
        r.reviewNote != null || r.appt.changeNote != null -> toneColors(Tone.WARN).fg
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (r.people.isEmpty()) "Unmatched attendee" else r.people.joinToString(" & ") { it.alias },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (r.people.isEmpty()) toneColors(Tone.BAD).fg else MaterialTheme.colorScheme.onSurface,
                )
                if (r.people.isEmpty()) TimePair(r.appt.start, null, managerZone)
                r.people.forEach { p -> TimePair(r.appt.start, p.timeZoneId, managerZone) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    if (r.appt.cancelled) Pill("Cancelled", Tone.BAD)
                    else if (r.appt.meetLink == null) Pill("No Meet link", Tone.BAD) else Pill("Meet link ✓", Tone.GOOD)
                    r.appt.changeNote?.let { Pill(it.substringBefore(" ("), Tone.WARN) }
                }
                r.reviewNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = toneColors(Tone.WARN).fg) }
            }
        }
    }
}
