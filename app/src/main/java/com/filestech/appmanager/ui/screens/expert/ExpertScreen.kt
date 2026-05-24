package com.filestech.appmanager.ui.screens.expert

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.ExpertReport
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.state.ErrorState
import com.filestech.appmanager.ui.components.state.LoadingState
import com.filestech.appmanager.ui.components.settings.SectionHeader
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.theme.BrandDanger
import java.text.DateFormat
import java.util.Date

/**
 * Expert Mode screen — surfaces the low-level Android components of an
 * installed app: identity, SDK envelope, ABI, APK paths, signature,
 * declared components (activities / services / receivers / providers with
 * the `exported` flag), permissions, app-ops best-effort.
 *
 * Audience: advanced users / developers / support technicians. The screen
 * deliberately uses monospace font for technical fields (paths, signatures,
 * component class names) and a clear danger color for exported components
 * without a permission gate — a real attack surface signal.
 *
 * Some advanced actions noted in the user's spec (component enable/disable,
 * app-op write) require root / ADB / Shizuku — out of scope for this F-Droid
 * release. The screen explains this in an info banner so the user understands
 * the "read-only" nature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: ExpertViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(packageName) {
        viewModel.load(packageName)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExpertViewModel.Event.ShowError -> snackbarHostState.showSnackbar(event.message)
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
                title = { BrandedTitle(stringResource(R.string.screen_expert_title)) },
            )
        },
    ) { innerPadding ->
        ExpertBody(
            innerPadding = innerPadding,
            state        = state,
            onRetry      = { viewModel.load(packageName) },
        )
    }
}

@Composable
private fun ExpertBody(
    innerPadding: PaddingValues,
    state: ExpertViewModel.UiState,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        when (val r = state.report) {
            Outcome.Loading    -> LoadingState()
            is Outcome.Failure -> ErrorState(message = r.error.toString(), onRetry = onRetry)
            is Outcome.Success -> ExpertReportContent(report = r.value)
        }
    }
}

@Composable
private fun ExpertReportContent(report: ExpertReport) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Info banner — sets expectations: read-only inspector, no root/Shizuku.
        ExpertInfoBanner()

        SectionHeader(stringResource(R.string.expert_section_identity))
        IdentityCard(report.identity)

        SectionHeader(stringResource(R.string.expert_section_sdk))
        SdkCard(report.sdkInfo)

        SectionHeader(stringResource(R.string.expert_section_native))
        NativeCard(report.nativeInfo)

        SectionHeader(stringResource(R.string.expert_section_apk_paths))
        ApkPathsCard(report.apkPaths)

        SectionHeader(stringResource(R.string.expert_section_signature))
        SignatureCard(report.signature)

        SectionHeader(
            stringResource(
                R.string.expert_section_components_count,
                report.components.totalCount,
                report.components.exportedCount,
            ),
        )
        ComponentsCard(report.components)

        SectionHeader(
            stringResource(
                R.string.expert_section_permissions_count,
                report.permissions.declaredCount,
                report.permissions.grantedCount,
            ),
        )
        PermissionsCard(report.permissions)

        SectionHeader(stringResource(R.string.expert_section_app_ops))
        AppOpsCard(report.appOps)
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun ExpertInfoBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors   = CardDefaults.cardColors(
            containerColor = BrandBlue.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector        = Icons.Outlined.Info,
                contentDescription = null,
                tint               = BrandBlue,
            )
            Text(
                text  = stringResource(R.string.expert_info_banner_body),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun IdentityCard(identity: ExpertReport.AppIdentity) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    SectionCard(icon = Icons.Outlined.Apps) {
        KvRow(stringResource(R.string.expert_field_label),              identity.label)
        KvRow(stringResource(R.string.expert_field_package),            identity.packageName, mono = true)
        KvRow(
            stringResource(R.string.expert_field_version),
            stringResource(R.string.app_detail_version, identity.versionName, identity.versionCode),
        )
        KvRow(stringResource(R.string.expert_field_uid),                identity.uid.toString())
        KvRow(
            stringResource(R.string.expert_field_installer),
            identity.installerPackage ?: stringResource(R.string.installer_sideload),
            mono = identity.installerPackage != null,
        )
        KvRow(stringResource(R.string.expert_field_first_install),      dateFormat.format(Date(identity.firstInstallTime)))
        KvRow(stringResource(R.string.expert_field_last_update),        dateFormat.format(Date(identity.lastUpdateTime)))
        KvRow(
            stringResource(R.string.expert_field_app_type),
            if (identity.isSystemApp) stringResource(R.string.expert_value_system_app)
            else stringResource(R.string.expert_value_user_app),
        )
        KvRow(
            stringResource(R.string.expert_field_state),
            if (identity.isEnabled) stringResource(R.string.expert_value_enabled)
            else stringResource(R.string.expert_value_disabled),
        )
    }
}

@Composable
private fun SdkCard(sdk: ExpertReport.SdkInfo) {
    SectionCard(icon = Icons.Outlined.Memory) {
        KvRow(
            stringResource(R.string.expert_field_min_sdk),
            sdk.minSdk?.toString() ?: stringResource(R.string.expert_value_unavailable),
        )
        KvRow(stringResource(R.string.expert_field_target_sdk), sdk.targetSdk.toString())
        KvRow(
            stringResource(R.string.expert_field_compile_sdk),
            sdk.compileSdk?.toString() ?: stringResource(R.string.expert_value_unavailable),
        )
    }
}

@Composable
private fun NativeCard(native: ExpertReport.NativeInfo) {
    SectionCard(icon = Icons.Outlined.Memory) {
        KvRow(
            stringResource(R.string.expert_field_primary_abi),
            native.primaryAbi ?: stringResource(R.string.expert_value_no_native),
            mono = native.primaryAbi != null,
        )
        KvRow(
            stringResource(R.string.expert_field_native_lib_dir),
            native.nativeLibraryDir ?: stringResource(R.string.expert_value_unavailable),
            mono = native.nativeLibraryDir != null,
        )
    }
}

@Composable
private fun ApkPathsCard(paths: ExpertReport.ApkPaths) {
    SectionCard(icon = Icons.Outlined.Folder) {
        KvRow(
            stringResource(R.string.expert_field_apk_base),
            paths.base ?: stringResource(R.string.expert_value_unavailable),
            mono = paths.base != null,
        )
        if (paths.publicSourceDir != null && paths.publicSourceDir != paths.base) {
            KvRow(
                stringResource(R.string.expert_field_apk_public),
                paths.publicSourceDir,
                mono = true,
            )
        }
        if (paths.splits.isEmpty()) {
            KvRow(stringResource(R.string.expert_field_apk_splits), stringResource(R.string.expert_value_no_splits))
        } else {
            Text(
                text       = stringResource(R.string.expert_field_apk_splits_n, paths.splits.size),
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            paths.splits.forEach { split ->
                Text(
                    text       = "• $split",
                    style      = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun SignatureCard(signature: ExpertReport.SignatureInfo) {
    SectionCard(icon = Icons.Outlined.Fingerprint) {
        KvRow(
            stringResource(R.string.expert_field_signers),
            signature.signerCount.toString(),
        )
        KvRow(
            stringResource(R.string.expert_field_signature_sha256),
            signature.sha256 ?: stringResource(R.string.expert_value_unavailable),
            mono = signature.sha256 != null,
        )
        if (signature.isDebugSigned) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint               = BrandDanger,
                )
                Text(
                    text       = stringResource(R.string.expert_signature_debug_warning),
                    style      = MaterialTheme.typography.bodySmall,
                    color      = BrandDanger,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ComponentsCard(components: ExpertReport.ComponentsInfo) {
    ComponentsSubsection(
        title    = stringResource(R.string.expert_components_activities, components.activities.size),
        entries  = components.activities,
        emptyMsg = stringResource(R.string.expert_components_none),
    )
    ComponentsSubsection(
        title    = stringResource(R.string.expert_components_services, components.services.size),
        entries  = components.services,
        emptyMsg = stringResource(R.string.expert_components_none),
    )
    ComponentsSubsection(
        title    = stringResource(R.string.expert_components_receivers, components.receivers.size),
        entries  = components.receivers,
        emptyMsg = stringResource(R.string.expert_components_none),
    )
    ProvidersSubsection(
        title    = stringResource(R.string.expert_components_providers, components.providers.size),
        entries  = components.providers,
        emptyMsg = stringResource(R.string.expert_components_none),
    )
}

@Composable
private fun ComponentsSubsection(
    title: String,
    entries: List<ExpertReport.ComponentEntry>,
    emptyMsg: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (entries.isEmpty()) {
                Text(
                    text     = emptyMsg,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                entries.forEach { entry ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    ComponentRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(entry: ExpertReport.ComponentEntry) {
    val unprotectedExported = entry.exported && entry.permission.isNullOrBlank()
    Column {
        Text(
            text       = entry.className,
            style      = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
        Row(
            modifier              = Modifier.padding(top = 2.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FlagChip(
                label   = stringResource(if (entry.exported) R.string.expert_chip_exported else R.string.expert_chip_internal),
                tint    = if (unprotectedExported) BrandDanger else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!entry.enabled) {
                FlagChip(label = stringResource(R.string.expert_chip_disabled), tint = BrandDanger)
            }
            if (!entry.permission.isNullOrBlank()) {
                FlagChip(label = stringResource(R.string.expert_chip_gated), tint = BrandBlue)
            }
        }
        if (!entry.permission.isNullOrBlank()) {
            Text(
                text       = entry.permission,
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ProvidersSubsection(
    title: String,
    entries: List<ExpertReport.ProviderEntry>,
    emptyMsg: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (entries.isEmpty()) {
                Text(
                    text     = emptyMsg,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                entries.forEach { entry ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    ProviderRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(entry: ExpertReport.ProviderEntry) {
    val unprotectedExported = entry.exported &&
        entry.readPermission.isNullOrBlank() &&
        entry.writePermission.isNullOrBlank()
    Column {
        Text(
            text       = entry.className,
            style      = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
        if (!entry.authority.isNullOrBlank()) {
            Text(
                text       = stringResource(R.string.expert_provider_authority, entry.authority),
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            modifier              = Modifier.padding(top = 2.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FlagChip(
                label = stringResource(if (entry.exported) R.string.expert_chip_exported else R.string.expert_chip_internal),
                tint  = if (unprotectedExported) BrandDanger else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!entry.enabled) {
                FlagChip(label = stringResource(R.string.expert_chip_disabled), tint = BrandDanger)
            }
            if (entry.grantUriPermissions) {
                FlagChip(label = stringResource(R.string.expert_chip_grant_uri), tint = BrandBlue)
            }
        }
        if (!entry.readPermission.isNullOrBlank()) {
            Text(
                text       = stringResource(R.string.expert_provider_read_perm, entry.readPermission),
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.padding(top = 2.dp),
            )
        }
        if (!entry.writePermission.isNullOrBlank()) {
            Text(
                text       = stringResource(R.string.expert_provider_write_perm, entry.writePermission),
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PermissionsCard(perms: ExpertReport.ExpertPermissions) {
    SectionCard(icon = Icons.Outlined.Security) {
        if (perms.declared.isEmpty()) {
            Text(
                text  = stringResource(R.string.expert_permissions_none_declared),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            perms.declared.forEach { perm ->
                ExpertPermissionRow(perm)
            }
        }
    }
}

@Composable
private fun ExpertPermissionRow(perm: ExpertReport.DeclaredPermission) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = if (perm.granted) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
            contentDescription = null,
            tint               = when {
                perm.granted && perm.isDangerous     -> BrandDanger
                perm.granted                         -> BrandBlue
                else                                 -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = perm.name,
                style      = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            if (perm.isDangerous) {
                Text(
                    text  = stringResource(R.string.expert_permission_dangerous),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandDanger,
                )
            }
        }
    }
}

@Composable
private fun AppOpsCard(snapshot: ExpertReport.AppOpsSnapshot) {
    SectionCard(icon = Icons.Outlined.Security) {
        Text(
            text     = stringResource(R.string.expert_app_ops_intro),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (!snapshot.isFullyAccessible) {
            Row(
                verticalAlignment     = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Info,
                    contentDescription = null,
                    tint               = BrandBlue,
                )
                Text(
                    text  = stringResource(R.string.expert_app_ops_limited),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        snapshot.entries.forEach { entry ->
            KvRow(label = entry.op, value = entry.modeLabel, mono = true)
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable atoms
// ---------------------------------------------------------------------------

@Composable
private fun KvRow(label: String, value: String, mono: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun FlagChip(label: String, tint: androidx.compose.ui.graphics.Color) {
    Surface(
        color    = tint.copy(alpha = 0.12f),
        shape    = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            color    = tint,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = BrandBlue,
            )
            Column(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}
