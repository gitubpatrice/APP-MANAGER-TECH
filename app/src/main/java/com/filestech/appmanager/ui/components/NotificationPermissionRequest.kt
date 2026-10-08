package com.filestech.appmanager.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import com.filestech.appmanager.R
import com.filestech.appmanager.data.system.canPostNotifications
import kotlinx.coroutines.launch

/**
 * v0.5.1 — Asks for the right to post notifications when the user switches on a setting that leads
 * to one: permission-change alerts, quarantine review reminders, cache threshold.
 *
 * The manifest declares `POST_NOTIFICATIONS`, but from Android 13 a declared permission is not a
 * granted one, and nothing ever asked for it: the three notifications were dropped without a word
 * unless the user allowed them by hand in Android's settings.
 *
 * Returns the function to call, with the notification's channel, once the setting is saved. The
 * setting keeps the user's choice either way. When the notifications stay blocked — permission
 * refused, or the app or that channel switched off in Android's settings — a snackbar on
 * [snackbarHostState] says so and offers to open the app's notification settings.
 */
@Composable
fun rememberNotificationPermissionRequest(snackbarHostState: SnackbarHostState): (channelId: String) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // Saveable: the activity can be recreated while the system dialog is up.
    var pendingChannel by rememberSaveable { mutableStateOf<String?>(null) }

    val warnIfBlocked: (String) -> Unit = { channelId ->
        if (!canPostNotifications(context, channelId)) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message           = resources.getString(R.string.notifications_blocked),
                    actionLabel       = resources.getString(R.string.notifications_blocked_action),
                    withDismissAction = true,
                    duration          = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) openAppNotificationSettings(context)
            }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Granted or not, the channel itself may be switched off: the same check answers both.
        pendingChannel?.let(warnIfBlocked)
        pendingChannel = null
    }
    return { channelId ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Once refused twice, Android answers "refused" without showing anything: the snackbar
            // in the callback is then the only way to the setting.
            pendingChannel = channelId
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            warnIfBlocked(channelId)
        }
    }
}

/**
 * Opens Android's notification page for this app. Same fallback as the language row in Settings:
 * a manufacturer that does not expose the page gets the app's details page, one tap away from it.
 */
private fun openAppNotificationSettings(context: Context) {
    val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(notifications) }
        .onFailure { runCatching { context.startActivity(details) } }
}
