package com.sabaferoz.apptreminder.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun at(zone: String, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant =
    LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.of(zone)).toInstant()

fun local(i: Instant, zone: String): LocalDateTime = i.atZone(ZoneId.of(zone)).toLocalDateTime()

fun patient(
    id: Long = 1, alias: String = "Test Patient", email: String = "p@example.com", phone: String = "+358401234567",
    zone: String = "Europe/Helsinki", consent: Boolean = true,
    a: ReminderRule? = null, b: ReminderRule? = null, tA: String? = null, tB: String? = null,
) = PatientProfile(id, alias, email, phone, zone, consent, a, b, tA, tB)

fun appt(
    id: String = "evt1", start: Instant, cancelled: Boolean = false, meet: String? = "https://meet.google.com/abc-defg-hij",
    attendees: List<EventAttendee> = listOf(EventAttendee("me@example.com", self = true, organizer = true), EventAttendee("p@example.com")),
    note: String? = null,
) = AppointmentInfo(id, start, start.plusSeconds(3600), cancelled, meet, attendees, note)

val noQuiet = QuietHours(enabled = false)
