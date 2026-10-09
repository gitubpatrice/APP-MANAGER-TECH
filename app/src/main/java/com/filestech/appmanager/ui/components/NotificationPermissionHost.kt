package com.filestech.appmanager.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.filestech.appmanager.data.local.datastore.AppSettings
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.model.ScanInterval
import com.filestech.appmanager.domain.repository.QuarantineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * v0.5.1 — asks for the notification permission ONCE per launch, when a notification is already turned
 * on: permission-change alerts, a cache threshold with the automatic scan running, or a quarantine whose
 * review reminder is on (the reminder is on by default).
 *
 * The settings ask when a notification is turned ON ([rememberNotificationPermissionRequest]); this host
 * covers what no toggle ever reaches — a user updating from a version that never asked, with those
 * notifications already on, or a quarantine created with the default reminder. Asking when the
 * quarantine is confirmed would put the permission dialog and Android's uninstall dialog on screen
 * together; the next launch asks instead, long before a review date. Silent when refused: Android
 * itself stops showing the dialog after two refusals, and the settings say why nothing arrives.
 *
 * Mounted once, outside the NavHost, next to the uninstall-reason host.
 */
@Composable
fun NotificationPermissionHost(viewModel: NotificationNeedViewModel = hiltViewModel()) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    // Saveable: a rotation or a language change recreates the activity; it is still the same launch.
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (asked) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && viewModel.notificationsNeeded()) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@HiltViewModel
class NotificationNeedViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val quarantine: QuarantineRepository,
) : ViewModel() {

    /** Reads the settings and the quarantine count once, as they are at launch. */
    suspend fun notificationsNeeded(): Boolean =
        notificationsNeeded(settings.flow.first(), quarantine.observeCount().first())

    companion object {
        /**
         * True when at least one notification of the app is turned on and could fire. The cache alert
         * comes only from the automatic scan, hence the interval; a quarantine reminder needs a quarantine.
         */
        fun notificationsNeeded(settings: AppSettings, quarantineCount: Int): Boolean =
            (settings.privacyMonitor.permissionDriftEnabled && settings.privacyMonitor.permissionDriftNotify) ||
                (settings.scanner.cacheThresholdMb > 0 && settings.scanner.autoScanInterval != ScanInterval.OFF) ||
                (settings.quarantine.restoreReminderEnabled && quarantineCount > 0)
    }
}
