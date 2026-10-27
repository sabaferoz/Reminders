package com.sabaferoz.apptreminder.demo

import com.sabaferoz.apptreminder.core.EventAttendee
import com.sabaferoz.apptreminder.core.FixedZoneChoice
import com.sabaferoz.apptreminder.core.ScheduleMode
import com.sabaferoz.apptreminder.core.ReminderRule
import com.sabaferoz.apptreminder.data.AppointmentEntity
import com.sabaferoz.apptreminder.data.PatientEntity
import com.sabaferoz.apptreminder.data.Repository
import com.sabaferoz.apptreminder.data.ruleToJson
import com.sabaferoz.apptreminder.data.AppJson
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.builtins.ListSerializer

/** Entirely fictional people. Phone numbers are blank on purpose so nothing can be sent to a stranger. */
object DemoData {
    private const val STAFF = "clinic.demo@example.com"
    private const val MEET = "https://meet.google.com/"

    private fun p(alias: String, email: String, iso: String, region: String, zone: String, a: ReminderRule? = null, b: ReminderRule? = null, tA: String? = null) =
        PatientEntity(
            alias = alias, email = email, phone = "", countryIso = iso, region = region, timeZoneId = zone, consent = true,
            consentAtMs = System.currentTimeMillis(), overrideAJson = ruleToJson(a), overrideBJson = ruleToJson(b),
            templateA = tA, templateB = null, isDemo = true,
        )

    private val patients = listOf(
        p("Demo Anna (Finland)", "anna.demo@example.com", "FI", "", "Europe/Helsinki"),
        p("Demo Bilal (Pakistan)", "bilal.demo@example.com", "PK", "", "Asia/Karachi"),
        p("Demo Charlotte (UK)", "charlotte.demo@example.com", "GB", "", "Europe/London"),
        p("Demo David (New York)", "david.demo@example.com", "US", "New York – New York City (Eastern)", "America/New_York"),
        p("Demo Emily (Los Angeles)", "emily.demo@example.com", "US", "California – Los Angeles, San Francisco (Pacific)", "America/Los_Angeles"),
        p(
            "Demo Farid (override A+B)", "farid.demo@example.com", "FI", "", "Europe/Helsinki",
            a = ReminderRule(mode = ScheduleMode.FIXED_TIME, clockMinutes = 18 * 60, fixedZone = FixedZoneChoice.SCHEDULE_ZONE),
            b = ReminderRule(offsetMinutes = 120),
        ),
        p(
            "Demo Grace (A off, B 30m, custom text)", "grace.demo@example.com", "PK", "", "Asia/Karachi",
            a = ReminderRule(enabled = false), b = ReminderRule(offsetMinutes = 30),
            tA = "Hi {patient_name}, your session is on {appointment_date} at {appointment_time} ({patient_timezone}). {meet_link}",
        ),
    )

    private fun att(vararg emails: String) = listOf(EventAttendee(STAFF, self = true, organizer = true)) + emails.map { EventAttendee(it) }

    private fun round(i: Instant): Instant {
        val q = 5 * 60L
        return Instant.ofEpochSecond((i.epochSecond / q + 1) * q)
    }

    suspend fun seed(repo: Repository, now: Instant = Instant.now()) {
        if (repo.allPatients().any { it.isDemo }) return
        val ids = patients.associate { it.email to repo.savePatient(it) }
        fun after(d: Duration) = round(now.plus(d))
        fun local(zone: String, y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.of(zone)).toInstant()

        data class A(val id: String, val start: Instant, val emails: List<String>, val meet: String? = MEET + "demo-aaaa-001", val cancelled: Boolean = false)
        val list = listOf(
            A("demo-01", after(Duration.ofMinutes(95)), listOf("bilal.demo@example.com")),
            A("demo-02", after(Duration.ofHours(26)), listOf("anna.demo@example.com"), MEET + "demo-aaaa-002"),
            A("demo-03", after(Duration.ofHours(51)), listOf("charlotte.demo@example.com"), meet = null), // missing Meet link
            A("demo-04", after(Duration.ofHours(30)), listOf("farid.demo@example.com"), MEET + "demo-aaaa-004"),
            A("demo-05", after(Duration.ofHours(5)), listOf("grace.demo@example.com"), MEET + "demo-aaaa-005"),
            A("demo-06", after(Duration.ofHours(8)), listOf("not.a.saved.patient@example.com"), MEET + "demo-aaaa-006"), // unmatched
            A("demo-07", after(Duration.ofHours(10)), listOf("anna.demo@example.com"), MEET + "demo-aaaa-007", cancelled = true),
            A("demo-08", after(Duration.ofDays(6)), listOf("bilal.demo@example.com", "emily.demo@example.com"), MEET + "demo-aaaa-008"), // two patients, two zones
            // Daylight-saving edge cases (only those still in the future):
            A("demo-dst-1", local("America/New_York", 2026, 11, 1, 9), listOf("david.demo@example.com"), MEET + "demo-dst-001"), // US fall-back day
            A("demo-dst-2", local("America/Los_Angeles", 2026, 11, 1, 10), listOf("emily.demo@example.com"), MEET + "demo-dst-002"),
            A("demo-dst-3", local("America/New_York", 2027, 3, 14, 9), listOf("david.demo@example.com"), MEET + "demo-dst-003"), // US spring-forward day
            A("demo-dst-4", local("Europe/Helsinki", 2027, 3, 29, 10), listOf("anna.demo@example.com"), MEET + "demo-dst-004"), // day after EU change
        ).filter { it.start.isAfter(now) }

        repo.upsertAppointments(list.map {
            AppointmentEntity(
                id = it.id, startMs = it.start.toEpochMilli(), endMs = it.start.plusSeconds(3000).toEpochMilli(),
                cancelled = it.cancelled, meetLink = it.meet,
                attendeesJson = AppJson.encodeToString(ListSerializer(EventAttendee.serializer()), att(*it.emails.toTypedArray())),
                changeNote = if (it.cancelled) "Cancelled in Google Calendar" else null, reviewNote = null, isDemo = true,
            )
        })
        check(ids.isNotEmpty())
    }

    /** One extra appointment [minutes] from now for the given demo patient email – handy with 5-minute test intervals. */
    suspend fun addTestAppointment(repo: Repository, patientEmail: String, minutes: Long, now: Instant = Instant.now()) {
        val start = now.plus(Duration.ofMinutes(minutes))
        repo.upsertAppointments(listOf(
            AppointmentEntity(
                id = "demo-test-${now.epochSecond}", startMs = start.toEpochMilli(), endMs = start.plusSeconds(1800).toEpochMilli(),
                cancelled = false, meetLink = MEET + "demo-test-${now.epochSecond % 1000}",
                attendeesJson = AppJson.encodeToString(ListSerializer(EventAttendee.serializer()), att(patientEmail)),
                changeNote = null, reviewNote = null, isDemo = true,
            ),
        ))
    }
}
