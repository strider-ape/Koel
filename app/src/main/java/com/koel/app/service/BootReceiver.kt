package com.koel.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.koel.app.R
import com.koel.app.data.Prefs
import com.koel.app.ui.MainActivity

/**
 * Shizuku's service does not survive a reboot, so recording is paused until the user
 * restarts it. If auto-record was on, remind them right after boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).autoRecord) return

        val mgr = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Restart reminder", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setContentTitle("Koel: recording paused")
            .setContentText("Phone restarted. Tap to restart Shizuku and resume recording.")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        mgr.notify(NOTIF_ID, n)
    }

    companion object {
        private const val CHANNEL = "koel.reboot"
        private const val NOTIF_ID = 2
    }
}
