package com.sabaferoz.apptreminder.core

import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class EventsResponse(val items: List<EventDto> = emptyList(), val nextPageToken: String? = null)

@Serializable
data class EventDto(
    val id: String,
    val status: String? = null,
    val start: EventTimeDto? = null,
    val end: EventTimeDto? = null,
    val attendees: List<AttendeeDto> = emptyList(),
    val hangoutLink: String? = null,
    val conferenceData: ConferenceDataDto? = null,
    val recurringEventId: String? = null,
)

@Serializable data class EventTimeDto(val dateTime: String? = null, val date: String? = null, val timeZone: String? = null)
@Serializable data class AttendeeDto(val email: String? = null, val self: Boolean = false, val organizer: Boolean = false, val resource: Boolean = false)
@Serializable data class ConferenceDataDto(val entryPoints: List<EntryPointDto> = emptyList())
@Serializable data class EntryPointDto(val entryPointType: String? = null, val uri: String? = null)

@Serializable data class CalendarListResponse(val items: List<CalendarListEntry> = emptyList())
@Serializable data class CalendarListEntry(val id: String, val summary: String? = null, val primary: Boolean = false, val accessRole: String? = null)

data class CalendarEvent(
    val id: String,
    val cancelled: Boolean,
    val start: Instant?,
    val end: Instant?,
    val allDay: Boolean,
    val attendees: List<EventAttendee>,
    val meetLink: String?,
)

object GoogleCalendarParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseEvents(body: String): EventsResponse = json.decodeFromString(body)
    fun parseCalendarList(body: String): CalendarListResponse = json.decodeFromString(body)

    fun toDomain(e: EventDto): CalendarEvent {
        val allDay = e.start?.dateTime == null && e.start?.date != null
        return CalendarEvent(
            id = e.id,
            cancelled = e.status == "cancelled",
            start = e.start?.dateTime?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
            end = e.end?.dateTime?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
            allDay = allDay,
            attendees = e.attendees.mapNotNull { a ->
                a.email?.takeIf { it.isNotBlank() }?.let { EventAttendee(it, a.self, a.organizer, a.resource) }
            },
            meetLink = MeetLinkExtractor.extract(e),
        )
    }
}

object MeetLinkExtractor {
    private fun isMeet(uri: String?) = uri != null && uri.trim().startsWith("https://meet.google.com/")

    /** Structured fields only: conferenceData video entry point first, then hangoutLink. Never parses free text. */
    fun extract(e: EventDto): String? {
        val video = e.conferenceData?.entryPoints.orEmpty().firstOrNull { it.entryPointType == "video" && isMeet(it.uri) }?.uri
        return (video ?: e.hangoutLink?.takeIf { isMeet(it) })?.trim()
    }
}

/** Pure merge of a fetched calendar window into locally known appointments (reschedule/cancel detection). */
object AppointmentSync {
    fun merge(
        existing: List<AppointmentInfo>,
        fetched: List<CalendarEvent>,
        windowStart: Instant,
        windowEnd: Instant,
    ): List<AppointmentInfo> {
        val byId = existing.associateBy { it.id }
        val out = mutableMapOf<String, AppointmentInfo>()
        val seen = mutableSetOf<String>()
        for (ev in fetched) {
            if (ev.allDay) continue
            seen += ev.id
            val old = byId[ev.id]
            if (ev.cancelled) {
                if (old != null && !old.cancelled) out[ev.id] = old.copy(cancelled = true, changeNote = "Cancelled in Google Calendar")
                continue
            }
            val start = ev.start ?: continue
            val end = ev.end ?: start
            val note = when {
                old == null -> null
                old.cancelled -> "Reinstated after cancellation"
                old.start != start -> "Rescheduled (was ${old.start})"
                else -> old.changeNote
            }
            out[ev.id] = AppointmentInfo(ev.id, start, end, false, ev.meetLink, ev.attendees, note)
        }
        for (old in existing) {
            if (old.id in seen || old.cancelled) continue
            if (!old.start.isBefore(windowStart) && old.start.isBefore(windowEnd)) {
                out[old.id] = old.copy(cancelled = true, changeNote = "Removed from the calendar")
            }
        }
        return out.values.filter { it != byId[it.id] }
    }
}
