package com.sabaferoz.apptreminder.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReconcilerTest {
    private val settings = AppSettings(reminder = ReminderSettings(quietHours = noQuiet))
    private val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
    private val p = patient()

    private fun run(appts: List<AppointmentInfo>, existing: List<ReminderState>, now: Instant, patients: List<PatientProfile> = listOf(p), s: AppSettings = settings) =
        ReminderReconciler.reconcile(appts, patients, existing, s, now)

    private fun apply(existing: List<ReminderState>, r: ReconcileResult) =
        (existing.associateBy { it.key } - r.deletes.toSet() + r.upserts.associateBy { it.key }).values.toList()

    @Test fun createsScheduledThenReadyWithoutDuplicates() {
        val t0 = start.minusSeconds(48 * 3600)
        val s1 = apply(emptyList(), run(listOf(appt(start = start)), emptyList(), t0))
        assertEquals(2, s1.size)
        assertTrue(s1.all { it.status == ReminderStatus.SCHEDULED })

        // Re-running with same state is a no-op (duplicate prevention)
        assertTrue(run(listOf(appt(start = start)), s1, t0).upserts.isEmpty())

        val t1 = start.minusSeconds(24 * 3600 + 60)
        val s2 = apply(s1, run(listOf(appt(start = start)), s1, t1.plusSeconds(120)))
        assertEquals(ReminderStatus.READY, s2.single { it.key.kind == ReminderKind.A }.status)
        assertEquals(ReminderStatus.SCHEDULED, s2.single { it.key.kind == ReminderKind.B }.status)
        assertEquals(2, s2.size)
    }

    @Test fun noConsentMeansNoReminders() {
        val r = run(listOf(appt(start = start)), emptyList(), start.minusSeconds(100000), listOf(patient(consent = false)))
        assertTrue(r.upserts.isEmpty())
        assertTrue(r.appointmentReviews.getValue("evt1").contains("consent"))
    }

    @Test fun missingMeetLinkOrPhoneNeedsReviewWhenDue() {
        val now = start.minusSeconds(3000)
        val noLink = run(listOf(appt(start = start, meet = null)), emptyList(), now)
        assertEquals(ReminderStatus.NEEDS_REVIEW, noLink.upserts.single { it.key.kind == ReminderKind.B }.status)
        val badPhone = run(listOf(appt(start = start)), emptyList(), now, listOf(patient(phone = "0401234567")))
        assertEquals(ReminderStatus.NEEDS_REVIEW, badPhone.upserts.single { it.key.kind == ReminderKind.B }.status)
        val noPhone = run(listOf(appt(start = start)), emptyList(), now, listOf(patient(phone = "")))
        assertEquals(ReminderStatus.NEEDS_REVIEW, noPhone.upserts.single { it.key.kind == ReminderKind.B }.status)
    }

    @Test fun cancellationSkipsOpenRemindersButKeepsConfirmed() {
        val now = start.minusSeconds(90000)
        val s1 = apply(emptyList(), run(listOf(appt(start = start)), emptyList(), now))
        val confirmed = s1[0].copy(status = ReminderStatus.SENT_CONFIRMED, locked = true)
        val existing = listOf(confirmed, s1[1])
        val r = run(listOf(appt(start = start, cancelled = true)), existing, now.plusSeconds(60))
        val out = apply(existing, r)
        assertEquals(ReminderStatus.SENT_CONFIRMED, out.first { it.key == confirmed.key }.status)
        val other = out.first { it.key == s1[1].key }
        assertEquals(ReminderStatus.SKIPPED, other.status)
        assertEquals("Appointment cancelled", other.reason)
    }

    @Test fun reschedulingRecalculatesAndResetsActionedReminders() {
        val now = start.minusSeconds(90000)
        val s1 = apply(emptyList(), run(listOf(appt(start = start)), emptyList(), now))
        val actioned = s1[0].copy(status = ReminderStatus.SENT_CONFIRMED, locked = true, draft = "old draft")
        val existing = listOf(actioned, s1[1])
        val newStart = start.plusSeconds(86400 * 2)
        val out = apply(existing, run(listOf(appt(start = newStart)), existing, now))
        val a = out.single { it.key.kind == ReminderKind.A }
        assertEquals(ReminderStatus.SCHEDULED, a.status)
        assertEquals(newStart, a.startSnapshot)
        assertEquals(newStart.minusSeconds(86400), a.dueAt)
        assertEquals(null, a.draft)
    }

    @Test fun actionedReminderIsLeftAloneWhenNothingChanged() {
        val now = start.minusSeconds(3000)
        val opened = ReminderState(ReminderKey("evt1", 1, ReminderKind.B), ReminderStatus.WHATSAPP_OPENED, start.minusSeconds(3600), start, locked = true, createdAt = now)
        assertTrue(run(listOf(appt(start = start)), listOf(opened), now).upserts.none { it.key == opened.key })
    }

    @Test fun overrideChangeRecalculatesOutstandingReminders() {
        val now = start.minusSeconds(90000)
        val s1 = apply(emptyList(), run(listOf(appt(start = start)), emptyList(), now))
        val changed = patient(b = ReminderRule(offsetMinutes = 120))
        val out = apply(s1, run(listOf(appt(start = start)), s1, now, listOf(changed)))
        assertEquals(start.minusSeconds(7200), out.single { it.key.kind == ReminderKind.B }.dueAt)
        val disabled = patient(b = ReminderRule(enabled = false))
        val r = run(listOf(appt(start = start)), s1, now, listOf(disabled))
        assertEquals(1, r.deletes.size)
    }

    @Test fun readyReminderExpiresWhenAppointmentStarts() {
        val now = start.minusSeconds(3000)
        val s1 = apply(emptyList(), run(listOf(appt(start = start)), emptyList(), now))
        val ready = s1.single { it.key.kind == ReminderKind.B }
        assertEquals(ReminderStatus.READY, ready.status)
        val later = apply(s1, run(listOf(appt(start = start)), s1, start.plusSeconds(10)))
        val b = later.single { it.key.kind == ReminderKind.B }
        assertEquals(ReminderStatus.SKIPPED, b.status)
        assertNotNull(b.reason)
    }

    @Test fun pastAppointmentsWithoutRemindersCreateNothing() {
        assertTrue(run(listOf(appt(start = start)), emptyList(), start.plusSeconds(100000)).upserts.isEmpty())
    }

    @Test fun twoPatientsOnOneEventGetOwnTimesAndReminders() {
        val pk = patient(id = 2, email = "b@x.com", zone = "Asia/Karachi", phone = "+923001234567")
        val a = appt(start = start, attendees = listOf(EventAttendee("p@example.com"), EventAttendee("b@x.com")))
        val out = run(listOf(a), emptyList(), start.minusSeconds(3000), listOf(p, pk))
        assertEquals(4, out.upserts.size)
        assertEquals(setOf(1L, 2L), out.upserts.map { it.key.patientId }.toSet())
    }

    @Test fun finalGateBlocksCancelledStartedAndDraftOverrides() {
        val a = appt(start = start)
        val now = start.minusSeconds(600)
        val ok = OutgoingMessageBuilder.build(ReminderKind.B, a, p, settings.reminder, null, now) as BuildResult.Ok
        assertTrue(ok.message.text.contains("Time: 3:00 PM (Europe/Helsinki, UTC+02:00)"))
        assertEquals("+358401234567", ok.message.phoneE164)
        val draft = OutgoingMessageBuilder.build(ReminderKind.B, a, p, settings.reminder, "my draft", now) as BuildResult.Ok
        assertEquals("my draft", draft.message.text)
        assertTrue(OutgoingMessageBuilder.build(ReminderKind.B, a.copy(cancelled = true), p, settings.reminder, null, now) is BuildResult.Blocked)
        assertTrue(OutgoingMessageBuilder.build(ReminderKind.B, a, p, settings.reminder, null, start.plusSeconds(1)) is BuildResult.Blocked)
        assertTrue(OutgoingMessageBuilder.build(ReminderKind.B, a.copy(meetLink = null), p, settings.reminder, null, now) is BuildResult.Blocked)
        assertTrue(OutgoingMessageBuilder.build(ReminderKind.B, a, p.copy(consent = false), settings.reminder, null, now) is BuildResult.Blocked)
        assertTrue(OutgoingMessageBuilder.build(ReminderKind.B, a, p.copy(phone = "123"), settings.reminder, null, now) is BuildResult.Blocked)
    }
}
