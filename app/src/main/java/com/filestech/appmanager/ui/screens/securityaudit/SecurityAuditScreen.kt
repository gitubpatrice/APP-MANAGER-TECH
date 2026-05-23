package com.filestech.appmanager.ui.screens.securityaudit

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.settings.SectionHeader
import timber.log.Timber

/**
 * Security audit screen — surfaces apps with elevated OS privileges that are
 * common abuse vectors: device administrators (can block their own uninstall)
 * and accessibility services (can read screen, generate inputs).
 *
 * v0.1.3 actions:
 *  - Tap row → AppDetail of that package (sizes, permissions, uninstall).
 *  - "Disable" button per section → opens OS Settings (Android does not let
 *    third-party apps revoke DeviceAdmin / Accessibility programmatically).
 *  - "Système" badge for well-known publisher apps (Samsung Knox, Google
 *    Play Services, etc.) so the user knows which are legitimate by design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(
    onBack: () -> Unit,
    onAppClick: (packageName: String) -> Unit,
    viewModel: SecurityAuditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SecurityAuditViewModel.Event.LaunchIntent -> launchIntent(context, event.intent)
                is SecurityAuditViewModel.Event.RefreshDone ->
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.security_audit_refresh_done,
                            event.adminCount,
                            event.a11yCount,
                        ),
                    )
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
                title = { BrandedTitle(stringResource(R.string.screen_security_audit_title)) },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SecurityAuditBody(
            innerPadding              = innerPadding,
            isLoading                 = state.isLoading,
            error                     = state.error,
            deviceAdmins              = state.deviceAdmins,
            accessibilityServices     = state.accessibilityServices,
            onAppClick                = onAppClick,
            onUninstallClick          = viewModel::uninstall,
            onDisableDeviceAdminClick = viewModel::openDeviceAdminSettings,
            onDisableA11yClick        = viewModel::openAccessibilitySettings,
        )
    }
}

@Composable
private fun SecurityAuditBody(
    innerPadding: PaddingValues,
    isLoading: Boolean,
    error: String?,
    deviceAdmins: List<String>,
    accessibilityServices: List<String>,
    onAppClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
    onDisableDeviceAdminClick: () -> Unit,
    onDisableA11yClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(32.dp).align(Alignment.TopCenter),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            if (error != null) {
                Text(
                    text     = error,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                text     = stringResource(R.string.security_audit_subtitle),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            Text(
                text     = stringResource(R.string.security_audit_tap_hint),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            )

            SectionHeader(stringResource(R.string.security_audit_section_device_admin))
            AuditCard(
                description       = stringResource(R.string.security_audit_device_admin_desc),
                packages          = deviceAdmins,
                onAppClick        = onAppClick,
                onUninstallClick  = onUninstallClick,
                disableActionLabel = stringResource(R.string.security_audit_action_disable_admin),
                onDisableClick    = onDisableDeviceAdminClick,
            )

            SectionHeader(stringResource(R.string.security_audit_section_accessibility))
            AuditCard(
                description       = stringResource(R.string.security_audit_accessibility_desc),
                packages          = accessibilityServices,
                onAppClick        = onAppClick,
                onUninstallClick  = onUninstallClick,
                disableActionLabel = stringResource(R.string.security_audit_action_disable_a11y),
                onDisableClick    = onDisableA11yClick,
            )
        }
    }
}

@Composable
private fun AuditCard(
    description: String,
    packages: List<String>,
    onAppClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
    disableActionLabel: String,
    onDisableClick: () -> Unit,
) {
    ElevatedCard(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (packages.isEmpty()) {
                Text(
                    text     = stringResource(R.string.security_audit_empty),
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                packages.forEach { pkg ->
                    PackageRow(
                        packageName  = pkg,
                        onViewDetails = { onAppClick(pkg) },
                        onUninstall   = { onUninstallClick(pkg) },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick  = onDisableClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(disableActionLabel)
                }
            }
        }
    }
}

@Composable
private fun PackageRow(
    packageName: String,
    onViewDetails: () -> Unit,
    onUninstall: () -> Unit,
) {
    val isSystem = packageName in SecurityAuditViewModel.SYSTEM_PUBLISHERS
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = packageName, size = 32.dp)
        Text(
            text       = packageName,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            modifier   = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
        if (isSystem) {
            Spacer(modifier = Modifier.width(8.dp))
            SystemBadge()
        }
        // v0.1.3 — per-row overflow menu instead of an implicit row-tap → AppDetail
        // (the tap was confusing — user feedback "ça envoie vers device services").
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector        = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                )
            }
            DropdownMenu(
                expanded         = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.action_view_details)) },
                    onClick = {
                        menuOpen = false
                        onViewDetails()
                    },
                )
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.action_uninstall)) },
                    onClick = {
                        menuOpen = false
                        onUninstall()
                    },
                )
            }
        }
    }
}

@Composable
private fun SystemBadge() {
    Surface(
        shape        = RoundedCornerShape(50),
        color        = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.secondary,
    ) {
        Text(
            text     = stringResource(R.string.security_audit_badge_system),
            style    = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun launchIntent(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Timber.w(it, "Failed to launch security audit intent %s", intent) }
}
