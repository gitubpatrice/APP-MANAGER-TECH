package com.filestech.appmanager.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.AppSortOrder
import com.filestech.appmanager.domain.model.AppTag
import com.filestech.appmanager.domain.model.ThemeMode
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.dialogs.RadioPickerDialog
import com.filestech.appmanager.ui.components.dialogs.appTagLabelRes
import com.filestech.appmanager.ui.components.settings.NavigationRow
import com.filestech.appmanager.ui.components.settings.SectionHeader
import com.filestech.appmanager.ui.components.settings.ToggleRow
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * Settings screen — Appearance / Scanner / Privacy / Advanced / About sections.
 *
 * Each ToggleRow is bound directly to its corresponding SettingsViewModel
 * setter; writes are fire-and-forget on viewModelScope. The DataStore
 * .edit { } transaction guarantees atomicity, so rapid toggling cannot
 * leave the prefs in a half-written state.
 *
 * Phase VIII.B: added "Advanced" section with NavigationRows to the four
 * dedicated screens (Cleaner / IgnoreList / Export / SecurityAudit).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenCleaner: () -> Unit = {},
    onOpenIgnoreList: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenSecurityAudit: () -> Unit = {},
    onOpenSmartCleaner: () -> Unit = {},
    onOpenTrackers: () -> Unit = {},
    onOpenTransparency: () -> Unit = {},
    onOpenRarelyUsed: () -> Unit = {},
    onOpenZombies: () -> Unit = {},
    onOpenPermissionFilter: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    // v0.2.1 audit H3 fix — v0.2.0 added these tools to the Outils grid
    // (ToolsScreen) and to the bottom nav, but the SettingsScreen tools
    // section never got the NavigationRow shortcuts. Adding them here +
    // wiring in AppRoot makes both paths reachable from Settings too.
    onOpenPermissionDrift: () -> Unit = {},
    onOpenQuarantine: () -> Unit = {},
    onOpenProtectedApps: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var themeDialog by rememberSaveable { mutableStateOf(false) }
    var sortDialog by rememberSaveable { mutableStateOf(false) }
    var retentionDialog by rememberSaveable { mutableStateOf(false) }
    var lifecycleRetentionDialog by rememberSaveable { mutableStateOf(false) }

    // SAF folder picker for the HARD-mode APK backup destination.
    // OPEN_DOCUMENT_TREE returns a content:// tree URI; we take a persistable
    // read+write grant so subsequent process restarts can still write into it
    // without re-prompting the user.
    val backupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
            viewModel.setQuarantineBackupTreeUri(uri.toString())
        }
    }

    Scaffold(
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
                title = { BrandedTitle(stringResource(R.string.screen_settings_title)) },
            )
        },
    ) { innerPadding ->
        SettingsBody(
            innerPadding         = innerPadding,
            settings             = settings,
            onThemeModeClick     = { themeDialog = true },
            onSortOrderClick     = { sortDialog = true },
            onDynamicColorChange = viewModel::setDynamicColor,
            onIncludeSystemChange = viewModel::setIncludeSystemApps,
            onAutoScanChange     = viewModel::setAutoScanOnLaunch,
            onFlagSecureChange   = viewModel::setFlagSecure,
            onConfirmDeleteChange = viewModel::setConfirmBeforeDelete,
            // v0.2.0 — Privacy monitor
            onPermissionDriftEnabledChange = viewModel::setPermissionDriftEnabled,
            onPermissionDriftRetentionClick = { retentionDialog = true },
            onPermissionDriftIncludeSystemChange = viewModel::setPermissionDriftIncludeSystemApps,
            onPermissionDriftNotifyChange = viewModel::setPermissionDriftNotify,
            // v0.2.0 — Quarantine
            onPickBackupFolderClick = { backupFolderLauncher.launch(null) },
            onQuarantineReminderChange = viewModel::setQuarantineRestoreReminderEnabled,
            // v0.3.0 — Lifecycle History
            onLifecycleEnabledChange       = viewModel::setLifecycleEnabled,
            onLifecycleRetentionClick      = { lifecycleRetentionDialog = true },
            onLifecyclePromptReasonChange  = viewModel::setLifecyclePromptReason,
            // v0.3.1 — Apps protégées entry
            onProtectedAppsClick           = onOpenProtectedApps,
            onAboutClick         = onOpenAbout,
            onCleanerClick       = onOpenCleaner,
            onIgnoreListClick    = onOpenIgnoreList,
            onExportClick        = onOpenExport,
            onSecurityAuditClick = onOpenSecurityAudit,
            onSmartCleanerClick  = onOpenSmartCleaner,
            onTrackersClick      = onOpenTrackers,
            onTransparencyClick  = onOpenTransparency,
            onRarelyUsedClick    = onOpenRarelyUsed,
            onZombiesClick       = onOpenZombies,
            onPermissionFilterClick = onOpenPermissionFilter,
            onTrashClick         = onOpenTrash,
            onPermissionDriftClick = onOpenPermissionDrift,
            onQuarantineClick    = onOpenQuarantine,
        )
    }

    if (themeDialog) {
        RadioPickerDialog(
            title    = stringResource(R.string.settings_theme_mode_title),
            options  = ThemeMode.entries,
            selected = settings.appearance.themeMode,
            labelOf  = { themeModeLabel(it) },
            onSelect = viewModel::setThemeMode,
            onDismiss = { themeDialog = false },
        )
    }

    if (sortDialog) {
        RadioPickerDialog(
            title    = stringResource(R.string.settings_default_sort_title),
            options  = AppSortOrder.entries,
            selected = settings.appearance.appSortOrder,
            labelOf  = { sortOrderLabel(it) },
            onSelect = viewModel::setDefaultSortOrder,
            onDismiss = { sortDialog = false },
        )
    }

    if (retentionDialog) {
        RadioPickerDialog(
            title    = stringResource(R.string.settings_retention_picker_title),
            options  = RetentionOption.entries.toList(),
            selected = RetentionOption.closestTo(settings.privacyMonitor.permissionDriftRetentionDays),
            labelOf  = { stringResource(it.labelRes) },
            onSelect = { viewModel.setPermissionDriftRetentionDays(it.days) },
            onDismiss = { retentionDialog = false },
        )
    }

    if (lifecycleRetentionDialog) {
        // v0.3.0 — lifecycle retention picker. Re-uses the existing
        // [RetentionOption] enum for parity with the drift tracker UX —
        // both features speak in the same 30/90/180/365 vocabulary so the
        // user doesn't have to learn a second mental model. The floor is
        // higher in storage (30) but DataStore coerces silently if the
        // saved value falls below.
        RadioPickerDialog(
            title    = stringResource(R.string.settings_retention_picker_title),
            options  = RetentionOption.entries.toList(),
            selected = RetentionOption.closestTo(settings.lifecycle.retentionDays),
            labelOf  = { stringResource(it.labelRes) },
            onSelect = { viewModel.setLifecycleRetentionDays(it.days) },
            onDismiss = { lifecycleRetentionDialog = false },
        )
    }
}

