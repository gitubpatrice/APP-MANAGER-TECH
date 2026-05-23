package com.filestech.appmanager.data.system

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.filestech.appmanager.MainActivity
import com.filestech.appmanager.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the user-facing notifications the background scan worker triggers.
 *
 * F-Droid friendly: no remote push, all notifications are built locally
 * from a Worker result and routed to a `MainActivity` deep-link click intent.
 *
 * On Android 13+ the user must have granted `POST_NOTIFICATIONS` for the
 * post to actually appear; we use the `notifyIfPermitted` helper to catch
 * `SecurityException` silently rather than crash.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    /**
     * Cache-threshold-breach notification. ID is stable (one per breach
     * cycle) so re-firing replaces the existing entry rather than stacking.
     */
    fun postCacheThreshold(usedBytes: Long, thresholdMb: Int) {
        // VIII D3 fix: channels are registered at startup in MainApplication.onCreate
        // — no per-post init here.
        val usedLabel = Formatter.formatShortFileSize(context, usedBytes)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(context, NotificationChannels.SCAN_CHANNEL_ID)
            // VIII M-3 fix: monochrome 24dp vector — was the launcher mipmap,
            // which Android renders washed out in the status bar.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_cache_title))
            .setContentText(
                context.getString(R.string.notif_cache_body, usedLabel, thresholdMb),
            )
            .setStyle(NotificationCompat.BigTextStyle())
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notifyIfPermitted(NOTIF_ID_CACHE_THRESHOLD, notif)
    }

    private fun notifyIfPermitted(id: Int, notif: android.app.Notification) {
        try {
            notificationManager.notify(id, notif)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted on API 33+ → silently drop.
            // The Settings screen has a UI to request grant explicitly.
        }
    }

    private companion object {
        const val REQUEST_OPEN_APP        = 1001
        const val NOTIF_ID_CACHE_THRESHOLD = 2001
    }
}
