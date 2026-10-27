package com.sabaferoz.apptreminder.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object MessageTemplates {
    const val DEFAULT_A = "Hello,\nThis is a reminder of your upcoming appointment.\n" +
        "Date: {appointment_date}\nTime: {appointment_time} ({patient_timezone})\n" +
        "Google Meet:\n{meet_link}\nThank you."
    const val DEFAULT_B = "Hello,\nThis is a reminder of your appointment.\n" +
        "Date: {appointment_date}\nTime: {appointment_time} ({patient_timezone})\n" +
        "Google Meet:\n{meet_link}\nThank you."
}

data class MessageContext(
    val patientName: String,
    val start: Instant,
    val zone: ZoneId,
    val meetLink: String?,
)

data class RenderedMessage(val text: String, val errors: List<String>, val warnings: List<String>) {
    val ok get() = errors.isEmpty()
}

object MessageRenderer {
    val PLACEHOLDERS = listOf("patient_name", "appointment_date", "appointment_time", "patient_timezone", "meet_link")
    private val token = Regex("\\{([A-Za-z_]+)\\}")
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val relativeDayWords = Regex("\\b(today|tomorrow|tonight|yesterday)\\b", RegexOption.IGNORE_CASE)

    fun formatDate(start: Instant, zone: ZoneId): String = dateFmt.format(start.atZone(zone))
    fun formatTime(start: Instant, zone: ZoneId): String = timeFmt.format(start.atZone(zone))

    /** e.g. "Europe/Helsinki, UTC+02:00" – offset is the one in force on the appointment date (DST-aware). */
    fun zoneLabel(start: Instant, zone: ZoneId): String {
        val secs = zone.rules.getOffset(start).totalSeconds
        val sign = if (secs < 0) "-" else "+"
        val a = kotlin.math.abs(secs)
        return "${zone.id}, UTC%s%02d:%02d".format(sign, a / 3600, (a % 3600) / 60)
    }

    fun isValidMeetLink(link: String?): Boolean =
        link != null && Regex("^https://meet\\.google\\.com/[A-Za-z0-9\\-_/?=&.]+$").matches(link.trim())

    fun validateTemplate(template: String): List<String> {
        val problems = mutableListOf<String>()
        if (template.isBlank()) problems += "Template is empty."
        token.findAll(template).map { it.groupValues[1] }.filter { it !in PLACEHOLDERS }.distinct()
            .forEach { problems += "Unknown placeholder {$it}." }
        if (relativeDayWords.containsMatchIn(template)) {
            problems += "Avoid words like \"today\" or \"tomorrow\": they may be wrong in the patient's local calendar."
        }
        return problems
    }

    fun render(template: String, ctx: MessageContext): RenderedMessage {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val usesLink = Regex("\\{meet_link\\}").containsMatchIn(template)
        if (usesLink && !isValidMeetLink(ctx.meetLink)) {
            errors += if (ctx.meetLink.isNullOrBlank()) "No Google Meet link on this appointment." else "Meet link is not a meet.google.com URL."
        }
        val text = token.replace(template) { m ->
            when (val name = m.groupValues[1]) {
                "patient_name" -> ctx.patientName
                "appointment_date" -> formatDate(ctx.start, ctx.zone)
                "appointment_time" -> formatTime(ctx.start, ctx.zone)
                "patient_timezone" -> zoneLabel(ctx.start, ctx.zone)
                "meet_link" -> ctx.meetLink?.trim().orEmpty()
                else -> { errors += "Unknown placeholder {$name}."; m.value }
            }
        }
        if (relativeDayWords.containsMatchIn(template)) warnings += "Template uses a relative day word (today/tomorrow)."
        return RenderedMessage(text, errors.distinct(), warnings)
    }
}