/**
 * Discrete retention choices exposed in the Settings UI. Backed by the integer
 * setting; selection on open is the closest exposed option (defensively
 * handles arbitrary persisted ints — e.g. if a future build exposes a slider
 * and downgrades to a discrete-only build).
 */
private enum class RetentionOption(val days: Int, val labelRes: Int) {
    DAYS_30(30, R.string.settings_retention_30_days),
    DAYS_90(90, R.string.settings_retention_90_days),
    DAYS_180(180, R.string.settings_retention_180_days),
    DAYS_365(365, R.string.settings_retention_365_days);

    companion object {
        fun closestTo(target: Int): RetentionOption =
            entries.minBy { kotlin.math.abs(it.days - target) }
    }
}

@Composable
private fun SettingsBody(
    innerPadding: PaddingValues,
    settings: com.filestech.appmanager.data.local.datastore.AppSettings,
    onThemeModeClick: () -> Unit,
    onSortOrderClick: () -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onIncludeSystemChange: (Boolean) -> Unit,
    onAutoScanChange: (Boolean) -> Unit,
    onFlagSecureChange: (Boolean) -> Unit,
    onConfirmDeleteChange: (Boolean) -> Unit,
    onPermissionDriftEnabledChange: (Boolean) -> Unit,
    onPermissionDriftRetentionClick: () -> Unit,
    onPermissionDriftIncludeSystemChange: (Boolean) -> Unit,
    onPermissionDriftNotifyChange: (Boolean) -> Unit,
    onPickBackupFolderClick: () -> Unit,
    onQuarantineReminderChange: (Boolean) -> Unit,
    onLifecycleEnabledChange: (Boolean) -> Unit,
    onLifecycleRetentionClick: () -> Unit,
    onLifecyclePromptReasonChange: (Boolean) -> Unit,
    onProtectedAppsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onCleanerClick: () -> Unit,
    onIgnoreListClick: () -> Unit,
    onExportClick: () -> Unit,
    onSecurityAuditClick: () -> Unit,
    onSmartCleanerClick: () -> Unit,
    onTrackersClick: () -> Unit,
    onTransparencyClick: () -> Unit,
    onRarelyUsedClick: () -> Unit,
    onZombiesClick: () -> Unit,
    onPermissionFilterClick: () -> Unit,
    onTrashClick: () -> Unit,
    onPermissionDriftClick: () -> Unit,
    onQuarantineClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Appearance
        SectionHeader(stringResource(R.string.settings_appearance_title))
        SettingsCard {
            NavigationRow(
                title       = stringResource(R.string.settings_theme_mode_title),
                onClick     = onThemeModeClick,
                currentValue = themeModeLabel(settings.appearance.themeMode),
            )
            ToggleRow(
                title          = stringResource(R.string.settings_dynamic_color_title),
                description    = stringResource(R.string.settings_dynamic_color_desc),
                checked        = settings.appearance.dynamicColor,
                onCheckedChange = onDynamicColorChange,
            )
            NavigationRow(
                title       = stringResource(R.string.settings_default_sort_title),
                onClick     = onSortOrderClick,
                currentValue = sortOrderLabel(settings.appearance.appSortOrder),
            )
        }

        // Scanner
        SectionHeader(stringResource(R.string.settings_scanner_title))
        SettingsCard {
            ToggleRow(
                title          = stringResource(R.string.settings_include_system_apps_title),
                description    = stringResource(R.string.settings_include_system_apps_desc),
                checked        = settings.scanner.includeSystemApps,
                onCheckedChange = onIncludeSystemChange,
            )
            ToggleRow(
                title          = stringResource(R.string.settings_auto_scan_title),
                description    = stringResource(R.string.settings_auto_scan_desc),
                checked        = settings.scanner.autoScanOnLaunch,
                onCheckedChange = onAutoScanChange,
            )
        }

        // Privacy
        SectionHeader(stringResource(R.string.settings_privacy_title))
        SettingsCard {
            ToggleRow(
                title          = stringResource(R.string.settings_flag_secure_title),
                description    = stringResource(R.string.settings_flag_secure_desc),
                checked        = settings.privacy.flagSecure,
                onCheckedChange = onFlagSecureChange,
            )
            ToggleRow(
                title          = stringResource(R.string.settings_confirm_delete_title),
                description    = stringResource(R.string.settings_confirm_delete_desc),
                checked        = settings.privacy.confirmBeforeDelete,
                onCheckedChange = onConfirmDeleteChange,
            )
        }

        // v0.2.0 — Privacy monitor (Permission Drift Tracker preferences)
        SectionHeader(stringResource(R.string.settings_section_privacy_monitor))
        SettingsCard {
            ToggleRow(
                title          = stringResource(R.string.settings_privacy_monitor_drift_title),
                description    = stringResource(R.string.settings_privacy_monitor_drift_desc),
                checked        = settings.privacyMonitor.permissionDriftEnabled,
                onCheckedChange = onPermissionDriftEnabledChange,
            )
            NavigationRow(
                title          = stringResource(R.string.settings_privacy_monitor_retention_title),
                currentValue   = stringResource(
                    R.string.settings_privacy_monitor_retention_desc,
                    settings.privacyMonitor.permissionDriftRetentionDays,
                ),
                onClick        = onPermissionDriftRetentionClick,
            )
            ToggleRow(
                title          = stringResource(R.string.settings_privacy_monitor_include_system_title),
                description    = stringResource(R.string.settings_privacy_monitor_include_system_desc),
                checked        = settings.privacyMonitor.permissionDriftIncludeSystemApps,
                onCheckedChange = onPermissionDriftIncludeSystemChange,
            )
            ToggleRow(
                title          = stringResource(R.string.settings_privacy_monitor_notify_title),
                description    = stringResource(R.string.settings_privacy_monitor_notify_desc),
                checked        = settings.privacyMonitor.permissionDriftNotify,
                onCheckedChange = onPermissionDriftNotifyChange,
            )
        }

        // v0.2.0 — Quarantine
        SectionHeader(stringResource(R.string.settings_section_quarantine))
        SettingsCard {
            val backupUri = settings.quarantine.backupTreeUri
            NavigationRow(
                title          = stringResource(R.string.settings_quarantine_backup_folder_title),
                description    = backupUri ?: stringResource(R.string.settings_quarantine_backup_folder_none),
                leadingIcon    = Icons.Outlined.Inventory2,
                currentValue   = stringResource(
                    if (backupUri == null) R.string.settings_quarantine_backup_folder_pick
                    else R.string.settings_quarantine_backup_folder_change,
                ),
                onClick        = onPickBackupFolderClick,
            )
            ToggleRow(
                title          = stringResource(R.string.settings_quarantine_reminder_title),
                description    = stringResource(R.string.settings_quarantine_reminder_desc),
                checked        = settings.quarantine.restoreReminderEnabled,
                onCheckedChange = onQuarantineReminderChange,
            )
        }

        // v0.3.1 — Safety Guardrails user-customisation
        SectionHeader(stringResource(R.string.settings_section_safety))
        SettingsCard {
            NavigationRow(
                title       = stringResource(R.string.settings_safety_protected_apps_title),
                description = stringResource(R.string.settings_safety_protected_apps_desc),
                leadingIcon = Icons.Outlined.Shield,
                onClick     = onProtectedAppsClick,
            )
        }

        // v0.3.0 — Lifecycle History
        SectionHeader(stringResource(R.string.settings_section_lifecycle))
        SettingsCard {
            ToggleRow(
                title          = stringResource(R.string.settings_lifecycle_enabled_title),
                description    = stringResource(R.string.settings_lifecycle_enabled_desc),
                checked        = settings.lifecycle.enabled,
                onCheckedChange = onLifecycleEnabledChange,
            )
            NavigationRow(
                title          = stringResource(R.string.settings_lifecycle_retention_title),
                currentValue   = stringResource(
                    R.string.settings_lifecycle_retention_desc,
                    settings.lifecycle.retentionDays,
                ),
                onClick        = onLifecycleRetentionClick,
            )
            ToggleRow(
                title          = stringResource(R.string.settings_lifecycle_prompt_reason_title),
                description    = stringResource(R.string.settings_lifecycle_prompt_reason_desc),
                checked        = settings.lifecycle.promptReason,
                onCheckedChange = onLifecyclePromptReasonChange,
            )
        }

        // v0.3.4 — Tag distribution stats. Hidden when no tag is assigned
        // (avoids surfacing an empty "0 / 0 / 0" card right after install);
        // appears as soon as the user tags their first app.
        TagStatsSection(appTags = settings.appTags)

        // Outils — Phase IX innovation screens (highlighted at the top of "tools").
        SectionHeader(stringResource(R.string.settings_tools_title))
        SettingsCard {
            NavigationRow(
                title       = stringResource(R.string.screen_smart_cleaner_title),
                leadingIcon = Icons.Outlined.AutoFixHigh,
                onClick     = onSmartCleanerClick,
            )
            NavigationRow(
                title       = stringResource(R.string.screen_trackers_title),
                leadingIcon = Icons.Outlined.VisibilityOff,
                onClick     = onTrackersClick,
            )
            NavigationRow(
                title       = stringResource(R.string.screen_security_audit_title),
                leadingIcon = Icons.Outlined.Security,
                onClick     = onSecurityAuditClick,
            )
            NavigationRow(
                title       = stringResource(R.string.screen_cleaner_title),
                leadingIcon = Icons.Outlined.CleaningServices,
                onClick     = onCleanerClick,
            )
            // Phase X — list-by-criterion screens (use cases already shipped, screens new).
            NavigationRow(
                title       = stringResource(R.string.settings_tools_rarely_used),
                description = stringResource(R.string.settings_tools_rarely_used_desc),
                leadingIcon = Icons.Outlined.AccessTime,
                onClick     = onRarelyUsedClick,
            )
            NavigationRow(
                title       = stringResource(R.string.settings_tools_zombies),
                description = stringResource(R.string.settings_tools_zombies_desc),
                leadingIcon = Icons.Outlined.SentimentSatisfied,
                onClick     = onZombiesClick,
            )
            NavigationRow(
                title       = stringResource(R.string.settings_tools_permission_filter),
                description = stringResource(R.string.settings_tools_permission_filter_desc),
                leadingIcon = Icons.Outlined.FilterAlt,
                onClick     = onPermissionFilterClick,
            )
            // v0.2.1 audit H3 fix — v0.2.0 features were missing from the
            // Settings → Outils section even though they had routes + cards
            // in the Outils tab. Adding NavigationRows here so users who
            // start from Settings can also reach them.
            NavigationRow(
                title       = stringResource(R.string.screen_permission_drift_title),
                leadingIcon = Icons.Outlined.History,
                onClick     = onPermissionDriftClick,
            )
            NavigationRow(
                title       = stringResource(R.string.screen_quarantine_title),
                leadingIcon = Icons.Outlined.Inventory2,
                onClick     = onQuarantineClick,
            )
            NavigationRow(
                title       = stringResource(R.string.screen_ignore_list_title),
                leadingIcon = Icons.Outlined.Block,
                onClick     = onIgnoreListClick,
            )
            NavigationRow(
                title       = stringResource(R.string.screen_export_title),
                leadingIcon = Icons.Outlined.FileDownload,
                onClick     = onExportClick,
            )
            NavigationRow(
                title       = stringResource(R.string.screen_transparency_title),
                leadingIcon = Icons.Outlined.VerifiedUser,
                onClick     = onTransparencyClick,
            )
            // Trash — always painted RED (destructive intent) per Patrice's brand discipline.
            NavigationRow(
                title           = stringResource(R.string.settings_tools_trash),
                description     = stringResource(R.string.settings_tools_trash_desc),
                leadingIcon     = Icons.Outlined.Delete,
                leadingIconTint = BrandDanger,
                titleColor      = BrandDanger,
                onClick         = onTrashClick,
            )
        }

        // About
        SectionHeader(stringResource(R.string.about_section_about))
        SettingsCard {
            NavigationRow(
                title       = stringResource(R.string.settings_about_title),
                description = stringResource(R.string.settings_about_desc),
                leadingIcon = Icons.Outlined.Info,
                onClick     = onAboutClick,
            )
        }
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT  -> R.string.settings_theme_light
        ThemeMode.DARK   -> R.string.settings_theme_dark
    }
)

