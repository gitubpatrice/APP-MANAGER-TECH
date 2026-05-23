package com.filestech.appmanager.ui.screens.appdetail

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppDetail
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.PrivacyScore
import com.filestech.appmanager.domain.model.TrackerReport
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.PrivacyDataPanel
import com.filestech.appmanager.ui.components.dialogs.ConfirmDialog
import com.filestech.appmanager.ui.components.dialogs.DestructiveDialog
import com.filestech.appmanager.ui.components.dialogs.UninstallChoiceDialog
import com.filestech.appmanager.ui.components.settings.SectionHeader
import com.filestech.appmanager.ui.components.state.ErrorState
import com.filestech.appmanager.ui.components.state.LoadingState
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.theme.BrandDanger
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DateFormat
import java.util.Date

/**
 * App detail screen — header, storage breakdown, actions, permissions,
 * install info.
 *
 * Destructive actions (Uninstall, Force stop) use [DestructiveDialog] (red);
 * non-destructive ones (Clear cache, Open settings) use [ConfirmDialog] (blue).
 * Brand colour discipline is enforced at the call site, NOT via colorScheme.error,
 * to stay consistent under Material You and dark theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var confirmDialog by rememberSaveable { mutableStateOf<ConfirmIntent?>(null) }

    LaunchedEffect(packageName) {
        viewModel.load(packageName)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AppDetailViewModel.Event.LaunchIntent -> launchIntent(context, event.intent)
                is AppDetailViewModel.Event.ActionDone   ->
                    snackbarHostState.showSnackbar(event.message)
                is AppDetailViewModel.Event.ShowError    ->
                    snackbarHostState.showSnackbar(event.message)
                is AppDetailViewModel.Event.MovedToTrash ->
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.trash_moved_snackbar, event.label),
                    )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar       = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                title          = {
                    Text(
                        text     = (state.detailOutcome as? Outcome.Success)
                            ?.value?.info?.label
                            ?: stringResource(R.string.screen_app_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions        = {
                    IconButton(onClick = viewModel::openAppSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.app_detail_action_open_settings),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AppDetailBody(
            state         = state,
            innerPadding  = innerPadding,
            onRetry       = { viewModel.load(packageName) },
            onUninstall   = {
                confirmDialog = ConfirmIntent.Uninstall(
                    label = (state.detailOutcome as? Outcome.Success)?.value?.info?.label ?: packageName,
                )
            },
            onClearCache  = {
                confirmDialog = ConfirmIntent.ClearCache(
                    label = (state.detailOutcome as? Outcome.Success)?.value?.info?.label ?: packageName,
                )
            },
            onForceStop   = {
                confirmDialog = ConfirmIntent.ForceStop(
                    label = (state.detailOutcome as? Outcome.Success)?.value?.info?.label ?: packageName,
                )
            },
            onToggleEnabled = { newEnabled ->
                confirmDialog = if (newEnabled) ConfirmIntent.Enable else ConfirmIntent.Disable
            },
            onToggleIgnore  = viewModel::toggleIgnore,
        )
    }

    when (val d = confirmDialog) {
        is ConfirmIntent.Uninstall -> UninstallChoiceDialog(
            label          = d.label,
            onCancel       = { confirmDialog = null },
            onMoveToTrash  = {
                viewModel.moveToTrash()
                confirmDialog = null
            },
            onUninstallNow = {
                viewModel.uninstall()
                confirmDialog = null
            },
        )
        is ConfirmIntent.ClearCache -> ConfirmDialog(
            title     = stringResource(R.string.dialog_clear_cache_title, d.label),
            body      = stringResource(R.string.dialog_clear_cache_body),
            onConfirm = {
                viewModel.clearCache()
                confirmDialog = null
            },
            onDismiss = { confirmDialog = null },
        )
        is ConfirmIntent.ForceStop -> DestructiveDialog(
            title     = stringResource(R.string.dialog_force_stop_title, d.label),
            body      = stringResource(R.string.dialog_force_stop_body),
            onConfirm = {
                viewModel.forceStop()
                confirmDialog = null
            },
            onDismiss = { confirmDialog = null },
        )
        ConfirmIntent.Disable -> DestructiveDialog(
            title     = stringResource(R.string.dialog_disable_title, packageName),
            body      = stringResource(R.string.dialog_disable_body),
            onConfirm = {
                viewModel.disable()
                confirmDialog = null
            },
            onDismiss = { confirmDialog = null },
        )
        ConfirmIntent.Enable -> ConfirmDialog(
            title     = stringResource(R.string.dialog_enable_title, packageName),
            body      = stringResource(R.string.dialog_enable_body),
            onConfirm = {
                viewModel.enable()
                confirmDialog = null
            },
            onDismiss = { confirmDialog = null },
        )
        null -> Unit
    }
    // Hint kotlin scope is used (rememberCoroutineScope keeps for future Phase VI batch flows).
    @Suppress("UNUSED_EXPRESSION") scope
}

// ---------------------------------------------------------------------------
// Body
// ---------------------------------------------------------------------------

@Composable
private fun AppDetailBody(
    state: AppDetailViewModel.UiState,
    innerPadding: PaddingValues,
    onRetry: () -> Unit,
    onUninstall: () -> Unit,
    onClearCache: () -> Unit,
    onForceStop: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleIgnore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        when (val o = state.detailOutcome) {
            Outcome.Loading -> LoadingState()
            is Outcome.Failure -> ErrorState(
                message = o.error.toString(),
                onRetry = onRetry,
            )
            is Outcome.Success -> AppDetailContent(
                detail            = o.value,
                privacyScore      = state.privacyScore,
                trackerReport     = state.trackerReport,
                isIgnored         = state.isIgnored,
                onUninstall       = onUninstall,
                onClearCache      = onClearCache,
                onForceStop       = onForceStop,
                onToggleEnabled   = onToggleEnabled,
                onToggleIgnore    = onToggleIgnore,
            )
        }
    }
}

@Composable
private fun AppDetailContent(
    detail: AppDetail,
    privacyScore: PrivacyScore?,
    trackerReport: TrackerReport?,
    isIgnored: Boolean,
    onUninstall: () -> Unit,
    onClearCache: () -> Unit,
    onForceStop: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleIgnore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        HeaderCard(detail.info)
        // Phase X — PrivacyDataPanel right after the header so privacy info
        // is the FIRST thing users see (more important than storage breakdown).
        SectionHeader(stringResource(R.string.appdetail_privacy_panel_title))
        PrivacyDataPanel(
            privacyScore        = privacyScore,
            trackerReport       = trackerReport,
            grantedPermissions  = detail.grantedPermissions,
            requestedPermissions = detail.requestedPermissions,
        )
        SectionHeader(stringResource(R.string.app_detail_section_storage))
        StorageBreakdownCard(detail.info)
        SectionHeader(stringResource(R.string.app_detail_section_actions))
        ActionsCard(
            info            = detail.info,
            isIgnored       = isIgnored,
            onUninstall     = onUninstall,
            onClearCache    = onClearCache,
            onForceStop     = onForceStop,
            onToggleEnabled = onToggleEnabled,
            onToggleIgnore  = onToggleIgnore,
        )
        SectionHeader(stringResource(R.string.app_detail_section_permissions))
        PermissionsCard(detail)
        SectionHeader(stringResource(R.string.app_detail_section_install_info))
        InstallInfoCard(detail.info)
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun HeaderCard(info: AppInfo) {
    SectionCard {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(packageName = info.packageName, size = 64.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = info.label,
                    style    = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text     = info.packageName,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text     = stringResource(R.string.app_detail_version, info.versionName, info.versionCode),
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StorageBreakdownCard(info: AppInfo) {
    val context = LocalContext.current
    val install = remember(info.installSizeBytes) { Formatter.formatShortFileSize(context, info.installSizeBytes) }
    val data    = remember(info.dataSizeBytes)    { Formatter.formatShortFileSize(context, info.dataSizeBytes) }
    val cache   = remember(info.cacheSizeBytes)   { Formatter.formatShortFileSize(context, info.cacheSizeBytes) }
    val total   = remember(info.totalSizeBytes)   { Formatter.formatShortFileSize(context, info.totalSizeBytes) }

    SectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            SizeRow(stringResource(R.string.app_detail_install_size), install, BrandBlue)
            SizeRow(stringResource(R.string.app_detail_data_size),    data,    MaterialTheme.colorScheme.secondary)
            SizeRow(stringResource(R.string.app_detail_cache_size),   cache,   MaterialTheme.colorScheme.tertiary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SizeRow(
                label = stringResource(R.string.app_detail_total_size),
                value = total,
                color = MaterialTheme.colorScheme.primary,
                bold  = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            StackedBar(info)
        }
    }
}

@Composable
private fun StackedBar(info: AppInfo) {
    val total = info.totalSizeBytes.coerceAtLeast(1L) // avoid div by 0
    val installWeight = info.installSizeBytes.toFloat() / total.toFloat()
    val dataWeight    = info.dataSizeBytes.toFloat()    / total.toFloat()
    val cacheWeight   = info.cacheSizeBytes.toFloat()   / total.toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        if (installWeight > 0f) BarSegment(BrandBlue, installWeight)
        if (dataWeight > 0f)    BarSegment(MaterialTheme.colorScheme.secondary, dataWeight)
        if (cacheWeight > 0f)   BarSegment(MaterialTheme.colorScheme.tertiary, cacheWeight)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarSegment(color: Color, weight: Float) {
    Surface(
        color    = color,
        modifier = Modifier
            .weight(weight)
            .height(12.dp),
        shape    = RoundedCornerShape(2.dp),
    ) {}
}

@Composable
private fun SizeRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(12.dp),
            color    = color,
            shape    = RoundedCornerShape(50),
        ) {}
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ActionsCard(
    info: AppInfo,
    isIgnored: Boolean,
    onUninstall: () -> Unit,
    onClearCache: () -> Unit,
    onForceStop: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleIgnore: () -> Unit,
) {
    SectionCard {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Uninstall — destructive (BrandDanger) — only enabled if uninstallable.
            OutlinedButton(
                onClick  = onUninstall,
                enabled  = info.isUninstallable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = BrandDanger)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.app_detail_action_uninstall), color = BrandDanger)
            }
            FilledTonalButton(
                onClick  = onClearCache,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.app_detail_action_clear_cache))
            }
            FilledTonalButton(
                onClick  = onForceStop,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.app_detail_action_force_stop))
            }
            OutlinedButton(
                onClick  = { onToggleEnabled(!info.isEnabled) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (info.isEnabled) stringResource(R.string.app_detail_action_disable)
                    else stringResource(R.string.app_detail_action_enable),
                )
            }
            // Phase X — Ignore / Unignore (whitelist this app from batch actions and notifications)
            OutlinedButton(
                onClick  = onToggleIgnore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector        = if (isIgnored) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isIgnored) stringResource(R.string.appdetail_unignore_action)
                    else stringResource(R.string.appdetail_ignore_action),
                )
            }
        }
    }
}

@Composable
private fun PermissionsCard(detail: AppDetail) {
    SectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = stringResource(R.string.app_detail_perms_granted, detail.grantedPermissions.size),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier          = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Block,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text  = stringResource(R.string.app_detail_perms_requested, detail.requestedPermissions.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (detail.requestedPermissions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(
                    modifier = Modifier.height(180.dp),
                ) {
                    items(detail.requestedPermissions) { perm ->
                        PermissionRow(
                            permission = perm,
                            granted    = perm in detail.grantedPermissions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(permission: String, granted: Boolean) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
            contentDescription = null,
            tint               = if (granted) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text     = permission.substringAfterLast('.'),
            style    = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InstallInfoCard(info: AppInfo) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    SectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(
                label = stringResource(R.string.app_detail_installer),
                value = installerLabel(info.installerPackage),
            )
            InfoRow(
                label = stringResource(R.string.app_detail_installed),
                value = dateFormat.format(Date(info.firstInstallTime)),
            )
            InfoRow(
                label = stringResource(R.string.app_detail_last_updated),
                value = dateFormat.format(Date(info.lastUpdateTime)),
            )
            InfoRow(
                label = stringResource(R.string.app_detail_last_used),
                value = if (info.lastUsedTime > 0L) dateFormat.format(Date(info.lastUsedTime))
                        else stringResource(R.string.app_detail_never_used),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text     = value,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun installerLabel(installer: String?): String = when (installer) {
    null                              -> stringResource(R.string.installer_sideload)
    "com.android.vending"             -> stringResource(R.string.installer_play_store)
    "org.fdroid.fdroid",
    "org.fdroid.fdroid.privileged"    -> stringResource(R.string.installer_f_droid)
    "com.aurora.store",
    "com.aurora.services"             -> stringResource(R.string.installer_aurora)
    else                              -> installer
}

// ---------------------------------------------------------------------------
// SectionCard
// ---------------------------------------------------------------------------

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// Confirm-intent state
// ---------------------------------------------------------------------------

private sealed interface ConfirmIntent : java.io.Serializable {
    data class Uninstall(val label: String) : ConfirmIntent
    data class ClearCache(val label: String) : ConfirmIntent
    data class ForceStop(val label: String) : ConfirmIntent
    data object Disable : ConfirmIntent
    data object Enable : ConfirmIntent
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun launchIntent(context: android.content.Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Timber.w(it, "Failed to launch intent %s", intent) }
}
