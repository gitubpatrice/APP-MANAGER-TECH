package com.filestech.appmanager.ui.screens.applist

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.dialogs.ConfirmDialog
import com.filestech.appmanager.ui.components.dialogs.DestructiveDialog
import com.filestech.appmanager.ui.components.dialogs.RadioPickerDialog
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
    onNavigateToStorage: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: AppListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showSortDialog by rememberSaveable { mutableStateOf(false) }
    var showFilterDialog by rememberSaveable { mutableStateOf(false) }
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
                    val msg = "${r.successCount}/${r.total} succeeded"
                    snackbarHostState.showSnackbar(msg)
                }
                is AppListViewModel.Event.NavigateToDetail -> onNavigateToDetail(event.packageName)
            }
        }
    }

    // Back exits selection mode first; nav back is the system fallback.
    BackHandler(enabled = state.isInSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar       = {
            if (state.isInSelectionMode) {
                SelectionTopBar(
                    count          = state.selectionCount,
                    onClose        = viewModel::clearSelection,
                    onUninstall    = { batchDialog = BatchActionIntent.Uninstall(state.selectionCount) },
                    onClearCache   = { batchDialog = BatchActionIntent.ClearCache(state.selectionCount) },
                    onForceStop    = { batchDialog = BatchActionIntent.ForceStop(state.selectionCount) },
                )
            } else {
                MainTopBar(
                    query              = state.searchQuery,
                    onQueryChange      = viewModel::onSearchQueryChanged,
                    onSortClick        = { showSortDialog = true },
                    onFilterClick      = { showFilterDialog = true },
                    onStorageClick     = onNavigateToStorage,
                    onSettingsClick    = onNavigateToSettings,
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

    if (showFilterDialog) {
        FilterPickerDialog(
            includeSystemApps = state.filterOptions.includeSystemApps,
            onToggleSystem    = viewModel::onToggleSystemApps,
            onDismiss         = { showFilterDialog = false },
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
    onFilterClick: () -> Unit,
    onStorageClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column {
        TopAppBar(
            title   = { Text(stringResource(R.string.screen_app_list_title)) },
            actions = {
                IconButton(onClick = onSortClick) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.cd_sort))
                }
                IconButton(onClick = onFilterClick) {
                    Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.cd_filter))
                }
                IconButton(onClick = onStorageClick) {
                    Icon(Icons.Outlined.Apps, contentDescription = stringResource(R.string.storage_title))
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.cd_settings))
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
    onClose: () -> Unit,
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

@Composable
private fun AppListBody(
    state: AppListViewModel.UiState,
    innerPadding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onItemClick: (AppInfo) -> Unit,
    onItemLongClick: (AppInfo) -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
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
                        icon = Icons.Outlined.Apps,
                        title = stringResource(R.string.app_list_empty_title),
                        body  = stringResource(R.string.app_list_empty_body),
                    )
                } else {
                    LazyColumn(
                        state    = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = o.value,
                            key   = { it.packageName },
                        ) { app ->
                            val isSelected = app.packageName in state.selectedPackages
                            AppListItem(
                                app           = app,
                                isSelected    = isSelected,
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

// ---------------------------------------------------------------------------
// Item
// ---------------------------------------------------------------------------

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppListItem(
    app: AppInfo,
    isSelected: Boolean,
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
                Row(
                    modifier            = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment   = Alignment.CenterVertically,
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
// Filter dialog (Phase V simple: single switch — Phase VIII can grow this)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPickerDialog(
    includeSystemApps: Boolean,
    onToggleSystem: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title         = { Text(stringResource(R.string.filter_title)) },
        text          = {
            Column {
                com.filestech.appmanager.ui.components.settings.ToggleRow(
                    title           = stringResource(R.string.filter_include_system_apps),
                    checked         = includeSystemApps,
                    onCheckedChange = onToggleSystem,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close))
            }
        },
    )
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