@Composable
private fun sortOrderLabel(order: AppSortOrder): String = stringResource(
    when (order) {
        AppSortOrder.NAME_ASC          -> R.string.sort_name_asc
        AppSortOrder.NAME_DESC         -> R.string.sort_name_desc
        AppSortOrder.SIZE_DESC         -> R.string.sort_size_desc
        AppSortOrder.LAST_USED_DESC    -> R.string.sort_last_used_desc
        AppSortOrder.INSTALL_DATE_DESC -> R.string.sort_install_date_desc
    }
)

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    // v0.1.2 — ElevatedCard for the premium drop-shadow RFT-style look (was a
    // flat tonal Card that looked muddy under pink wallpapers with Material You).
    ElevatedCard(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column { content() }
    }
}

// ---------------------------------------------------------------------------
// v0.3.4 — Tag distribution stats card
// ---------------------------------------------------------------------------

/**
 * v0.3.4 — Aggregated count of apps per [AppTag] across the user's
 * assignments. Reactive on the `appTags` map from the Settings flow.
 *
 * Behaviour:
 *  - Hidden entirely when no app is tagged yet (avoids a confusing empty
 *    "0 / 0 / 0" card immediately post-install).
 *  - Renders one row per AppTag preset, sorted by enum ordinal so the order
 *    matches the picker. Tags with 0 hits are still shown (the user can
 *    eyeball "I have no Family-tagged apps").
 *  - Footer total counts UNIQUE tagged packages, not the sum of tag rows —
 *    an app tagged WORK + TOOLS counts as 1 here but contributes 1 to each
 *    of the WORK and TOOLS rows above.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TagStatsSection(appTags: Map<String, Set<AppTag>>) {
    SectionHeader(stringResource(R.string.settings_tag_stats_title))
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            if (appTags.isEmpty()) {
                // v0.3.4 audit C1 fix — render the empty-state hint so the
                // feature is discoverable from Settings BEFORE the user has
                // tagged their first app. Previously the entire section
                // was hidden, which orphaned the i18n string and lost the
                // onboarding cue.
                Text(
                    text  = stringResource(R.string.settings_tag_stats_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val countsByTag = remember(appTags) {
                val out = HashMap<AppTag, Int>(AppTag.entries.size)
                AppTag.entries.forEach { out[it] = 0 }
                for ((_, tags) in appTags) {
                    for (t in tags) {
                        out[t] = (out[t] ?: 0) + 1
                    }
                }
                out
            }
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
            ) {
                AppTag.entries.forEach { tag ->
                    TagStatChip(
                        label = stringResource(appTagLabelRes(tag)),
                        count = countsByTag[tag] ?: 0,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text  = stringResource(R.string.settings_tag_stats_total, appTags.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TagStatChip(label: String, count: Int) {
    Surface(
        color = BrandBlue.copy(alpha = 0.14f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Outlined.Label,
                contentDescription = null,
                tint               = BrandBlue,
                modifier           = Modifier.padding(end = 2.dp),
            )
            Text(
                text       = stringResource(R.string.settings_tag_stats_row, label, count),
                style      = MaterialTheme.typography.labelMedium,
                color      = BrandBlue,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

