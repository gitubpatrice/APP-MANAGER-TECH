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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppDetail
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.PrivacyScore
import com.filestech.appmanager.domain.model.TrackerReport
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.PrivacyDataPanel
import com.filestech.appmanager.ui.components.UsageStatsAccessBanner
import com.filestech.appmanager.ui.components.dialogs.ConfirmDialog
import com.filestech.appmanager.ui.components.dialogs.DestructiveDialog
import com.filestech.appmanager.ui.components.dialogs.CriticalWarningDialog
import com.filestech.appmanager.ui.components.dialogs.QuarantineConfigDialog
import com.filestech.appmanager.ui.components.dialogs.SoftQuarantineActionDialog
import com.filestech.appmanager.ui.components.dialogs.UninstallChoiceDialog
import com.filestech.appmanager.domain.model.CriticalClassification
import com.filestech.appmanager.domain.model.QuarantineMode
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
    /**
     * v0.2.1 UX add — navigates to the Trash screen. Used by the
     * "Voir la corbeille" shortcut button that appears in the ActionsCard
     * after a successful Move-to-trash. Default {} keeps the function
     * usable in isolation (tests, previews) without breaking the signature.
     */
    onOpenTrash: () -> Unit = {},
    /**
     * v0.2.2 — navigates to the Expert Mode screen for the current package.
     * Default {} preserves backward compat for previews / tests.
     */
    onOpenExpert: () -> Unit = {},
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleEvents by viewModel.lifecycleEvents.collectAsStateWithLifecycle()
    val currentTag by viewModel.currentTag.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // v0.3.3 — tag picker open state. Not Saveable because AppTag enum is
    // safely Saveable via its `name`, but the simplest boolean toggle is
    // sufficient — config change re-derives from currentTag.
    var tagDialogOpen by rememberSaveable { mutableStateOf(false) }

    var confirmDialog by rememberSaveable { mutableStateOf<ConfirmIntent?>(null) }
    var quarantineDialogOpen by rememberSaveable { mutableStateOf(false) }
    // Intent is Parcelable not Serializable — `remember` instead of
    // rememberSaveable. A mid-dialog rotation drops the dialog; acceptable
    // for a transient prompt (the quarantine entry is already persisted).
    var softQuarantinePending by remember { mutableStateOf<SoftQuarantinePrompt?>(null) }
    var criticalConfirm by remember { mutableStateOf<CriticalConfirmState?>(null) }

    LaunchedEffect(packageName) {
        viewModel.load(packageName)
    }

    // v0.2.1 — re-probe PACKAGE_USAGE_STATS on ON_RESUME so once the user
    // grants the permission in Settings and comes back, the banner disappears
    // and the screen reloads with real lastUsedTime (was always "Jamais
    // utilisée" without this permission — root cause of user report
    // "dans information d'installation, la fonction dernière utilisation
    // ne marche pas, toujours jamais utilisée").
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
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
                AppDetailViewModel.Event.NeedsBackupFolder ->
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.quarantine_error_needs_backup_folder),
                    )
                is AppDetailViewModel.Event.LaunchIntentChain ->
                    if (!launchIntentChain(context, event.intents)) {
                        snackbarHostState.showSnackbar(context.getString(R.string.error_no_handler))
                    }
                is AppDetailViewModel.Event.SoftQuarantineCreated ->
                    softQuarantinePending = SoftQuarantinePrompt(
                        intent       = event.intent,
                        label        = event.label,
                        durationDays = event.durationDays,
                    )
                is AppDetailViewModel.Event.RequiresCriticalConfirmation ->
                    criticalConfirm = CriticalConfirmState(
                        classification = event.classification,
                        action         = event.action,
                        durationDays   = event.durationDays,
                        appLabel       = (state.detailOutcome as? Outcome.Success)?.value?.info?.label
                            ?: event.classification.packageName,
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
                    BrandedTitle(
                        screenTitle = (state.detailOutcome as? Outcome.Success)
                            ?.value?.info?.label
                            ?: stringResource(R.string.screen_app_detail_title),
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
            state             = state,
            lifecycleEvents   = lifecycleEvents,
            currentTag        = currentTag,
            onTagClick        = { tagDialogOpen = true },
            innerPadding      = innerPadding,
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
            onQuarantine             = { quarantineDialogOpen = true },
            onOpenPermissionsSettings = viewModel::openAppPermissionsSettings,
            onToggleIgnore           = viewModel::toggleIgnore,
            onRequestUsageStatsPerm  = viewModel::openUsageAccessSettings,
            onOpenTrash              = onOpenTrash,
            onOpenExpert             = onOpenExpert,
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

    if (quarantineDialogOpen) {
        val label = (state.detailOutcome as? Outcome.Success)?.value?.info?.label ?: packageName
        QuarantineConfigDialog(
            label     = label,
            isWorking = false,
            onDismiss = { quarantineDialogOpen = false },
            onConfirm = { mode, days ->
                viewModel.quarantine(mode, days)
                quarantineDialogOpen = false
            },
        )
    }

    softQuarantinePending?.let { prompt ->
        SoftQuarantineActionDialog(
            label        = prompt.label,
            durationDays = prompt.durationDays,
            onOpenSettings = {
                launchIntent(context, prompt.intent)
                softQuarantinePending = null
            },
            onLater = { softQuarantinePending = null },
        )
    }

    // v0.3.3 — tag picker dialog. Derives the label from the loaded
    // AppDetail when available, falls back to the package name (the dialog
    // shouldn't open before the detail loads, but the fallback keeps the
    // string non-empty under any race).
    if (tagDialogOpen) {
        val label = (state.detailOutcome as? Outcome.Success)?.value?.info?.label
            ?: state.packageName
        com.filestech.appmanager.ui.components.dialogs.TagPickerDialog(
            appLabel  = label,
            current   = currentTag,
            onConfirm = { picked ->
                viewModel.setTag(picked)
                tagDialogOpen = false
            },
            onDismiss = { tagDialogOpen = false },
        )
    }

    criticalConfirm?.let { state ->
        val actionLabel = stringResource(
            when (state.action) {
                AppDetailViewModel.CriticalAction.UNINSTALL       -> R.string.critical_action_uninstall
                AppDetailViewModel.CriticalAction.QUARANTINE_HARD -> R.string.critical_action_quarantine_hard
                AppDetailViewModel.CriticalAction.DISABLE         -> R.string.critical_action_disable
            },
        )
        CriticalWarningDialog(
            appLabel    = state.appLabel,
            actionLabel = actionLabel,
            category    = state.classification.category,
            onConfirm = {
                when (state.action) {
                    AppDetailViewModel.CriticalAction.UNINSTALL ->
                        viewModel.uninstall(bypassCriticalCheck = true)
                    AppDetailViewModel.CriticalAction.QUARANTINE_HARD ->
                        viewModel.quarantine(
                            mode = QuarantineMode.HARD_UNINSTALL,
                            durationDays = state.durationDays,
                            bypassCriticalCheck = true,
                        )
                    AppDetailViewModel.CriticalAction.DISABLE ->
                        viewModel.disable(bypassCriticalCheck = true)
                }
                criticalConfirm = null
            },
            onCancel = { criticalConfirm = null },
        )
    }
    // Hint kotlin scope is used (rememberCoroutineScope keeps for future Phase VI batch flows).
    @Suppress("UNUSED_EXPRESSION") scope
}

/** State holder for the post-event-fire Safety Guardrails dialog. */
private data class CriticalConfirmState(
    val classification: CriticalClassification,
    val action: AppDetailViewModel.CriticalAction,
    val durationDays: Int,
    val appLabel: String,
)

/**
 * Holds the data needed to render the SOFT-mode post-quarantine explanation
 * dialog. NOT [java.io.Serializable] because [Intent] is Parcelable, not
 * Serializable — the wrapping state uses plain `remember`, so a config change
 * during the dialog drops it (the quarantine entry is already persisted, so
 * the user can re-trigger from the Quarantine list).
 */
private data class SoftQuarantinePrompt(
    val intent: Intent,
    val label: String,
    val durationDays: Int,
)

// ---------------------------------------------------------------------------
// Body
// ---------------------------------------------------------------------------

@Composable
private fun AppDetailBody(
    state: AppDetailViewModel.UiState,
    lifecycleEvents: List<com.filestech.appmanager.domain.model.LifecycleEvent>,
    currentTag: com.filestech.appmanager.domain.model.AppTag?,
    onTagClick: () -> Unit,
    innerPadding: PaddingValues,
    onRetry: () -> Unit,
    onUninstall: () -> Unit,
    onClearCache: () -> Unit,
    onForceStop: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onQuarantine: () -> Unit,
    onOpenPermissionsSettings: () -> Unit,
    onToggleIgnore: () -> Unit,
    onRequestUsageStatsPerm: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenExpert: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        // v0.2.1 fix — missing PACKAGE_USAGE_STATS makes lastUsedTime = 0
        // (rendered as "Jamais utilisée") and storage sizes empty. Banner
        // deep-links to Settings → Usage access; ON_RESUME re-probe in the
        // parent Composable auto-reloads on grant.
        UsageStatsAccessBanner(
            visible      = !state.usageStatsGranted,
            onGrantClick = onRequestUsageStatsPerm,
        )
        // v0.2.1 audit M-4 / L-2 fix — `weight(1f)` not `fillMaxSize()`. The
        // Column parent is fillMaxSize; a child Box with fillMaxSize demands
        // all available height and squeezes the banner out. weight(1f) lets
        // the banner take its natural height and the Box absorbs the remainder.
        Box(modifier = Modifier.weight(1f)) {
            when (val o = state.detailOutcome) {
                Outcome.Loading -> LoadingState()
                is Outcome.Failure -> ErrorState(
                    message = o.error.toString(),
                    onRetry = onRetry,
                )
                is Outcome.Success -> AppDetailContent(
                    detail                    = o.value,
                    privacyScore              = state.privacyScore,
                    trackerReport             = state.trackerReport,
                    isIgnored                 = state.isIgnored,
                    recentlyMovedToTrash      = state.recentlyMovedToTrash,
                    lifecycleEvents           = lifecycleEvents,
                    currentTag                = currentTag,
                    onTagClick                = onTagClick,
                    onUninstall               = onUninstall,
                    onClearCache              = onClearCache,
                    onForceStop               = onForceStop,
                    onToggleEnabled           = onToggleEnabled,
                    onQuarantine              = onQuarantine,
                    onOpenPermissionsSettings = onOpenPermissionsSettings,
                    onToggleIgnore            = onToggleIgnore,
                    onOpenTrash               = onOpenTrash,
                    onOpenExpert              = onOpenExpert,
                )
            }
        }
    }
}

