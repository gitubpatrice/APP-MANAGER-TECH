package com.filestech.appmanager.data.system

import android.app.NotificationManager
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
 * post to actually appear. v0.5.1 — `notify()` does not throw when the
 * notifications are refused or switched off: Android drops the post without
 * a word. `notifyIfPermitted` therefore checks [canPostNotifications] first,
 * and still catches a `SecurityException` rather than crash.
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
            // Posted at every scan while the cache stays above the threshold: same id, so it is
            // replaced; without this it would ring and vibrate again each time.
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notifyIfPermitted(NOTIF_ID_CACHE_THRESHOLD, notif)
    }

    /**
     * Permission Drift summary — fired when the snapshot worker detects at
     * least one drift event this tick. Stable ID — re-firing replaces the
     * previous entry instead of stacking.
     *
     * Tap → deep-links into App Manager (the user navigates to the Drift
     * screen from the home shell). v0.3.0 will add a direct deep-link route.
     */
    fun postPermissionDriftSummary(driftCount: Int): Boolean {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_DRIFT,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(context, NotificationChannels.DRIFT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_drift_title))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.notif_drift_body, driftCount, driftCount,
                ),
            )
            .setStyle(NotificationCompat.BigTextStyle())
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return notifyIfPermitted(NOTIF_ID_DRIFT_SUMMARY, notif)
    }

    /**
     * Quarantine expiry — one notif per expired entry. Distinct stable IDs
     * derived from `packageName.hashCode()` so multiple expirations don't
     * squash each other.
     *
     * v0.2.0 audit M-1 fix: the previous `hashCode() and 0x7FFF` masked the
     * IDs to a 15-bit space (~32k values) → birthday-paradox collisions at
     * ~180 distinct packages quarantined in the process lifetime. The full
     * 32-bit hash gives 2^31 distinct IDs, and `Int.MAX_VALUE`-sized
     * decoration offsets keep the notif and PendingIntent request-code
     * spaces non-overlapping with the other notif emitters.
     *
     * Returns true iff the notification was actually posted (POST_NOTIFICATIONS
     * granted). The worker uses this to decide whether to `markNotified`.
     */
    fun postQuarantineExpired(packageName: String, label: String): Boolean {
        // v0.2.1 audit M2 fix — `hashCode()` can return Int.MIN_VALUE (negative).
        // `notify(negativeId, ...)` is silently dropped or aliased to ID 0 on
        // some OEMs (Samsung, Xiaomi). `and Int.MAX_VALUE` masks the sign bit
        // → IDs are guaranteed in [0, Int.MAX_VALUE], still 2^31 values.
        val stableId = packageName.hashCode() and Int.MAX_VALUE
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_QUARANTINE_BASE + stableId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(context, NotificationChannels.QUARANTINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_quarantine_title, label))
            .setContentText(context.getString(R.string.notif_quarantine_body, label))
            .setStyle(NotificationCompat.BigTextStyle())
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return notifyIfPermitted(NOTIF_ID_QUARANTINE_BASE + stableId, notif)
    }

    /**
     * Returns true iff the notification was actually posted. Caller uses the
     * outcome to decide follow-up state (e.g. flipping `notified = true` on
     * the Room row only if the user actually saw the alert).
     *
     * v0.5.1 — returned true for every post that did not throw, and a refused
     * or switched-off notification does not throw: the quarantine worker
     * marked as delivered a reminder nobody had seen. Checked before posting.
     */
    private fun notifyIfPermitted(id: Int, notif: android.app.Notification): Boolean {
        val channelId = notif.channelId ?: return false
        if (!canPostNotifications(context, channelId)) return false
        return try {
            notificationManager.notify(id, notif)
            true
        } catch (expected: SecurityException) {
            // POST_NOTIFICATIONS withdrawn between the check and the post.
            // The Settings screens ask for it again when the user switches on
            // a setting that notifies.
            false
        }
    }

    private companion object {
        // Single-instance request codes / notif IDs — non-overlapping with
        // the per-package quarantine namespace below.
        const val REQUEST_OPEN_APP         = 1001
        const val REQUEST_OPEN_DRIFT       = 1002

        const val NOTIF_ID_CACHE_THRESHOLD = 2001
        const val NOTIF_ID_DRIFT_SUMMARY   = 2002

        // Per-package quarantine namespace: `BASE + pkg.hashCode()` (full
        // 32-bit). Bases chosen far from the single-instance constants so
        // no addition can land on one of them (Int overflow wraps cleanly).
        // v0.2.0 audit M-1 fix — was a 15-bit hash, now full 32-bit.
        const val NOTIF_ID_QUARANTINE_BASE = 1_000_000
        const val REQUEST_OPEN_QUARANTINE_BASE = 2_000_000
    }
}

/**
 * v0.5.1 — Whether a notification posted on [channelId] would be shown. False when the app's
 * notifications are off — from Android 13, also while `POST_NOTIFICATIONS` is not granted — or when
 * the user switched that channel off. In all three cases `notify()` drops the post silently.
 *
 * Checked before every post, and by the Settings screens to tell the user that an alert they just
 * switched on cannot appear.
 */
fun canPostNotifications(context: Context, channelId: String): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    // Channels are registered at startup (MainApplication.onCreate); a post on a missing one is
    // dropped as well.
    val channel = manager.getNotificationChannel(channelId) ?: return false
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}
