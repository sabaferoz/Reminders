package com.sabaferoz.apptreminder.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sabaferoz.apptreminder.App
import com.sabaferoz.apptreminder.calendar.AuthOutcome
import com.sabaferoz.apptreminder.core.AppSettings
import com.sabaferoz.apptreminder.core.AppointmentInfo
import com.sabaferoz.apptreminder.core.AttendeeMatcher
import com.sabaferoz.apptreminder.core.BuildResult
import com.sabaferoz.apptreminder.core.CalendarListEntry
import com.sabaferoz.apptreminder.core.DeliveryResult
import com.sabaferoz.apptreminder.core.Emails
import com.sabaferoz.apptreminder.core.MessageRenderer
import com.sabaferoz.apptreminder.core.OutgoingMessageBuilder
import com.sabaferoz.apptreminder.core.PatientProfile
import com.sabaferoz.apptreminder.core.PhoneValidator
import com.sabaferoz.apptreminder.core.ReminderKey
import com.sabaferoz.apptreminder.core.ReminderKind
import com.sabaferoz.apptreminder.core.ReminderState
import com.sabaferoz.apptreminder.data.AppointmentEntity
import com.sabaferoz.apptreminder.data.PatientEntity
import com.sabaferoz.apptreminder.data.ReminderEntity
import com.sabaferoz.apptreminder.data.toCore
import com.sabaferoz.apptreminder.whatsapp.WhatsAppHandoff
import com.sabaferoz.apptreminder.work.Notifier
import com.sabaferoz.apptreminder.work.Scheduling
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderRow(
    val state: ReminderState,
    val appt: AppointmentInfo?,
    val patient: PatientProfile?,
    val preview: String,
)

