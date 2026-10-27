package com.sabaferoz.apptreminder.core

import java.time.Instant
import java.time.ZoneId

data class ReconcileResult(
    val upserts: List<ReminderState>,
    val deletes: List<ReminderKey>,
    /** Appointment id → why it needs manual review (absent = fine). */
    val appointmentReviews: Map<String, String>,
)

sealed interface BuildResult {
    data class Ok(val message: OutgoingMessage) : BuildResult
    data class Blocked(val reason: String) : BuildResult
}

object OutgoingMessageBuilder {
    fun template(kind: ReminderKind, patient: PatientProfile, settings: ReminderSettings) =
        patient.template(kind)?.takeIf { it.isNotBlank() } ?: settings.template(kind)

    fun render(kind: ReminderKind, appt: AppointmentInfo, patient: PatientProfile, settings: ReminderSettings): RenderedMessage =
        MessageRenderer.render(
            template(kind, patient, settings),
            MessageContext(patient.alias, appt.start, ZoneId.of(patient.timeZoneId), appt.meetLink),
        )

    /** Non-null = the reminder cannot be sent as it stands. */
    fun problem(kind: ReminderKind, appt: AppointmentInfo, patient: PatientProfile, settings: ReminderSettings): String? {
        if (!patient.consent) return "No recorded reminder consent for this patient."
        PhoneValidator.error(patient.phone)?.let { return it }
        val r = render(kind, appt, patient, settings)
        return r.errors.firstOrNull()
    }

    /** Final gate used at "Open WhatsApp" time with the freshest data. */
    fun build(
        kind: ReminderKind,
        appt: AppointmentInfo?,
        patient: PatientProfile?,
        settings: ReminderSettings,
        draft: String?,
        now: Instant,
    ): BuildResult {
        if (appt == null) return BuildResult.Blocked("Appointment no longer exists.")
        if (patient == null) return BuildResult.Blocked("Patient no longer exists.")
        if (appt.cancelled) return BuildResult.Blocked("Appointment was cancelled.")
        if (!now.isBefore(appt.start)) return BuildResult.Blocked("Appointment has already started.")
        if (!patient.consent) return BuildResult.Blocked("No recorded reminder consent for this patient.")
        val phone = PhoneValidator.normalize(patient.phone)
            ?: return BuildResult.Blocked(PhoneValidator.error(patient.phone) ?: "Invalid phone number.")
        val rendered = render(kind, appt, patient, settings)
        val text = if (!draft.isNullOrBlank()) draft else {
            if (!rendered.ok) return BuildResult.Blocked(rendered.errors.first())
            rendered.text
        }
        return BuildResult.Ok(OutgoingMessage(patient.id, appt.id, kind, phone, text))
    }
}

object ReminderReconciler {
    fun reconcile(
        appointments: List<AppointmentInfo>,
        patients: List<PatientProfile>,
        existing: List<ReminderState>,
        settings: AppSettings,
        now: Instant,
    ): ReconcileResult {
        val byKey = existing.associateBy { it.key }
        val upserts = linkedMapOf<ReminderKey, ReminderState>()
        val deletes = mutableSetOf<ReminderKey>()
        val reviews = mutableMapOf<String, String>()
        val handled = mutableSetOf<ReminderKey>()
        val apptById = appointments.associateBy { it.id }

        for (appt in appointments) {
            if (appt.cancelled) continue
            val match = AttendeeMatcher.match(appt.attendees, settings.staffEmails, patients)
            val notes = mutableListOf<String>()
            match.reviewNote?.let { notes += it }
            if (appt.meetLink == null) notes += "No Google Meet link on this event."
            for (p in match.matched) {
                if (!p.consent) {
                    notes += "No recorded reminder consent for ${p.alias}."
                    continue
                }
                val plans = ReminderPlanner.planBoth(appt.start, p, settings.reminder, now)
                for (kind in ReminderKind.entries) {
                    val key = ReminderKey(appt.id, p.id, kind)
                    handled += key
                    val old = byKey[key]
                    val plan = plans.getValue(kind)
                    if (old != null && old.locked && old.startSnapshot == appt.start) continue // manager already acted
                    val fresh = if (old != null && old.startSnapshot != appt.start) null else old
                    val next: ReminderState? = when (plan) {
                        ReminderPlan.Disabled -> null
                        is ReminderPlan.Skip -> when {
                            fresh == null && plan.reason == "Appointment already started" -> null
                            else -> skipped(key, appt, fresh, plan.reason, now)
                        }
                        is ReminderPlan.Due -> due(key, appt, p, kind, fresh, plan, settings.reminder, now)
                    }
                    if (next == null) {
                        if (old != null) deletes += key
                    } else if (next != old) upserts[key] = next
                }
            }
            if (notes.isNotEmpty()) reviews[appt.id] = notes.joinToString(" ")
        }

        // Anything we did not regenerate: appointment cancelled/removed, patient or consent gone.
        for (old in existing) {
            if (old.key in handled) continue
            if (old.status == ReminderStatus.SENT_CONFIRMED || old.status == ReminderStatus.SKIPPED) continue
            val appt = apptById[old.key.appointmentId]
            val reason = if (appt == null || appt.cancelled) "Appointment cancelled" else "Patient no longer matches or has no consent"
            upserts[old.key] = old.copy(status = ReminderStatus.SKIPPED, reason = reason, locked = false, skippedAt = old.skippedAt ?: now)
        }
        return ReconcileResult(upserts.values.toList(), deletes.toList(), reviews)
    }

    private fun skipped(key: ReminderKey, appt: AppointmentInfo, old: ReminderState?, reason: String, now: Instant) =
        ReminderState(
            key, ReminderStatus.SKIPPED, old?.dueAt, appt.start, reason, locked = false,
            createdAt = old?.createdAt ?: now, skippedAt = old?.skippedAt ?: now,
        )

    private fun due(
        key: ReminderKey, appt: AppointmentInfo, p: PatientProfile, kind: ReminderKind,
        old: ReminderState?, plan: ReminderPlan.Due, rs: ReminderSettings, now: Instant,
    ): ReminderState {
        val problem = OutgoingMessageBuilder.problem(kind, appt, p, rs)
        val status = when {
            plan.at.isAfter(now) -> ReminderStatus.SCHEDULED
            problem != null -> ReminderStatus.NEEDS_REVIEW
            else -> ReminderStatus.READY
        }
        val keep = old != null && old.status == status
        return ReminderState(
            key = key, status = status, dueAt = plan.at, startSnapshot = appt.start,
            reason = problem ?: plan.note, locked = false, draft = old?.draft,
            createdAt = old?.createdAt ?: now,
            readyAt = if (status == ReminderStatus.READY || status == ReminderStatus.NEEDS_REVIEW) (if (keep) old?.readyAt else null) ?: now else null,
            notifiedAt = if (keep) old?.notifiedAt else null,
        )
    }
}
