package com.sabaferoz.apptreminder.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface ReminderPlan {
    data object Disabled : ReminderPlan
    data class Due(val at: Instant, val note: String? = null) : ReminderPlan
    data class Skip(val reason: String) : ReminderPlan
}

object ReminderPlanner {
    fun effectiveRule(kind: ReminderKind, settings: ReminderSettings, patient: PatientProfile): ReminderRule =
        patient.override(kind) ?: settings.rule(kind)

    fun plan(
        kind: ReminderKind,
        start: Instant,
        patientZone: ZoneId,
        rule: ReminderRule,
        settings: ReminderSettings,
        now: Instant,
    ): ReminderPlan {
        if (!settings.remindersEnabled || !rule.enabled) return ReminderPlan.Disabled
        if (!now.isBefore(start)) return ReminderPlan.Skip("Appointment already started")

        val raw = when (rule.mode) {
            ScheduleMode.RELATIVE -> start.minus(Duration.ofMinutes(rule.offsetMinutes))
            ScheduleMode.FIXED_TIME -> {
                val zone = if (rule.fixedZone == FixedZoneChoice.PATIENT_LOCAL) patientZone else ZoneId.of(settings.scheduleZoneId)
                val apptDate = start.atZone(zone).toLocalDate()
                val date = if (kind == ReminderKind.A) apptDate.minusDays(1) else apptDate
                ZonedDateTime.of(date, rule.clock, zone).toInstant()
            }
        }
        if (!raw.isBefore(start)) return ReminderPlan.Skip("Scheduled time is not before the appointment")

        val q = settings.quietHours
        val local = raw.atZone(patientZone).toLocalDateTime()
        if (q.enabled && q.contains(local.toLocalTime())) {
            val postponed = q.nextAllowed(local).atZone(patientZone).toInstant()
            return if (postponed.isBefore(start)) {
                ReminderPlan.Due(postponed, "Postponed from quiet hours (${q.label()} patient time)")
            } else {
                ReminderPlan.Skip("Falls in patient quiet hours (${q.label()}); next allowed time is after the appointment")
            }
        }
        return ReminderPlan.Due(raw)
    }

    fun planBoth(
        start: Instant,
        patient: PatientProfile,
        settings: ReminderSettings,
        now: Instant,
    ): Map<ReminderKind, ReminderPlan> {
        val zone = ZoneId.of(patient.timeZoneId)
        val a = plan(ReminderKind.A, start, zone, effectiveRule(ReminderKind.A, settings, patient), settings, now)
        val b = plan(ReminderKind.B, start, zone, effectiveRule(ReminderKind.B, settings, patient), settings, now)
        val aFinal =
            if (a is ReminderPlan.Due && b is ReminderPlan.Due && !a.at.isAfter(now) && !b.at.isAfter(now)) {
                ReminderPlan.Skip("Superseded: Reminder B is already due")
            } else a
        return mapOf(ReminderKind.A to aFinal, ReminderKind.B to b)
    }
}
