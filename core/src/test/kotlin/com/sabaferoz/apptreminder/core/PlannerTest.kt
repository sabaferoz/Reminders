package com.sabaferoz.apptreminder.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlannerTest {
    private val s = ReminderSettings(quietHours = noQuiet)
    private val longAgo = Instant.parse("2026-01-01T00:00:00Z")

    private fun due(p: ReminderPlan): Instant = assertIs<ReminderPlan.Due>(p).at

    @Test fun finlandAndPakistanConversion() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        assertEquals(java.time.LocalTime.of(18, 0), local(start, "Asia/Karachi").toLocalTime()) // EET UTC+2 vs PKT UTC+5 → +3h
        assertEquals(java.time.LocalTime.of(8, 0), local(start, "America/New_York").toLocalTime()) // 13:00Z = 08:00 EST
        assertEquals(java.time.LocalTime.of(13, 0), local(start, "Europe/London").toLocalTime())
    }

    @Test fun defaultsRelative24hAnd1h() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        val plans = ReminderPlanner.planBoth(start, patient(), s, longAgo)
        assertEquals(start.minusSeconds(24 * 3600), due(plans.getValue(ReminderKind.A)))
        assertEquals(start.minusSeconds(3600), due(plans.getValue(ReminderKind.B)))
    }

    @Test fun relativeAcrossDstUsesElapsedTimeNotWallClock() {
        // US fall-back Nov 1 2026 02:00 → 24h before 09:00 EST is 10:00 EDT the day before.
        val start = at("America/New_York", 2026, 11, 1, 9, 0)
        val d = due(ReminderPlanner.planBoth(start, patient(zone = "America/New_York"), s, longAgo).getValue(ReminderKind.A))
        assertEquals(java.time.LocalDateTime.of(2026, 10, 31, 10, 0), local(d, "America/New_York"))
    }

    @Test fun fixedPreviousDayInPakistanTime() {
        // Patient in Helsinki, appt Tue 3 Nov 15:00 EET = 18:00 PKT. A = previous day 18:00 PKT.
        val rule = ReminderRule(mode = ScheduleMode.FIXED_TIME, clockMinutes = 18 * 60, fixedZone = FixedZoneChoice.SCHEDULE_ZONE)
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        val d = due(ReminderPlanner.plan(ReminderKind.A, start, java.time.ZoneId.of("Europe/Helsinki"), rule, s, longAgo))
        assertEquals(java.time.LocalDateTime.of(2026, 11, 2, 18, 0), local(d, "Asia/Karachi"))
    }

    @Test fun fixedTimeUsesAppointmentDateInSelectedZone_midnightBoundary() {
        // Appt 01:00 Mon in Helsinki is 04:00 Mon in PKT – same date. But 23:00 Sunday in LA is Mon 11:00 PKT.
        val start = at("America/Los_Angeles", 2026, 11, 8, 23, 0) // Sunday evening LA = Monday 12:00 PKT
        val rule = ReminderRule(mode = ScheduleMode.FIXED_TIME, clockMinutes = 9 * 60, fixedZone = FixedZoneChoice.SCHEDULE_ZONE)
        val d = due(ReminderPlanner.plan(ReminderKind.B, start, java.time.ZoneId.of("America/Los_Angeles"), rule, s, longAgo))
        assertEquals(java.time.LocalDateTime.of(2026, 11, 9, 9, 0), local(d, "Asia/Karachi")) // Monday 9am PKT, not Sunday
        val patientLocal = rule.copy(fixedZone = FixedZoneChoice.PATIENT_LOCAL)
        val d2 = due(ReminderPlanner.plan(ReminderKind.B, start, java.time.ZoneId.of("America/Los_Angeles"), patientLocal, s, longAgo))
        assertEquals(java.time.LocalDateTime.of(2026, 11, 8, 9, 0), local(d2, "America/Los_Angeles"))
    }

    @Test fun fixedTimeAfterAppointmentIsSkipped() {
        val start = at("Asia/Karachi", 2026, 11, 3, 8, 0)
        val rule = ReminderRule(mode = ScheduleMode.FIXED_TIME, clockMinutes = 9 * 60)
        assertIs<ReminderPlan.Skip>(ReminderPlanner.plan(ReminderKind.B, start, java.time.ZoneId.of("Asia/Karachi"), rule, s, longAgo))
    }

    @Test fun fixedTimeOnDstGapDayStillResolves() {
        // Helsinki spring forward 2027-03-28 03:00→04:00; fixed 03:30 local on that day shifts forward, no crash.
        val start = at("Europe/Helsinki", 2027, 3, 28, 12, 0)
        val rule = ReminderRule(mode = ScheduleMode.FIXED_TIME, clockMinutes = 3 * 60 + 30, fixedZone = FixedZoneChoice.PATIENT_LOCAL)
        val d = due(ReminderPlanner.plan(ReminderKind.B, start, java.time.ZoneId.of("Europe/Helsinki"), rule, s, longAgo))
        assertEquals(java.time.LocalDateTime.of(2027, 3, 28, 4, 30), local(d, "Europe/Helsinki"))
    }

    @Test fun patientOverridesAreIndependent() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        val pB = patient(
            a = ReminderRule(mode = ScheduleMode.FIXED_TIME, clockMinutes = 18 * 60),
            b = ReminderRule(offsetMinutes = 120),
        )
        val plans = ReminderPlanner.planBoth(start, pB, s, longAgo)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 2, 18, 0), local(due(plans.getValue(ReminderKind.A)), "Asia/Karachi"))
        assertEquals(start.minusSeconds(7200), due(plans.getValue(ReminderKind.B)))

        val pC = patient(a = ReminderRule(enabled = false), b = ReminderRule(offsetMinutes = 30))
        val c = ReminderPlanner.planBoth(start, pC, s, longAgo)
        assertEquals(ReminderPlan.Disabled, c.getValue(ReminderKind.A))
        assertEquals(start.minusSeconds(1800), due(c.getValue(ReminderKind.B)))
    }

    @Test fun globalToggles() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        val off = ReminderPlanner.planBoth(start, patient(), s.copy(remindersEnabled = false), longAgo)
        assertTrue(off.values.all { it == ReminderPlan.Disabled })
        val onlyB = ReminderPlanner.planBoth(start, patient(), s.copy(ruleA = s.ruleA.copy(enabled = false)), longAgo)
        assertEquals(ReminderPlan.Disabled, onlyB.getValue(ReminderKind.A))
        assertIs<ReminderPlan.Due>(onlyB.getValue(ReminderKind.B))
    }

    @Test fun quietHoursPostponeWhenStillBeforeAppointment() {
        val q = ReminderSettings() // 22:00–08:00 default
        // Appt 14:00 Helsinki; A 24h before = 14:00 previous day (fine). Use custom 17h → 21:00; use 16h → 22:00 quiet → postponed 08:00 next day.
        val start = at("Europe/Helsinki", 2026, 11, 3, 14, 0)
        val rule = ReminderRule(offsetMinutes = 16 * 60)
        val d = ReminderPlanner.plan(ReminderKind.A, start, java.time.ZoneId.of("Europe/Helsinki"), rule, q, longAgo)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 3, 8, 0), local(assertIs<ReminderPlan.Due>(d).at, "Europe/Helsinki"))
    }

    @Test fun quietHoursSkipWhenNextAllowedIsAfterAppointment() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 8, 30) // B 1h before = 07:30 quiet → 08:00 is before 08:30 → postponed
        val postponed = ReminderPlanner.plan(ReminderKind.B, start, java.time.ZoneId.of("Europe/Helsinki"), ReminderRule(offsetMinutes = 60), ReminderSettings(), longAgo)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 3, 8, 0), local(assertIs<ReminderPlan.Due>(postponed).at, "Europe/Helsinki"))
        val early = at("Europe/Helsinki", 2026, 11, 3, 7, 30) // 06:30 quiet → 08:00 is after appointment → skip with reason
        val skip = ReminderPlanner.plan(ReminderKind.B, early, java.time.ZoneId.of("Europe/Helsinki"), ReminderRule(offsetMinutes = 60), ReminderSettings(), longAgo)
        assertTrue(assertIs<ReminderPlan.Skip>(skip).reason.contains("quiet"))
    }

    @Test fun quietHoursUsePatientLocalNotPakistanTime() {
        // Appt 10:00 New York; 1h before = 09:00 NY (awake) although it is 18:00… PKT irrelevant; and 03:00 NY is quiet while 12:00 PKT is not.
        val start = at("America/New_York", 2026, 11, 3, 4, 0)
        val p = ReminderPlanner.plan(ReminderKind.B, start, java.time.ZoneId.of("America/New_York"), ReminderRule(offsetMinutes = 60), ReminderSettings(), longAgo)
        assertIs<ReminderPlan.Skip>(p)
    }

    @Test fun neverPlansAfterAppointmentStarted() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        val p = ReminderPlanner.planBoth(start, patient(), s, start.plusSeconds(1))
        assertTrue(p.values.all { it is ReminderPlan.Skip })
    }

    @Test fun reminderAIsSupersededWhenBothAlreadyDue() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        val now = start.minusSeconds(1800) // 30 min before: A (24h) and B (1h) both in the past
        val plans = ReminderPlanner.planBoth(start, patient(), s, now)
        assertIs<ReminderPlan.Skip>(plans.getValue(ReminderKind.A))
        assertIs<ReminderPlan.Due>(plans.getValue(ReminderKind.B))
    }

    @Test fun shortTestIntervalFiveMinutes() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        val rule = ReminderRule(offsetMinutes = 5)
        assertEquals(start.minusSeconds(300), due(ReminderPlanner.plan(ReminderKind.B, start, java.time.ZoneId.of("Europe/Helsinki"), rule, s, longAgo)))
    }
}
