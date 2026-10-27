package com.sabaferoz.apptreminder.core

object Emails {
    fun normalize(email: String): String = email.trim().lowercase()
    fun isValid(email: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
}

data class MatchResult(
    val matched: List<PatientProfile>,
    /** Attendee emails shared by more than one saved patient – never used as recipients. */
    val ambiguousEmails: List<String>,
    /** Non-staff attendees with no saved patient. */
    val unknownEmails: List<String>,
    val reviewNote: String?,
)

object AttendeeMatcher {
    fun match(attendees: List<EventAttendee>, staffEmails: List<String>, patients: List<PatientProfile>): MatchResult {
        val staff = staffEmails.map(Emails::normalize).toSet()
        val candidates = attendees
            .filter { !it.self && !it.organizer && !it.resource }
            .map { Emails.normalize(it.email) }
            .filter { it.isNotEmpty() && it !in staff }
            .distinct()

        val byEmail = patients.groupBy { Emails.normalize(it.email) }
        val matched = mutableListOf<PatientProfile>()
        val ambiguous = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        for (e in candidates) {
            val hits = byEmail[e].orEmpty()
            when {
                hits.size == 1 -> matched += hits[0]
                hits.size > 1 -> ambiguous += e
                else -> unknown += e
            }
        }
        val note = when {
            candidates.isEmpty() -> "No patient attendee on this event."
            ambiguous.isNotEmpty() -> "Attendee email is saved for more than one patient; fix the directory."
            matched.isEmpty() -> "No saved patient matches the attendees."
            unknown.isNotEmpty() -> "Event also has attendees who are not saved patients (they will not receive reminders)."
            else -> null
        }
        return MatchResult(matched.distinctBy { it.id }, ambiguous, unknown, note)
    }
}