@Composable
private fun AppDetailContent(
    detail: AppDetail,
    privacyScore: PrivacyScore?,
    trackerReport: TrackerReport?,
    isIgnored: Boolean,
    recentlyMovedToTrash: Boolean,
    lifecycleEvents: List<com.filestech.appmanager.domain.model.LifecycleEvent>,
    currentTag: com.filestech.appmanager.domain.model.AppTag?,
    onTagClick: () -> Unit,
    onUninstall: () -> Unit,
    onClearCache: () -> Unit,
    onForceStop: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onQuarantine: () -> Unit,
    onOpenPermissionsSettings: () -> Unit,
    onToggleIgnore: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenExpert: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        HeaderCard(info = detail.info, currentTag = currentTag, onTagClick = onTagClick)
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
            info                 = detail.info,
            isIgnored            = isIgnored,
            recentlyMovedToTrash = recentlyMovedToTrash,
            onUninstall          = onUninstall,
            onClearCache         = onClearCache,
            onForceStop          = onForceStop,
            onToggleEnabled      = onToggleEnabled,
            onQuarantine         = onQuarantine,
            onToggleIgnore       = onToggleIgnore,
            onOpenTrash          = onOpenTrash,
        )
        SectionHeader(stringResource(R.string.app_detail_section_permissions))
        PermissionsCard(
            detail                    = detail,
            onOpenPermissionsSettings = onOpenPermissionsSettings,
        )
        SectionHeader(stringResource(R.string.app_detail_section_install_info))
        InstallInfoCard(detail.info)
        // v0.3.1 — Lifecycle inline section: 5 most recent lifecycle events
        // for this package (read-only). Shown only when the Lifecycle History
        // feature has actually recorded at least one event for this app —
        // avoids a confusing empty section for users who haven't enabled the
        // feature yet (the screen's "no events" state is reserved for the
        // dedicated LifecycleHistoryScreen, which carries the activation CTA).
        if (lifecycleEvents.isNotEmpty()) {
            SectionHeader(stringResource(R.string.app_detail_section_lifecycle))
            LifecycleInlineCard(events = lifecycleEvents.take(5))
        }
        // v0.2.2 — entry point to the Expert Mode (advanced inspector). Kept
        // at the bottom so it doesn't compete visually with the action card.
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick  = onOpenExpert,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector        = Icons.Outlined.DataObject,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.app_detail_open_expert))
        }
    }
}

