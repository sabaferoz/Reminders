package com.sabaferoz.apptreminder.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.Serializable

enum class ReminderKind(val label: String) { A("Reminder A"), B("Reminder B") }

enum class ScheduleMode { RELATIVE, FIXED_TIME }

/** Which time zone a fixed clock time is interpreted in. */
enum class FixedZoneChoice { SCHEDULE_ZONE, PATIENT_LOCAL }

enum class WhatsAppApp(val packageName: String, val label: String) {
    REGULAR("com.whatsapp", "WhatsApp"),
    BUSINESS("com.whatsapp.w4b", "WhatsApp Business"),
}

enum class ReminderStatus(val label: String) {
    SCHEDULED("Scheduled"),
    READY("Ready"),
    WHATSAPP_OPENED("WhatsApp opened"),
    SENT_CONFIRMED("User-confirmed sent"),
    SKIPPED("Skipped"),
    NEEDS_REVIEW("Needs review"),
}

@Serializable
data class ReminderRule(
    val enabled: Boolean = true,
    val mode: ScheduleMode = ScheduleMode.RELATIVE,
    val offsetMinutes: Long = 60,
    val clockMinutes: Int = 9 * 60,
    val fixedZone: FixedZoneChoice = FixedZoneChoice.SCHEDULE_ZONE,
) {
    val clock: LocalTime get() = LocalTime.of((clockMinutes / 60).coerceIn(0, 23), (clockMinutes % 60).coerceIn(0, 59))
}

@Serializable
data class QuietHours(
    val enabled: Boolean = true,
    val startMinutes: Int = 22 * 60,
    val endMinutes: Int = 8 * 60,
) {
    fun contains(t: LocalTime): Boolean {
        if (!enabled || startMinutes == endMinutes) return false
        val m = t.hour * 60 + t.minute
        return if (startMinutes < endMinutes) m in startMinutes until endMinutes else m >= startMinutes || m < endMinutes
    }

    /** First local date-time at or after [local] that is outside quiet hours. */
    fun nextAllowed(local: LocalDateTime): LocalDateTime {
        if (!contains(local.toLocalTime())) return local
        val m = local.hour * 60 + local.minute
        val endDate = if (startMinutes < endMinutes || m < endMinutes) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return LocalDateTime.of(endDate, LocalTime.of(endMinutes / 60, endMinutes % 60))
    }

    fun label(): String = "%02d:%02d–%02d:%02d".format(startMinutes / 60, startMinutes % 60, endMinutes / 60, endMinutes % 60)
}

@Serializable
data class ReminderSettings(
    val remindersEnabled: Boolean = true,
    val ruleA: ReminderRule = ReminderRule(offsetMinutes = 24 * 60, clockMinutes = 18 * 60),
    val ruleB: ReminderRule = ReminderRule(offsetMinutes = 60, clockMinutes = 9 * 60),
    val scheduleZoneId: String = "Asia/Karachi",
    val quietHours: QuietHours = QuietHours(),
    val templateA: String = MessageTemplates.DEFAULT_A,
    val templateB: String = MessageTemplates.DEFAULT_B,
) {
    fun rule(kind: ReminderKind) = if (kind == ReminderKind.A) ruleA else ruleB
    fun template(kind: ReminderKind) = if (kind == ReminderKind.A) templateA else templateB
}

@Serializable
data class AppSettings(
    val reminder: ReminderSettings = ReminderSettings(),
    val whatsApp: WhatsAppApp = WhatsAppApp.REGULAR,
    val demoMode: Boolean = true,
    val googleConnected: Boolean = false,
    val calendarId: String? = null,
    val calendarName: String? = null,
    /** Emails of the psychiatrist/manager so they are never treated as patients. */
    val staffEmails: List<String> = emptyList(),
    val whatsAppNoticeAcknowledged: Boolean = false,
    val lastSyncAtMs: Long = 0,
    val lastSyncStatus: String = "",
)

/** A saved patient. Override fields: null = use global defaults. */
data class PatientProfile(
    val id: Long,
    val alias: String,
    val email: String,
    val phone: String,
    val timeZoneId: String,
    val consent: Boolean,
    val overrideA: ReminderRule? = null,
    val overrideB: ReminderRule? = null,
    val templateA: String? = null,
    val templateB: String? = null,
) {
    fun override(kind: ReminderKind) = if (kind == ReminderKind.A) overrideA else overrideB
    fun template(kind: ReminderKind) = if (kind == ReminderKind.A) templateA else templateB
}

@Serializable
data class EventAttendee(
    val email: String,
    val self: Boolean = false,
    val organizer: Boolean = false,
    val resource: Boolean = false,
)

/** Locally retained appointment (deliberately has no title/description). */
data class AppointmentInfo(
    val id: String,
    val start: Instant,
    val end: Instant,
    val cancelled: Boolean,
    val meetLink: String?,
    val attendees: List<EventAttendee>,
    val changeNote: String? = null,
)

data class ReminderKey(val appointmentId: String, val patientId: Long, val kind: ReminderKind)

data class ReminderState(
    val key: ReminderKey,
    val status: ReminderStatus,
    val dueAt: Instant?,
    val startSnapshot: Instant,
    val reason: String? = null,
    /** True once the manager acted (opened/confirmed/skipped) – automation stops touching it. */
    val locked: Boolean = false,
    val draft: String? = null,
    val createdAt: Instant,
    val readyAt: Instant? = null,
    val notifiedAt: Instant? = null,
    val openedAt: Instant? = null,
    val confirmedAt: Instant? = null,
    val skippedAt: Instant? = null,
)

/** Version 2 seam: manual WhatsApp hand-off today, WhatsApp Business Platform API later. */
data class OutgoingMessage(
    val patientId: Long,
    val appointmentId: String,
    val kind: ReminderKind,
    val phoneE164: String,
    val text: String,
)

sealed interface DeliveryResult {
    /** Chat opened with prefilled text; the manager still has to press Send. */
    data object HandedOff : DeliveryResult
    data object AppNotInstalled : DeliveryResult
    data class Failed(val reason: String) : DeliveryResult
}

interface MessageDelivery {
    fun deliver(message: OutgoingMessage): DeliveryResult
}