data class ApptRow(val appt: AppointmentInfo, val reviewNote: String?, val people: List<PatientProfile>, val isDemo: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val g = (app as App).graph
    val zones get() = g.zones

    val settings: StateFlow<AppSettings> = g.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val patients: StateFlow<List<PatientEntity>> = g.repo.observePatients().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val appointments: StateFlow<List<AppointmentEntity>> = g.repo.observeAppointments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val reminders: StateFlow<List<ReminderEntity>> = g.repo.observeReminders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Ticks every 30 s so "ready"/"today" views stay current while the app is open. */
    val now: StateFlow<Instant> = flow { while (true) { emit(Instant.now()); delay(30_000) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Instant.now())

    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val calendars = MutableStateFlow<List<CalendarListEntry>>(emptyList())

    val apptRows: StateFlow<List<ApptRow>> = combine(appointments, patients, settings) { a, p, s ->
        val profiles = p.map { it.toCore() }
        a.map { e ->
            val info = e.toCore()
            ApptRow(info, e.reviewNote, AttendeeMatcher.match(info.attendees, s.staffEmails, profiles).matched, e.isDemo)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reminderRows: StateFlow<List<ReminderRow>> = combine(reminders, appointments, patients, settings) { r, a, p, s ->
        val appts = a.associate { it.id to it.toCore() }
        val pats = p.associate { it.id to it.toCore() }
        r.map { e ->
            val st = e.toCore()
            val ap = appts[st.key.appointmentId]
            val pt = pats[st.key.patientId]
            val preview = when {
                st.draft != null -> st.draft!!
                ap == null || pt == null -> ""
                else -> OutgoingMessageBuilder.render(st.key.kind, ap, pt, s.reminder).let { if (it.ok) it.text else "⚠ ${it.errors.first()}" }
            }
            ReminderRow(st, ap, pt, preview)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { g.repo.deleteDemoData() } // clear any leftovers from earlier demo builds
        refresh()
    }

    fun toast(msg: String?) { message.value = msg }

    fun refresh() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                Scheduling.tick(getApplication(), syncCalendar = true)
                message.value = g.settings.get().lastSyncStatus.ifBlank { null }
            } catch (e: Exception) {
                message.value = "Refresh failed."
            } finally { busy.value = false }
        }
    }

    private fun recompute() = viewModelScope.launch { Scheduling.tick(getApplication(), syncCalendar = false) }

    // ---- settings -------------------------------------------------------------------------------------------------
    fun updateSettings(f: (AppSettings) -> AppSettings) {
        viewModelScope.launch { g.settings.update(f); Scheduling.tick(getApplication(), syncCalendar = false) }
    }

    // ---- Google ---------------------------------------------------------------------------------------------------
    suspend fun beginAuth(): AuthOutcome = g.auth.authorize()

    fun onConsentResult(data: Intent?) {
        when (val o = g.auth.tokenFromConsent(data)) {
            is AuthOutcome.Token -> finishConnect(o.accessToken)
            is AuthOutcome.Failed -> message.value = o.reason
            is AuthOutcome.NeedsConsent -> Unit
        }
    }

    fun finishConnect(token: String) {
        viewModelScope.launch {
            g.settings.update { it.copy(googleConnected = true) }
            loadCalendars(token)
            refresh()
        }
    }

    private suspend fun loadCalendars(token: String) {
        try {
            val list = g.api.listCalendars(token)
            calendars.value = list
            val s = g.settings.get()
            if (s.calendarId == null) list.firstOrNull { it.primary }?.let { c ->
                g.settings.update { it.copy(calendarId = c.id, calendarName = c.summary ?: c.id) }
            }
        } catch (e: Exception) {
            message.value = "Could not list calendars; using your primary calendar."
        }
    }

    fun loadCalendarsSilently() {
        viewModelScope.launch { (g.auth.authorize() as? AuthOutcome.Token)?.let { loadCalendars(it.accessToken) } }
    }

    fun selectCalendar(c: CalendarListEntry) {
        viewModelScope.launch {
            g.repo.deleteRealAppointments()
            g.settings.update { it.copy(calendarId = c.id, calendarName = c.summary ?: c.id) }
            refresh()
        }
    }

    fun disconnectGoogle() {
        viewModelScope.launch {
            (g.auth.authorize() as? AuthOutcome.Token)?.let { g.auth.revoke(it.accessToken) }
            g.repo.deleteRealAppointments()
            g.settings.update { it.copy(googleConnected = false, calendarId = null, calendarName = null) }
            calendars.value = emptyList()
            recompute()
            message.value = "Disconnected. Calendar data removed from this phone."
        }
    }

    fun wipeEverything() {
        viewModelScope.launch {
            g.repo.wipeEverything()
            Notifier.clear(getApplication())
            Scheduling.armAlarm(getApplication(), null)
            message.value = "All local data deleted."
        }
    }

    // ---- patients -------------------------------------------------------------------------------------------------
    fun validate(p: PatientEntity): List<String> {
        val errs = mutableListOf<String>()
        if (p.alias.isBlank()) errs += "Name or alias is required."
        if (!Emails.isValid(p.email)) errs += "Enter the patient's Google Calendar attendee email."
        else if (patients.value.any { it.id != p.id && Emails.normalize(it.email) == Emails.normalize(p.email) }) {
            errs += "Another patient already uses this email."
        }
        if (p.phone.isNotBlank()) PhoneValidator.error(p.phone)?.let { errs += it }
        else if (p.consent) errs += "WhatsApp number is required when reminders are enabled."
        if (p.countryIso.isBlank()) errs += "Select a country."
        if (runCatching { ZoneId.of(p.timeZoneId) }.isFailure) errs += "Select the patient's region or time zone."
        listOfNotNull(p.templateA, p.templateB).forEach { t ->
            MessageRenderer.validateTemplate(t).filter { it.startsWith("Unknown") || it.startsWith("Template is empty") }.forEach { errs += it }
        }
        return errs
    }

    fun savePatient(p: PatientEntity, done: () -> Unit) {
        viewModelScope.launch {
            val normalized = p.copy(
                email = p.email.trim(), alias = p.alias.trim(),
                phone = PhoneValidator.normalize(p.phone) ?: p.phone.trim(),
            )
            g.repo.savePatient(normalized)
            recompute()
            done()
        }
    }

    fun deletePatient(id: Long) { viewModelScope.launch { g.repo.deletePatient(id); recompute(); message.value = "Patient and their reminders deleted." } }

    // ---- reminders ------------------------------------------------------------------------------------------------
    fun saveDraft(key: ReminderKey, text: String?) { viewModelScope.launch { g.repo.saveDraft(key, text) } }
    fun skip(key: ReminderKey) { viewModelScope.launch { g.repo.skip(key); recompute() } }

    fun openWhatsApp(ctx: Context, key: ReminderKey) {
        viewModelScope.launch {
            busy.value = true
            try {
                // 1) refresh from Calendar (best effort), 2) rebuild from the freshest data, 3) hand off.
                runCatching { g.sync.sync() }
                when (val r = g.repo.buildMessage(key)) {
                    is BuildResult.Blocked -> { message.value = "Not sent: ${r.reason}"; recompute() }
                    is BuildResult.Ok -> when (val d = WhatsAppHandoff(ctx, settings.value.whatsApp).deliver(r.message)) {
                        DeliveryResult.HandedOff -> g.repo.markOpened(key)
                        DeliveryResult.AppNotInstalled -> message.value = "${settings.value.whatsApp.label} is not installed. Choose the other app in Settings."
                        is DeliveryResult.Failed -> message.value = d.reason
                    }
                }
            } finally { busy.value = false }
        }
    }

    fun kindLabel(k: ReminderKind) = k.label
}
