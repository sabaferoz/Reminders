package com.sabaferoz.apptreminder.calendar

import com.sabaferoz.apptreminder.core.CalendarEvent
import com.sabaferoz.apptreminder.core.CalendarListEntry
import com.sabaferoz.apptreminder.core.GoogleCalendarParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarHttpException(val code: Int) : IOException("HTTP $code")

/** Read-only GET requests only. There is deliberately no code path that can write to Google Calendar. */
class CalendarApi {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(url: String, token: String): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code !in 200..299) throw CalendarHttpException(code)
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun listCalendars(token: String): List<CalendarListEntry> =
        GoogleCalendarParser.parseCalendarList(
            get("$BASE/users/me/calendarList?minAccessRole=owner&fields=items(id,summary,primary,accessRole)", token),
        ).items

    suspend fun listEvents(token: String, calendarId: String, from: Instant, to: Instant): List<CalendarEvent> {
        val out = mutableListOf<CalendarEvent>()
        var page: String? = null
        do {
            // `fields` keeps titles/descriptions (which may be clinical) from ever being downloaded.
            val url = buildString {
                append("$BASE/calendars/${enc(calendarId)}/events?singleEvents=true&showDeleted=true&orderBy=startTime&maxResults=250")
                append("&timeMin=${enc(from.toString())}&timeMax=${enc(to.toString())}")
                append("&fields=${enc("nextPageToken,items(id,status,start,end,attendees(email,self,organizer,resource),hangoutLink,conferenceData(entryPoints(entryPointType,uri)),recurringEventId)")}")
                if (page != null) append("&pageToken=${enc(page!!)}")
            }
            val resp = GoogleCalendarParser.parseEvents(get(url, token))
            out += resp.items.map(GoogleCalendarParser::toDomain)
            page = resp.nextPageToken
        } while (page != null)
        return out
    }

    private companion object { const val BASE = "https://www.googleapis.com/calendar/v3" }
}
