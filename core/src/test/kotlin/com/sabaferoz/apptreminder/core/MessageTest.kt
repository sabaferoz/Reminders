package com.sabaferoz.apptreminder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageTest {
    private val link = "https://meet.google.com/abc-defg-hij"

    @Test fun rendersInPatientLocalTimeWithDstAwareOffset() {
        val start = at("America/New_York", 2026, 11, 1, 9, 0) // after fall-back → EST (-05:00)
        val r = MessageRenderer.render(MessageTemplates.DEFAULT_A, MessageContext("X", start, java.time.ZoneId.of("America/New_York"), link))
        assertTrue(r.ok)
        assertTrue(r.text.contains("Date: Sunday, 1 November 2026"))
        assertTrue(r.text.contains("Time: 9:00 AM (America/New_York, UTC-05:00)"))
        assertTrue(r.text.contains(link))
        val summer = at("America/New_York", 2026, 10, 31, 9, 0)
        assertTrue(MessageRenderer.render("{patient_timezone}", MessageContext("X", summer, java.time.ZoneId.of("America/New_York"), link)).text.endsWith("UTC-04:00"))
    }

    @Test fun finlandVsPakistanSameInstantDifferentLabels() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        val fi = MessageRenderer.render("{appointment_time} {patient_timezone}", MessageContext("X", start, java.time.ZoneId.of("Europe/Helsinki"), link)).text
        val pk = MessageRenderer.render("{appointment_time} {patient_timezone}", MessageContext("X", start, java.time.ZoneId.of("Asia/Karachi"), link)).text
        assertEquals("3:00 PM Europe/Helsinki, UTC+02:00", fi)
        assertEquals("6:00 PM Asia/Karachi, UTC+05:00", pk)
    }

    @Test fun missingMeetLinkIsFlagged() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        val r = MessageRenderer.render(MessageTemplates.DEFAULT_B, MessageContext("X", start, java.time.ZoneId.of("Europe/Helsinki"), null))
        assertFalse(r.ok)
        val r2 = MessageRenderer.render("Hi {patient_name}", MessageContext("X", start, java.time.ZoneId.of("Europe/Helsinki"), null))
        assertTrue(r2.ok) // template without link does not need one
    }

    @Test fun nonMeetUrlIsRejected() {
        assertFalse(MessageRenderer.isValidMeetLink("https://evil.example/meet"))
        assertFalse(MessageRenderer.isValidMeetLink("http://meet.google.com/abc"))
        assertTrue(MessageRenderer.isValidMeetLink(link))
    }

    @Test fun unknownPlaceholderAndRelativeWordsValidation() {
        val problems = MessageRenderer.validateTemplate("See you tomorrow {patient_nam} {meet_link}")
        assertTrue(problems.any { it.contains("{patient_nam}") })
        assertTrue(problems.any { it.contains("tomorrow") })
        assertTrue(MessageRenderer.validateTemplate(MessageTemplates.DEFAULT_A).isEmpty())
        assertTrue(MessageRenderer.validateTemplate(MessageTemplates.DEFAULT_B).isEmpty())
    }

    @Test fun valuesContainingBracesAreNotReSubstituted() {
        val start = at("Europe/Helsinki", 2026, 11, 3, 15, 0)
        val r = MessageRenderer.render("{patient_name}", MessageContext("{meet_link}", start, java.time.ZoneId.of("Europe/Helsinki"), link))
        assertEquals("{meet_link}", r.text)
    }

    @Test fun patientTemplateOverridesGlobal() {
        val a = appt(start = at("Europe/Helsinki", 2026, 11, 3, 15, 0))
        val s = ReminderSettings()
        assertEquals(MessageTemplates.DEFAULT_A, OutgoingMessageBuilder.template(ReminderKind.A, patient(), s))
        assertEquals("Custom {meet_link}", OutgoingMessageBuilder.template(ReminderKind.A, patient(tA = "Custom {meet_link}"), s))
        assertEquals(MessageTemplates.DEFAULT_B, OutgoingMessageBuilder.template(ReminderKind.B, patient(tA = "Custom"), s))
        assertTrue(OutgoingMessageBuilder.render(ReminderKind.A, a, patient(tA = "Hi {patient_name}"), s).text == "Hi Test Patient")
    }
}
