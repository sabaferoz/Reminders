package com.sabaferoz.apptreminder.calendar

import com.sabaferoz.apptreminder.core.AppointmentSync
import com.sabaferoz.apptreminder.data.Repository
import com.sabaferoz.apptreminder.data.SettingsRepo
import com.sabaferoz.apptreminder.data.toCore
import com.sabaferoz.apptreminder.data.toEntity
import com.sabaferoz.apptreminder.demo.DemoData
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CalendarSync(
    private val repo: Repository,
    private val settingsRepo: SettingsRepo,
    private val auth: GoogleAuth,
    private val api: CalendarApi,
) {
    private val mutex = Mutex()

    /** Returns a short human-readable status; never throws, never logs patient data. */
    suspend fun sync(now: Instant = Instant.now()): String = mutex.withLock {
        val s = settingsRepo.get()
        val status = when {
            s.demoMode -> { DemoData.seed(repo, now); "Demo mode: using fictional data." }
            !s.googleConnected -> "Google Calendar is not connected."
            else -> fetchGoogle(now, s.calendarId ?: "primary")
        }
        repo.reconcile(now)
        settingsRepo.update { it.copy(lastSyncAtMs = now.toEpochMilli(), lastSyncStatus = status) }
        status
    }

    private suspend fun fetchGoogle(now: Instant, calendarId: String): String {
        val token = when (val a = auth.authorize()) {
            is AuthOutcome.Token -> a.accessToken
            is AuthOutcome.NeedsConsent -> return "Google permission needs to be granted again. Open Settings → Reconnect."
            is AuthOutcome.Failed -> return "Could not authorize with Google. Showing last synced data."
        }
        val from = now.minus(Duration.ofDays(1))
        val to = now.plus(Duration.ofDays(30))
        return try {
            val fetched = api.listEvents(token, calendarId, from, to)
            val existing = repo.loadAppointments(demo = false)
            val reviews = existing.associate { it.id to it.reviewNote }
            val changed = AppointmentSync.merge(existing.map { it.toCore() }, fetched, from, to)
            repo.upsertAppointments(changed.map { it.toEntity(isDemo = false, reviewNote = reviews[it.id]) })
            if (fetched.none { !it.allDay && !it.cancelled }) "Synced: no upcoming appointments in this calendar." else "Synced ${fetched.size} calendar entries."
        } catch (e: CalendarHttpException) {
            when (e.code) {
                401 -> "Google sign-in expired or was revoked. Open Settings → Reconnect."
                403 -> "Google denied access (is the Calendar API enabled and are you a test user?)."
                404 -> "Selected calendar was not found. Choose a calendar in Settings."
                429 -> "Google rate limit reached; will retry later."
                else -> "Google Calendar error (${e.code}). Showing last synced data."
            }
        } catch (e: IOException) {
            "Offline: showing last synced data."
        }
    }
}
