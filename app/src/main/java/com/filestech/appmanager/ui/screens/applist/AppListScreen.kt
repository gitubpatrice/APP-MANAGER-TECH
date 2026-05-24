package com.filestech.appmanager.ui.screens.applist

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppSortOrder
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.AppTag
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.UsageStatsAccessBanner
import com.filestech.appmanager.ui.components.dialogs.ConfirmDialog
import com.filestech.appmanager.ui.components.dialogs.DestructiveDialog
import com.filestech.appmanager.ui.components.dialogs.RadioPickerDialog
import com.filestech.appmanager.ui.components.dialogs.appTagLabelRes
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.components.state.ErrorState
import com.filestech.appmanager.ui.components.state.LoadingState
import com.filestech.appmanager.ui.theme.BrandDanger
import timber.log.Timber

/**
 * App list — main screen.
 *
 * Modes:
 * - Normal: TopAppBar with search field + sort + filter + settings actions.
 *           Long-press on an item enters selection mode.
 * - Selection: contextual TopAppBar with selection count, batch actions
 *              (uninstall, clear cache, force stop), and a close button.
 *
 * BackHandler intercepts back when in selection mode to clear the selection
 * instead of popping the back stack.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AppListScreen(
    onNavigateToDetail: (packageName: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: AppListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showSortDialog by rememberSaveable { mutableStateOf(false) }
    var batchDialog by rememberSaveable { mutableStateOf<BatchActionIntent?>(null) }

    // One-shot events → side effects.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AppListViewModel.Event.LaunchIntent -> launchIntent(context, event.intent)
                is AppListViewModel.Event.LaunchIntentsSequentially -> {
                    event.intents.forEach { launchIntent(context, it) }
                }
                is AppListViewModel.Event.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is AppListViewModel.Event.BatchDone -> {
                    val r = event.result
                    // v0.1.3 audit M-2 fix — string externalised + translated FR/EN.
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.batch_done_result, r.successCount, r.total),
                    )
                }
                is AppListViewModel.Event.NavigateToDetail -> onNavigateToDetail(event.packageName)
            }
        }
    }

    // Back exits selection mode first; nav back is the system fallback.
    BackHandler(enabled = state.isInSelectionMode) {
        viewModel.clearSelection()
    }

    // v0.1.1 — re-probe PACKAGE_USAGE_STATS on ON_RESUME so a freshly-granted
    // permission triggers an immediate rescan without the user tapping Refresh.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    Scaffold(
        // v0.1.1 audit H-1 fix — empty contentWindowInsets so we do not
        // re-apply the navigation-bar insets that the parent HomeShell already
        // consumed for its BottomBar (otherwise the last LazyColumn item is
        // hidden behind the NavigationBar on gesture-nav devices).
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar       = {
            if (state.isInSelectionMode) {
                val visibleApps = (state.listOutcome as? Outcome.Success)?.value.orEmpty()
                val allSelected = visibleApps.isNotEmpty() &&
                    state.selectionCount == visibleApps.size
                SelectionTopBar(
                    count          = state.selectionCount,
                    allSelected    = allSelected,
                    onClose        = viewModel::clearSelection,
                    onToggleSelectAll = {
                        if (allSelected) viewModel.clearSelection()
                        else viewModel.selectAll(visibleApps)
                    },
                    onUninstall    = { batchDialog = BatchActionIntent.Uninstall(state.selectionCount) },
                    onClearCache   = { batchDialog = BatchActionIntent.ClearCache(state.selectionCount) },
                    onForceStop    = { batchDialog = BatchActionIntent.ForceStop(state.selectionCount) },
                )
            } else {
                MainTopBar(
                    query                 = state.searchQuery,
                    onQueryChange         = viewModel::onSearchQueryChanged,
                    onSortClick           = { showSortDialog = true },
                    includeSystemApps     = state.filterOptions.includeSystemApps,
                    onToggleSystemApps    = { viewModel.onToggleSystemApps(!state.filterOptions.includeSystemApps) },
                    onRefreshClick        = viewModel::refresh,
                    refreshEnabled        = !state.isRefreshing,
                    onSettingsClick       = onNavigateToSettings,
                )
            }
        },
    ) { innerPadding ->
        AppListBody(
            state             = state,
            innerPadding      = innerPadding,
            listState         = listState,
            onItemClick       = { app ->
                if (state.isInSelectionMode) viewModel.toggleSelection(app.packageName)
                else onNavigateToDetail(app.packageName)
            },
            onItemLongClick   = { app -> viewModel.toggleSelection(app.packageName) },
            onRetry           = {
                // Re-emit current sortOrder to re-trigger the flatMapLatest pipeline.
                viewModel.onSortOrderChanged(state.sortOrder)
            },
            onRefresh         = viewModel::refresh,
            onGrantUsageStats = viewModel::requestUsageStatsPermission,
            onToggleTagFilter = viewModel::onToggleTagFilter,
            onClearTagFilter  = viewModel::onClearTagFilter,
        )
    }

    if (showSortDialog) {
        RadioPickerDialog(
            title    = stringResource(R.string.sort_dialog_title),
            options  = AppSortOrder.entries,
            selected = state.sortOrder,
            labelOf  = { sortOrderLabel(it) },
            onSelect = viewModel::onSortOrderChanged,
            onDismiss = { showSortDialog = false },
        )
    }

    // Confirmation dialogs for batch actions. Destructive ones (uninstall / force
    // stop) paint BrandDanger; cache clear is non-destructive (just opens system
    // app-info one-by-one, the user still taps "Clear cache" themselves).
    when (val d = batchDialog) {
        is BatchActionIntent.Uninstall -> DestructiveDialog(
            title     = stringResource(R.string.dialog_batch_uninstall_title, d.count),
            body      = stringResource(R.string.dialog_batch_uninstall_body),
            onConfirm = {
                viewModel.batchUninstall()
                batchDialog = null
            },
            onDismiss = { batchDialog = null },
        )
        is BatchActionIntent.ClearCache -> ConfirmDialog(
            title     = stringResource(R.string.dialog_batch_clear_cache_title, d.count),
            body      = stringResource(R.string.dialog_batch_clear_cache_body),
            onConfirm = {
                viewModel.batchClearCache()
                batchDialog = null
            },
            onDismiss = { batchDialog = null },
        )
        is BatchActionIntent.ForceStop -> DestructiveDialog(
            title     = stringResource(R.string.dialog_batch_force_stop_title, d.count),
            body      = stringResource(R.string.dialog_batch_force_stop_body),
            onConfirm = {
                viewModel.batchForceStop()
                batchDialog = null
            },
            onDismiss = { batchDialog = null },
        )
        null -> Unit
    }
}

// ---------------------------------------------------------------------------
// Batch-action confirmation state
// ---------------------------------------------------------------------------

private sealed interface BatchActionIntent : java.io.Serializable {
    data class Uninstall(val count: Int) : BatchActionIntent
    data class ClearCache(val count: Int) : BatchActionIntent
    data class ForceStop(val count: Int) : BatchActionIntent
}

// ---------------------------------------------------------------------------
// TopAppBars
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSortClick: () -> Unit,
    includeSystemApps: Boolean,
    onToggleSystemApps: () -> Unit,
    onRefreshClick: () -> Unit,
    refreshEnabled: Boolean,
    onSettingsClick: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Column {
        TopAppBar(
            title   = { BrandedTitle(stringResource(R.string.screen_app_list_title)) },
            actions = {
                IconButton(onClick = onRefreshClick, enabled = refreshEnabled) {
                    Icon(
                        imageVector        = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.action_refresh_apps),
                    )
                }
                // v0.1.3 — Sort + Filter + Settings consolidated in a 3-dots
                // overflow menu per user request (less visual clutter).
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
                        text        = { Text(stringResource(R.string.screen_settings_title)) },
                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        onClick     = {
                            menuOpen = false
                            onSettingsClick()
                        },
                    )
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.sort_dialog_title)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null) },
                        onClick     = {
                            menuOpen = false
                            onSortClick()
                        },
                    )
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.filter_include_system_apps)) },
                        leadingIcon = {
                            // v0.1.3 audit N-2 fix — persistent leading icon
                            // (CheckBox filled when ON, outline when OFF) so
                            // the text doesn't shift horizontally on toggle
                            // and the disabled state is also visible.
                            Icon(
                                imageVector = if (includeSystemApps) Icons.Outlined.CheckBox
                                              else Icons.Outlined.CheckBoxOutlineBlank,
                                contentDescription = null,
                            )
                        },
                        onClick     = {
                            menuOpen = false
                            onToggleSystemApps()
                        },
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(),
        )
        OutlinedTextField(
            value          = query,
            onValueChange  = onQueryChange,
            singleLine     = true,
            placeholder    = { Text(stringResource(R.string.app_list_search_placeholder)) },
            leadingIcon    = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon   = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            },
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onUninstall: () -> Unit,
    onClearCache: () -> Unit,
    onForceStop: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_close))
            }
        },
        title = {
            Text(stringResource(R.string.app_list_selection_count, count))
        },
        actions = {
            // v0.1.2 — single toggle button that swaps Select-all / Unselect-all
            // based on the current selection. Filled checkbox = everything is
            // selected (tap to clear); outline checkbox = some / none selected
            // (tap to select all visible).
            IconButton(onClick = onToggleSelectAll) {
                Icon(
                    imageVector        = if (allSelected) Icons.Outlined.Deselect
                                          else Icons.Outlined.SelectAll,
                    contentDescription = stringResource(
                        if (allSelected) R.string.action_unselect_all
                        else R.string.action_select_all,
                    ),
                )
            }
            IconButton(onClick = onForceStop) {
                Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.app_list_action_force_stop_selected))
            }
            IconButton(onClick = onClearCache) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.app_list_action_clear_cache_selected))
            }
            IconButton(onClick = onUninstall) {
                Icon(
                    imageVector        = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.app_list_action_uninstall_selected),
                    tint               = BrandDanger,
                )
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Body
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListBody(
    state: AppListViewModel.UiState,
    innerPadding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onItemClick: (AppInfo) -> Unit,
    onItemLongClick: (AppInfo) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onGrantUsageStats: () -> Unit,
    onToggleTagFilter: (AppTag) -> Unit,
    onClearTagFilter: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        // v0.1.1 — surfaces the silent PACKAGE_USAGE_STATS denial that
        // otherwise leaves every size at 0 and every "last used" at —.
        UsageStatsAccessBanner(
            visible      = !state.usageStatsGranted,
            onGrantClick = onGrantUsageStats,
        )

        // v0.1.1 — Material 3 PullToRefreshBox: gesture-driven rescan in
        // addition to the toolbar Refresh button.
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh    = onRefresh,
            modifier     = Modifier.fillMaxSize(),
        ) {
            when (val o = state.listOutcome) {
                Outcome.Loading -> LoadingState()
                is Outcome.Failure -> ErrorState(
                    message = o.error.toString(),
                    onRetry = onRetry,
                )
                is Outcome.Success -> {
                    if (o.value.isEmpty()) {
                        EmptyState(
                            icon  = Icons.Outlined.Apps,
                            title = stringResource(R.string.app_list_empty_title),
                            body  = stringResource(R.string.app_list_empty_body),
                        )
                    } else {
                        LazyColumn(
                            state          = listState,
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            item(key = "__overview__") {
                                OverviewCard(apps = o.value)
                            }
                            // v0.3.4 — per-tag filter chip row. Always
                            // rendered (cheap; 5 chips + 1 clear pill) so
                            // the feature is discoverable from Home — the
                            // user does not have to dive into AppDetail to
                            // learn that tags exist. Tapping a chip toggles
                            // its membership in the active filter set; OR
                            // semantics across selected chips.
                            item(key = "__tag_filter__") {
                                TagFilterChipRow(
                                    current = state.tagFilter,
                                    onToggle = onToggleTagFilter,
                                    onClear  = onClearTagFilter,
                                )
                            }
                            item(key = "__section_header__") {
                                SectionHeader(
                                    icon  = Icons.Outlined.Apps,
                                    label = stringResource(R.string.home_section_apps),
                                )
                            }
                            items(
                                items = o.value,
                                key   = { it.packageName },
                            ) { app ->
                                val isSelected = app.packageName in state.selectedPackages
                                AppListItem(
                                    app           = app,
                                    isSelected    = isSelected,
                                    tags          = state.appTags[app.packageName].orEmpty(),
                                    onClick       = { onItemClick(app) },
                                    onLongClick   = { onItemLongClick(app) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// v0.1.2 — Home overview card (RFT-style summary)
// ---------------------------------------------------------------------------

@Composable
private fun OverviewCard(apps: List<AppInfo>) {
    val context = LocalContext.current
    // v0.1.2 audit L-1: fused into a single remember slot — one allocation + one O(n) pass.
    val (totalBytes, cacheBytes) = remember(apps) {
        apps.fold(0L to 0L) { (t, c), a -> (t + a.totalSizeBytes) to (c + a.cacheSizeBytes) }
    }
    val totalLabel = remember(totalBytes) { Formatter.formatShortFileSize(context, totalBytes) }
    val cacheLabel = remember(cacheBytes) { Formatter.formatShortFileSize(context, cacheBytes) }

    // v0.1.2 — ElevatedCard for the premium drop-shadow look (user feedback:
    // "il y a des ombres sous les blocks, c'est plus premium").
    androidx.compose.material3.ElevatedCard(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // v0.1.2 audit M-3: home_overview_title now actually wired (was a
            // dead string).
            Text(
                text       = stringResource(R.string.home_overview_title),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text       = stringResource(R.string.home_overview_apps_count, apps.size),
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = stringResource(R.string.home_overview_total_size, totalLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (cacheBytes > 0L) {
                Text(
                    text  = stringResource(R.string.home_overview_cache_size, cacheLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier          = Modifier.padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text       = label,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Item
// ---------------------------------------------------------------------------

@OptIn(
    ExperimentalComposeUiApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
private fun AppListItem(
    app: AppInfo,
    isSelected: Boolean,
    tags: Set<AppTag>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val sizeLabel = remember(app.totalSizeBytes) {
        Formatter.formatShortFileSize(context, app.totalSizeBytes)
    }
    val bg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(bg),
        color = bg,
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(packageName = app.packageName, size = 40.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = app.label,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    text     = app.packageName,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // v0.3.4 — badges row: SYSTEM / DISABLED (preserved from
                // v0.1.x) + USER TAGS appended. FlowRow so long tag sets
                // wrap to a second line instead of clipping. Untagged apps
                // skip the tag chips entirely — same visual footprint as
                // before for the common case.
                if (app.isSystemApp || !app.isEnabled || tags.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier              = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp),
                    ) {
                        if (app.isSystemApp) {
                            TagBadge(stringResource(R.string.app_list_system_badge))
                        }
                        if (!app.isEnabled) {
                            TagBadge(
                                text = stringResource(R.string.app_list_disabled_badge),
                                tint = BrandDanger,
                            )
                        }
                        tags.sortedBy { it.ordinal }.forEach { tag ->
                            TagBadge(
                                text = stringResource(appTagLabelRes(tag)),
                                tint = BrandBlue,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text  = sizeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * v0.3.4 — Multi-select horizontal chip row for the per-tag filter. The
 * leading "Tous" pill is selected when no chip is active (zero filter);
 * tapping it clears every selected tag chip. Each AppTag chip toggles its
 * membership in the filter set on tap.
 *
 * Sticks to the BrandBlue palette established by the AppDetail tag chip so
 * the filter feels visually tied to the per-app assignment UX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterChipRow(
    current: Set<AppTag>,
    onToggle: (AppTag) -> Unit,
    onClear: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        TagFilterChip(
            label    = stringResource(R.string.tag_filter_all),
            selected = current.isEmpty(),
            onClick  = onClear,
        )
        AppTag.entries.forEach { tag ->
            TagFilterChip(
                label    = stringResource(appTagLabelRes(tag)),
                selected = tag in current,
                onClick  = { onToggle(tag) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label) },
        colors   = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = BrandBlue.copy(alpha = 0.18f),
            selectedLabelColor     = BrandBlue,
        ),
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            enabled       = true,
            selected      = selected,
            borderColor   = MaterialTheme.colorScheme.outline,
            selectedBorderColor = BrandBlue,
        ),
    )
}

@Composable
private fun TagBadge(text: String, tint: androidx.compose.ui.graphics.Color? = null) {
    val container = tint ?: MaterialTheme.colorScheme.secondary
    Surface(
        shape   = RoundedCornerShape(50),
        color   = container.copy(alpha = 0.12f),
        contentColor = container,
    ) {
        Text(
            text  = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

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

private fun launchIntent(context: android.content.Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Timber.w(it, "Failed to launch intent %s", intent) }
}
