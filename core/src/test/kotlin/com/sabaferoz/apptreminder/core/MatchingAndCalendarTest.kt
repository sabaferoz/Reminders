package com.sabaferoz.apptreminder.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchingAndCalendarTest {
    private val me = EventAttendee("Me@Clinic.com", self = true, organizer = true)

    @Test fun matchesByNormalizedEmail() {
        val m = AttendeeMatcher.match(listOf(me, EventAttendee("  P@Example.COM ")), emptyList(), listOf(patient(email = "p@example.com")))
        assertEquals(1, m.matched.size)
        assertNull(m.reviewNote)
    }

    @Test fun staffEmailsAndOrganizerAreNeverPatients() {
        val staff = patient(id = 9, email = "manager@clinic.com")
        val m = AttendeeMatcher.match(listOf(me, EventAttendee("manager@clinic.com"), EventAttendee("doc@clinic.com", organizer = true)), listOf("Manager@clinic.com"), listOf(staff))
        assertTrue(m.matched.isEmpty())
        assertNotNull(m.reviewNote)
    }

    @Test fun missingAttendeesFlagged() {
        val m = AttendeeMatcher.match(listOf(me), emptyList(), listOf(patient()))
        assertTrue(m.matched.isEmpty()); assertNotNull(m.reviewNote)
    }

    @Test fun duplicatePatientEmailIsAmbiguousAndNotMatched() {
        val m = AttendeeMatcher.match(listOf(me, EventAttendee("p@example.com")), emptyList(), listOf(patient(id = 1), patient(id = 2)))
        assertTrue(m.matched.isEmpty())
        assertEquals(listOf("p@example.com"), m.ambiguousEmails)
    }

    @Test fun multiplePatientsMatchedIndividually() {
        val m = AttendeeMatcher.match(
            listOf(me, EventAttendee("a@x.com"), EventAttendee("b@x.com"), EventAttendee("stranger@x.com")), emptyList(),
            listOf(patient(id = 1, email = "a@x.com"), patient(id = 2, email = "b@x.com", zone = "Asia/Karachi")),
        )
        assertEquals(listOf(1L, 2L), m.matched.map { it.id })
        assertEquals(listOf("stranger@x.com"), m.unknownEmails)
    }

    private val sample = """
    {"kind":"calendar#events","items":[
     {"id":"e1","status":"confirmed","summary":"SECRET TITLE","start":{"dateTime":"2026-11-03T15:00:00+02:00","timeZone":"Europe/Helsinki"},
      "end":{"dateTime":"2026-11-03T16:00:00+02:00"},"hangoutLink":"https://meet.google.com/aaa-bbbb-ccc",
      "conferenceData":{"entryPoints":[{"entryPointType":"video","uri":"https://meet.google.com/aaa-bbbb-ccc"},{"entryPointType":"phone","uri":"tel:+1555"}]},
      "attendees":[{"email":"me@clinic.com","self":true,"organizer":true},{"email":"p@example.com","responseStatus":"accepted"}]},
     {"id":"e2","status":"confirmed","start":{"date":"2026-11-04"},"end":{"date":"2026-11-05"}},
     {"id":"e3","status":"cancelled"},
     {"id":"e4","status":"confirmed","start":{"dateTime":"2026-11-03T10:00:00Z"},"end":{"dateTime":"2026-11-03T11:00:00Z"},"hangoutLink":"https://zoom.us/j/123"}
    ],"nextPageToken":"tok"}
    """.trimIndent()

    @Test fun parsesEventsAndExtractsMeetFromStructuredFields() {
        val r = GoogleCalendarParser.parseEvents(sample)
        assertEquals("tok", r.nextPageToken)
        val ev = r.items.map(GoogleCalendarParser::toDomain)
        assertEquals("https://meet.google.com/aaa-bbbb-ccc", ev[0].meetLink)
        assertEquals(Instant.parse("2026-11-03T13:00:00Z"), ev[0].start)
        assertEquals(2, ev[0].attendees.size)
        assertTrue(ev[1].allDay)
        assertTrue(ev[2].cancelled)
        assertNull(ev[3].meetLink) // non-Meet URL never accepted
    }

    @Test fun hangoutLinkFallbackWhenNoEntryPoints() {
        val e = EventDto("x", hangoutLink = "https://meet.google.com/zzz-yyyy-xxx")
        assertEquals("https://meet.google.com/zzz-yyyy-xxx", MeetLinkExtractor.extract(e))
        assertNull(MeetLinkExtractor.extract(EventDto("y")))
    }

    @Test fun emptyCalendarParses() {
        assertTrue(GoogleCalendarParser.parseEvents("""{"items":[]}""").items.isEmpty())
        assertTrue(GoogleCalendarParser.parseEvents("{}").items.isEmpty())
    }

    private fun ev(id: String, start: Instant, cancelled: Boolean = false) =
        CalendarEvent(id, cancelled, start, start.plusSeconds(3600), false, listOf(EventAttendee("p@example.com")), "https://meet.google.com/a-b-c")

    @Test fun rescheduleAndCancellationDetected() {
        val t = Instant.parse("2026-11-03T10:00:00Z")
        val existing = listOf(appt("e1", t))
        val moved = AppointmentSync.merge(existing, listOf(ev("e1", t.plusSeconds(7200))), t.minusSeconds(86400), t.plusSeconds(86400 * 14))
        assertEquals(t.plusSeconds(7200), moved.single().start)
        assertTrue(moved.single().changeNote!!.startsWith("Rescheduled"))

        val cancelled = AppointmentSync.merge(existing, listOf(ev("e1", t, cancelled = true)), t.minusSeconds(86400), t.plusSeconds(86400 * 14))
        assertTrue(cancelled.single().cancelled)

        val removed = AppointmentSync.merge(existing, emptyList(), t.minusSeconds(86400), t.plusSeconds(86400 * 14))
        assertTrue(removed.single().cancelled)

        val outsideWindow = AppointmentSync.merge(existing, emptyList(), t.plusSeconds(86400), t.plusSeconds(86400 * 14))
        assertTrue(outsideWindow.isEmpty())
    }

    @Test fun unchangedEventProducesNoWrite() {
        val t = Instant.parse("2026-11-03T10:00:00Z")
        val e = ev("e1", t)
        val existing = listOf(AppointmentInfo("e1", t, t.plusSeconds(3600), false, e.meetLink, e.attendees))
        assertTrue(AppointmentSync.merge(existing, listOf(e), t.minusSeconds(1), t.plusSeconds(100000)).isEmpty())
    }

    @Test fun phoneValidationAndLinks() {
        assertEquals("+358401234567", PhoneValidator.normalize("+358 40 123 4567"))
        assertEquals("+923001234567", PhoneValidator.normalize("00 92 300-1234567"))
        assertNull(PhoneValidator.normalize("0401234567"))
        assertNull(PhoneValidator.normalize("+12"))
        assertNull(PhoneValidator.normalize("+35840abc"))
        assertNotNull(PhoneValidator.error(""))
        assertEquals("https://wa.me/358401234567?text=Hi%20there%0AMeet%3A%20https%3A%2F%2Fmeet.google.com%2Fa", WhatsAppLink.build("+358401234567", "Hi there\nMeet: https://meet.google.com/a"))
    }

    @Test fun timeZoneResolver() {
        val r = TimeZoneResolver { iso -> if (iso == "BR") listOf("America/Sao_Paulo", "America/Manaus", "Bogus/Zone") else emptyList() }
        assertEquals(ZoneResolution.Single("Europe/Helsinki"), r.resolve("fi"))
        assertEquals(ZoneResolution.Single("Asia/Karachi"), r.resolve("PK"))
        assertEquals(ZoneResolution.Single("Europe/London"), r.resolve("GB"))
        val us = r.resolve("US") as ZoneResolution.NeedsRegion
        assertTrue(us.options.any { it.zoneId == "America/New_York" && it.label.contains("New York") })
        assertTrue(us.options.any { it.zoneId == "America/Los_Angeles" && it.label.contains("Los Angeles") })
        assertEquals(2, (r.resolve("BR") as ZoneResolution.NeedsRegion).options.size)
        assertEquals(ZoneResolution.Unknown, r.resolve("ZZ"))
        assertTrue(r.countries().any { it.iso == "FI" && it.name == "Finland" })
        // every curated zone id must be valid
        (Curated.single.values + Curated.regions.values.flatten().map { it.zoneId }).forEach { assertTrue(TimeZoneResolver.valid(it), it) }
        assertTrue(TimeZoneResolver.search("new york", r.allIanaZones()).contains("America/New_York"))
    }
}
