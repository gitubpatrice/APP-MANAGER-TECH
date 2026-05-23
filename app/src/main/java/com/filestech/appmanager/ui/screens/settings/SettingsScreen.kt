package com.filestech.appmanager.ui.screens.settings

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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.AppSortOrder
import com.filestech.appmanager.domain.model.ThemeMode
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.dialogs.RadioPickerDialog
import com.filestech.appmanager.ui.components.settings.NavigationRow
import com.filestech.appmanager.ui.components.settings.SectionHeader
import com.filestech.appmanager.ui.components.settings.ToggleRow
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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var themeDialog by rememberSaveable { mutableStateOf(false) }
    var sortDialog by rememberSaveable { mutableStateOf(false) }

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
