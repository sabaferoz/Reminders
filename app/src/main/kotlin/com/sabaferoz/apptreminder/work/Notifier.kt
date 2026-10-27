package com.sabaferoz.apptreminder.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sabaferoz.apptreminder.R
import com.sabaferoz.apptreminder.ui.MainActivity

object Notifier {
    const val CHANNEL = "reminders_ready"
    private const val ID = 1

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Reminders ready to send", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tells you when appointment reminders are ready to send."
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canNotify(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Text is generic by design: no names, times or links ever appear on the lock screen. */
    fun showReady(ctx: Context, count: Int) {
        if (!canNotify(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "reminders").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Appointment Reminder Assistant")
            .setContentText("Reminders are ready.").build()
        val text = if (count == 1) "1 reminder is ready to send." else "$count reminders are ready to send."
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Appointment Reminder Assistant")
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID, n) }
    }

    fun clear(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}
