package com.sabaferoz.apptreminder.calendar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AuthOutcome {
    data class Token(val accessToken: String) : AuthOutcome
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthOutcome
    data class Failed(val reason: String) : AuthOutcome
}

/**
 * Uses Google Play services' AuthorizationClient: the app never handles a refresh token or client secret and stores no
 * credentials; it asks for a short-lived access token each time. Think "OAuth2 with the token vault outsourced to the OS".
 *
 * Scopes (both read-only):
 *  - calendar.events.owned.readonly : read events on calendars the signed-in user owns (narrowest scope that works for events.list)
 *  - calendar.calendarlist.readonly : list calendars so the manager can pick the appointment calendar
 * NOTE: OAuth scopes apply to the whole Google account, not to one calendar. Choosing a calendar in this app only limits what
 * the app *asks for*; it does not technically restrict what the granted token could read.
 */
class GoogleAuth(private val ctx: Context) {
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE_EVENTS), Scope(SCOPE_CALENDAR_LIST)))
        .build()

    suspend fun authorize(): AuthOutcome = try {
        val result = Identity.getAuthorizationClient(ctx).authorize(request).await()
        val pi = result.pendingIntent
        when {
            result.hasResolution() && pi != null -> AuthOutcome.NeedsConsent(pi)
            result.accessToken != null -> AuthOutcome.Token(result.accessToken!!)
            else -> AuthOutcome.Failed("Google returned no access token.")
        }
    } catch (e: Exception) {
        AuthOutcome.Failed(e.message ?: "Google authorization failed.")
    }

    fun tokenFromConsent(data: Intent?): AuthOutcome = try {
        val r = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data)
        r.accessToken?.let { AuthOutcome.Token(it) } ?: AuthOutcome.Failed("Consent finished without a token.")
    } catch (e: Exception) {
        AuthOutcome.Failed(e.message ?: "Consent was cancelled.")
    }

    /** Best-effort revocation of the grant at Google; local state is cleared by the caller regardless. */
    suspend fun revoke(accessToken: String) = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://oauth2.googleapis.com/revoke").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write("token=${java.net.URLEncoder.encode(accessToken, "UTF-8")}".toByteArray()) }
            c.responseCode
            c.disconnect()
        }
    }

    companion object {
        const val SCOPE_EVENTS = "https://www.googleapis.com/auth/calendar.events.owned.readonly"
        const val SCOPE_CALENDAR_LIST = "https://www.googleapis.com/auth/calendar.calendarlist.readonly"
    }
}
