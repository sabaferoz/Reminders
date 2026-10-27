package com.sabaferoz.apptreminder.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sabaferoz.apptreminder.App
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Why two mechanisms?
 *  - AlarmManager.setAndAllowWhileIdle wakes the app close to the next due time (it can be deferred by Doze by a few
 *    minutes, which is fine for "prepare a reminder"). It needs no special permission, unlike exact alarms
 *    (SCHEDULE_EXACT_ALARM), which would be excessive for a manager who must tap Send anyway.
 *  - WorkManager's 15-minute periodic job is the safety net: it also syncs the calendar and catches missed alarms
 *    (force-stop, battery optimisation, OEM task killers). Alarms are re-armed after reboot / time-zone / clock changes.
 * Android cannot guarantee background work on every vendor ROM; opening the app always re-syncs and re-evaluates.
 */
object Scheduling {
    private const val ACTION = "com.sabaferoz.apptreminder.TICK"

    fun ensurePeriodic(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("periodic-sync", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun armAlarm(ctx: Context, at: Instant?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) { am.cancel(pi); return }
        val whenMs = maxOf(at.toEpochMilli(), System.currentTimeMillis() + 1_000)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
    }

    /** Re-evaluate reminders (optionally syncing first), notify, and arm the next alarm. */
    suspend fun tick(ctx: Context, syncCalendar: Boolean) {
        val g = (ctx.applicationContext as App).graph
        val now = Instant.now()
        if (syncCalendar) g.sync.sync(now) else g.repo.reconcile(now)
        val (ready, fresh) = g.repo.readyCountAndMarkNotified(now)
        if (ready == 0) Notifier.clear(ctx) else if (fresh) Notifier.showReady(ctx, ready)
        armAlarm(ctx, g.repo.nextDueAfter(now))
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        Scheduling.tick(applicationContext, syncCalendar = true)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

abstract class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { Scheduling.tick(context.applicationContext, syncCalendar = false) } catch (_: Exception) { } finally { pending.finish() }
        }
    }
}

class AlarmReceiver : TickReceiver()

/** Boot, clock change, time-zone change, app update: alarms are lost or stale, so re-arm. */
class BootReceiver : TickReceiver()
