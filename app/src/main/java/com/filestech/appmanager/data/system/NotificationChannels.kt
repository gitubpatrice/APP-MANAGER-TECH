package com.filestech.appmanager.data.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification channels registry.
 *
 * Phase VIII D3 fix: converted from `object` to `@Singleton class @Inject
 * constructor`. The previous lazy-init pattern (early-return if channel
 * exists, called from `NotificationHelper.postCacheThreshold`) had two
 * issues: (1) if a WorkManager-resurrected process posted a notif before
 * the helper was ever called, the channel didn't exist and the notif was
 * silently dropped; (2) it was harder to update channel metadata later.
 *
 * Now [ensureRegistered] is called once at startup from
 * [com.filestech.appmanager.MainApplication.onCreate]. Subsequent calls are
 * still safe — `createNotificationChannel` is idempotent on Android and
 * merges modifiable properties on update.
 *
 * Channels:
 * - [SCAN_CHANNEL_ID]: background-scan results (cache threshold breach,
 *   periodic worker outcome). Importance = DEFAULT — not noisy.
 *
 * F-Droid: notifications are local-only; no FCM, no remote payload.
 */
@Singleton
class NotificationChannels @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Idempotent — Android dedupes by ID and merges modifiable properties. */
    fun ensureRegistered() {
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        // VIII L-5 fix: no early-return on existing channel — Android dedupes
        // by ID and merges modifiable properties on re-create (importance is
        // locked once set by the user, but description/group/etc. update).
        val channel = NotificationChannel(
            SCAN_CHANNEL_ID,
            "Background scan",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifications fired by the periodic catalogue scan worker."
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        const val SCAN_CHANNEL_ID = "scan"
    }
}
