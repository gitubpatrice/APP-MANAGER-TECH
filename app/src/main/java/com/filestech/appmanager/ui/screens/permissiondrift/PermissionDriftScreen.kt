package com.filestech.appmanager.ui.screens.permissiondrift

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.PermissionDrift
import com.filestech.appmanager.domain.model.SnapshotStats
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.theme.BrandDanger
import java.text.DateFormat
import java.util.Date

/**
 * Permission Drift Tracker — chronological feed of every permission state
 * change captured by the periodic snapshot worker.
 *
 * UX:
 *  - 3 assist chips (30d / 90d / Tout) → switch the observation window.
 *  - Per-row: app icon + label + permission (short label) + GAINED/LOST badge
 *    with arrow + relative timestamp.
 *  - Tap row → deep-link straight to Android Settings → App → Permissions for
 *    that app (one-tap path to re-grant a revoked permission).
 *  - Primary "Capturer maintenant" button inside the status card.
 *
 * @param onAppClick reserved for the rare case the caller wants to override
 *   the default deep-link behaviour (e.g. external integrations). Currently
 *   unused by AppRoot — the in-screen handler invokes the VM directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDriftScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onAppClick: (String) -> Unit,
    viewModel: PermissionDriftViewModel = hiltViewModel(),
) {
    val drifts by viewModel.drifts.collectAsStateWithLifecycle()
    val window by viewModel.window.collectAsStateWithLifecycle()
    val isCapturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PermissionDriftViewModel.Event.CaptureDone -> {
                    val msg = when {
                        event.drifts > 0 ->
                            context.getString(R.string.drift_capture_changes, event.drifts)
                        event.baselines > 0 ->
                            context.getString(R.string.drift_capture_baseline, event.baselines)
                        else ->
                            context.getString(R.string.drift_capture_no_change)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is PermissionDriftViewModel.Event.LaunchIntentChain ->
                    if (!launchIntentChain(context, event.intents)) {
                        snackbarHostState.showSnackbar(context.getString(R.string.error_no_handler))
                    }
                is PermissionDriftViewModel.Event.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                title = { BrandedTitle(stringResource(R.string.screen_permission_drift_title)) },
                // v0.2.0 — refresh button moved OUT of TopAppBar into the
                // status card as a labelled Button. Icon-only in the bar was
                // unclear ("le bouton refresh me dit pareil…") so the
                // primary action now sits with the status info.
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // v0.2.0 — Always-visible status card (replaces the icon-only
            // refresh button in TopAppBar). It carries the primary "Capturer
            // maintenant" CTA + the live monitoring stats (perms/apps/last
            // capture) so the user always knows both what's tracked and how
            // to trigger a manual capture.
            MonitoringStatusCard(
                stats       = stats,
                isCapturing = isCapturing,
                onCapture   = { viewModel.captureNow() },
            )
            WindowChips(
                selected = window,
                onSelect = { viewModel.setWindow(it) },
            )
            HorizontalDivider()
            if (drifts.isEmpty()) {
                EmptyState(
                    icon  = Icons.Outlined.History,
                    title = stringResource(
                        if (stats.isEmpty) R.string.drift_empty_title
                        else R.string.drift_empty_title_after_capture,
                    ),
                    body  = stringResource(
                        if (stats.isEmpty) R.string.drift_empty_body
                        else R.string.drift_empty_body_after_capture,
                    ),
                )
            } else {
                DriftFeed(
                    drifts     = drifts,
                    // Tap deep-links straight to Android Settings → App →
                    // Permissions for the app. Single tap re-grant path.
                    onAppClick = { pkg -> viewModel.openPermissionsForApp(pkg) },
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }
    }

}

@Composable
private fun MonitoringStatusCard(
    stats: SnapshotStats,
    isCapturing: Boolean,
    onCapture: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    val lastLabel = remember(stats.lastCapturedAt) {
        stats.lastCapturedAt?.let { dateFormat.format(Date(it)) } ?: ""
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint               = if (stats.isEmpty) MaterialTheme.colorScheme.onSurfaceVariant
                                         else MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = stringResource(
                            if (stats.isEmpty) R.string.drift_status_inactive
                            else R.string.drift_status_active,
                        ),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!stats.isEmpty) {
                        Text(
                            text  = stringResource(
                                R.string.drift_status_counts,
                                stats.distinctPermissions,
                                stats.distinctPackages,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (lastLabel.isNotEmpty()) {
                            Text(
                                text  = stringResource(R.string.drift_status_last_capture, lastLabel),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text  = stringResource(R.string.drift_status_inactive_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(10.dp))
            // Primary action — labelled Button instead of the previous icon-
            // only TopAppBar refresh, which users misread as a no-op when
            // it produced no visible change ("le bouton refresh me dit pareil
            // état déjà capturé"). The labelled button + result snackbar
            // makes the action obvious + the outcome explicit.
            Button(
                onClick  = onCapture,
                enabled  = !isCapturing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Icon(
                    imageVector        = Icons.Outlined.Refresh,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (stats.isEmpty) R.string.drift_action_capture_first
                        else R.string.drift_action_capture_now,
                    ),
                )
            }
        }
    }
}

@Composable
private fun WindowChips(
    selected: PermissionDriftViewModel.Window,
    onSelect: (PermissionDriftViewModel.Window) -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PermissionDriftViewModel.Window.entries.forEach { w ->
            val labelRes = when (w) {
                PermissionDriftViewModel.Window.DAYS_30 -> R.string.drift_window_30_days
                PermissionDriftViewModel.Window.DAYS_90 -> R.string.drift_window_90_days
                PermissionDriftViewModel.Window.ALL     -> R.string.drift_window_all
            }
            AssistChip(
                onClick = { onSelect(w) },
                label   = { Text(stringResource(labelRes)) },
                colors  = if (w == selected) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor     = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

@Composable
private fun DriftFeed(
    drifts: List<PermissionDrift>,
    onAppClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(items = drifts, key = { "${it.packageName}|${it.permission}|${it.whenMs}" }) { drift ->
            DriftRow(
                drift     = drift,
                dateLabel = dateFormat.format(Date(drift.whenMs)),
                onClick   = { onAppClick(drift.packageName) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun DriftRow(
    drift: PermissionDrift,
    dateLabel: String,
    onClick: () -> Unit,
) {
    val gained = drift.change == PermissionDrift.Change.GAINED
    val accent = if (gained) BrandDanger else MaterialTheme.colorScheme.primary
    val badgeRes = if (gained) R.string.drift_badge_gained else R.string.drift_badge_lost
    val badgeIcon = if (gained) Icons.Outlined.NorthEast else Icons.Outlined.SouthWest

    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = drift.packageName, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = drift.appLabel ?: drift.packageName,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text     = shortPermissionLabel(drift.permission),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text  = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = accent.copy(alpha = 0.14f),
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier            = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment   = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector        = badgeIcon,
                    contentDescription = null,
                    tint               = accent,
                    modifier           = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text       = stringResource(badgeRes),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = accent,
                )
            }
        }
    }
}

/**
 * Strips the `android.permission.` prefix for visual density. Falls back to the
 * full string if the prefix is absent (vendor / custom permission).
 */
private fun shortPermissionLabel(fullName: String): String =
    fullName.removePrefix("android.permission.")

/**
 * Tries each [intents] in order, calling [android.content.Context.startActivity]
 * on it. Returns true as soon as one launches successfully. Catches only
 * [android.content.ActivityNotFoundException] and [SecurityException] —
 * anything else propagates (programmer error).
 *
 * Used by the drift-row tap path: first tries the direct
 * `ACTION_MANAGE_APP_PERMISSIONS` deep-link, then falls back to the App-info
 * Settings page. The Android-11+ package-visibility quirk (where
 * `resolveActivity` returns null even when a handler exists) cannot be
 * worked around by probing — the only honest test is to actually attempt
 * the launch and catch.
 */
private fun launchIntentChain(
    context: android.content.Context,
    intents: List<Intent>,
): Boolean {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: android.content.ActivityNotFoundException) {
            timber.log.Timber.w(e, "PermissionDrift: no handler for %s, trying next", intent.action)
        } catch (e: SecurityException) {
            timber.log.Timber.w(e, "PermissionDrift: security denial for %s, trying next", intent.action)
        }
    }
    timber.log.Timber.e("PermissionDrift: NO handler available across %d candidates", intents.size)
    return false
}