/**
 * v0.3.1 — Inline lifecycle timeline (5 latest events). Read-only — the user
 * can drill down to the full per-app history via [LifecycleHistoryScreen]
 * (filter by package — to be added in a follow-up if needed). Each event
 * row shows a type badge + version + date; reason chip when the user
 * captured one.
 */
@Composable
private fun LifecycleInlineCard(
    events: List<com.filestech.appmanager.domain.model.LifecycleEvent>,
) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    SectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            events.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                LifecycleInlineRow(event = event, dateFormat = dateFormat)
            }
        }
    }
}

@Composable
private fun LifecycleInlineRow(
    event: com.filestech.appmanager.domain.model.LifecycleEvent,
    dateFormat: DateFormat,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Type chip — colored by destructiveness.
        val tint = when (event.type) {
            com.filestech.appmanager.domain.model.LifecycleEventType.UNINSTALLED -> BrandDanger
            com.filestech.appmanager.domain.model.LifecycleEventType.INSTALLED,
            com.filestech.appmanager.domain.model.LifecycleEventType.REPLACED   -> BrandBlue
            com.filestech.appmanager.domain.model.LifecycleEventType.BASELINE   -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(
            color = tint.copy(alpha = 0.14f),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text     = stringResource(
                    when (event.type) {
                        com.filestech.appmanager.domain.model.LifecycleEventType.BASELINE    -> R.string.lifecycle_type_baseline
                        com.filestech.appmanager.domain.model.LifecycleEventType.INSTALLED   -> R.string.lifecycle_type_installed
                        com.filestech.appmanager.domain.model.LifecycleEventType.UNINSTALLED -> R.string.lifecycle_type_uninstalled
                        com.filestech.appmanager.domain.model.LifecycleEventType.REPLACED    -> R.string.lifecycle_type_replaced
                    },
                ),
                style    = MaterialTheme.typography.labelSmall,
                color    = tint,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = event.versionName?.let { "v$it" } ?: dateFormat.format(Date(event.capturedAt)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (event.versionName != null) {
                Text(
                    text  = dateFormat.format(Date(event.capturedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun HeaderCard(
    info: AppInfo,
    currentTag: com.filestech.appmanager.domain.model.AppTag?,
    onTagClick: () -> Unit,
) {
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
                // v0.3.3 — tap row showing the current user tag chip + an
                // edit affordance. When no tag is assigned we still render
                // the "Add a tag" call-to-action so the feature is
                // discoverable from AppDetail without burying it in Outils.
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier
                        .clickable(onClick = onTagClick)
                        .padding(vertical = 2.dp),
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Outlined.Label,
                        contentDescription = null,
                        tint               = BrandBlue,
                        modifier           = Modifier.size(16.dp),
                    )
                    if (currentTag != null) {
                        Surface(
                            color = BrandBlue.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text     = stringResource(
                                    com.filestech.appmanager.ui.components.dialogs.appTagLabelRes(currentTag),
                                ),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = BrandBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        Text(
                            text  = stringResource(R.string.tag_chip_add),
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandBlue,
                        )
                    }
                }
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
    recentlyMovedToTrash: Boolean,
    onUninstall: () -> Unit,
    onClearCache: () -> Unit,
    onForceStop: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onQuarantine: () -> Unit,
    onToggleIgnore: () -> Unit,
    onOpenTrash: () -> Unit,
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
            // v0.2.1 UX add — "Voir la corbeille" shortcut appears under
            // Uninstall right after a successful Move-to-trash, so the user
            // can immediately jump to the Trash screen and confirm the
            // staged app. Fond rouge clair + texte sombre per user request
            // ("intuitif comme ça").
            if (recentlyMovedToTrash) {
                FilledTonalButton(
                    onClick  = onOpenTrash,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = BrandDanger.copy(alpha = 0.15f),
                        contentColor   = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint               = BrandDanger,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.app_detail_action_view_trash))
                }
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
            // v0.2.0 — Quarantine this app. Opens the shared QuarantineConfigDialog
            // (mode picker + duration slider). HARD mode requires the user to
            // have picked a backup folder in Settings → Quarantine; if not, the
            // VM emits NeedsBackupFolder and the snackbar guides the user there.
            OutlinedButton(
                onClick  = onQuarantine,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.app_detail_action_quarantine))
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
private fun PermissionsCard(
    detail: AppDetail,
    onOpenPermissionsSettings: () -> Unit,
) {
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
            // v0.2.0 — one-tap deep-link to OS Permissions sub-page for this
            // app. Direct response to feedback "qui me renvoie au bon endroit
            // sans chercher 2 heures" — used after a permission drift to
            // re-grant without navigating through 3 Settings screens.
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick  = onOpenPermissionsSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.app_detail_open_android_permissions))
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
            // v0.3.2 — surface the OS-reported hibernation state when true.
            // Skipping the row when false avoids cluttering the card for the
            // common case of an actively-used app.
            if (info.isHibernated) {
                InfoRow(
                    label = stringResource(R.string.app_detail_hibernated),
                    value = stringResource(R.string.app_detail_hibernated_yes),
                )
            }
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

/**
 * Tries each intent in order, returns true on the first that launches.
 * Catches ActivityNotFoundException + SecurityException — both mean "this
 * handler is not usable", fall through to the next candidate. Used for
 * deep-links where Android 11+ package visibility can lie about handler
 * availability (cf. IntentFactory.appPermissionsSettingsChain).
 */
private fun launchIntentChain(context: android.content.Context, intents: List<Intent>): Boolean {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.w(e, "AppDetail: no handler for %s, trying next", intent.action)
        } catch (e: SecurityException) {
            Timber.w(e, "AppDetail: security denial for %s, trying next", intent.action)
        }
    }
    Timber.e("AppDetail: NO handler available across %d candidates", intents.size)
    return false
}
