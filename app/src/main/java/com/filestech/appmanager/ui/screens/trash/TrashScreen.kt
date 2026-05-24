package com.filestech.appmanager.ui.screens.trash

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
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
import com.filestech.appmanager.domain.model.TrashItem
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.dialogs.ConfirmDialog
import com.filestech.appmanager.ui.components.dialogs.CriticalWarningDialog
import com.filestech.appmanager.ui.components.dialogs.DestructiveDialog
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.theme.BrandDanger
import timber.log.Timber
import java.text.DateFormat
import java.util.Date

/**
 * Corbeille / Trash screen — review apps staged for uninstall.
 *
 * Brand discipline: the trash itself signals **destructive intent**, so every
 * surface here paints [BrandDanger] — TopAppBar icon, top-level "Empty trash"
 * action, per-row "Uninstall now" button. Restore is non-destructive
 * (BrandBlue via [FilledTonalButton]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var dialog by rememberSaveable { mutableStateOf<TrashDialog?>(null) }
    // v0.3.1 Safety Phase B — pending critical confirmation surfaced by the
    // ViewModel. Held in `remember` (not rememberSaveable) because
    // PendingAction is not Serializable; a config change drops the dialog,
    // which is acceptable — the user simply taps the action again.
    var criticalConfirm by remember { mutableStateOf<CriticalConfirmState?>(null) }

    // v0.2.1 bug fix — when the user returns from the OS uninstall
    // confirmation dialog (Android pauses our Activity for the system one),
    // sweep the Trash for orphaned rows whose package PackageManager no
    // longer knows about. Without this, the trashed app stays in the list
    // with stale Restore / Uninstall buttons even though it is fully gone.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TrashViewModel.Event.LaunchIntent -> launchIntent(context, event.intent)
                is TrashViewModel.Event.LaunchIntents -> {
                    event.intents.forEach { launchIntent(context, it) }
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.trash_emptied_snackbar, event.total),
                    )
                }
                is TrashViewModel.Event.Restored -> {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.trash_restored_snackbar, event.packageName),
                    )
                }
                is TrashViewModel.Event.ShowError -> snackbarHostState.showSnackbar(event.message)
                is TrashViewModel.Event.RequiresCriticalConfirmation ->
                    criticalConfirm = CriticalConfirmState(
                        classification = event.classification,
                        action         = event.action,
                        appLabel       = items.firstOrNull {
                            // For UninstallOne the label comes from the matching trash row.
                            // For EmptyTrash we still show the first detected critical's label.
                            it.packageName == event.classification.packageName
                        }?.label ?: event.classification.packageName,
                    )
            }
        }
    }

    val totalBytes = remember(items) { items.sumOf { it.totalSizeBytes } }
    val totalLabel = remember(totalBytes) { Formatter.formatShortFileSize(context, totalBytes) }

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
                title = {
                    // v0.1.3 — Files Tech brand mark + title (uniform across all screens).
                    // The destructive-intent identity is carried by the actions on the right
                    // (Empty trash BrandDanger) + the screen body (red sub-title, red per-row
                    // Uninstall buttons), so the title no longer needs the red Icon prefix.
                    BrandedTitle(stringResource(R.string.screen_trash_title))
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { dialog = TrashDialog.EmptyAll }) {
                            Icon(
                                imageVector        = Icons.Outlined.DeleteForever,
                                contentDescription = stringResource(R.string.trash_action_empty_all),
                                tint               = BrandDanger,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        TrashBody(
            innerPadding = innerPadding,
            items        = items,
            totalLabel   = totalLabel,
            onRestore    = { item -> dialog = TrashDialog.Restore(item.packageName, item.label) },
            onUninstall  = { item -> viewModel.uninstallNow(item.packageName) },
        )
    }

    when (val d = dialog) {
        is TrashDialog.Restore -> ConfirmDialog(
            title     = stringResource(R.string.dialog_trash_restore_title, d.label),
            body      = stringResource(R.string.dialog_trash_restore_body),
            onConfirm = {
                viewModel.restore(d.packageName)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        TrashDialog.EmptyAll -> DestructiveDialog(
            title     = stringResource(R.string.dialog_trash_empty_title),
            body      = stringResource(R.string.dialog_trash_empty_body),
            onConfirm = {
                viewModel.emptyTrash()
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    // v0.3.1 Safety Phase B — hold-3s critical confirmation surfaced by the
    // ViewModel after detecting a critical package among the Trash items.
    criticalConfirm?.let { state ->
        val actionLabel = stringResource(
            when (state.action) {
                is TrashViewModel.PendingAction.UninstallOne -> R.string.critical_action_uninstall
                is TrashViewModel.PendingAction.EmptyTrash   -> R.string.critical_action_empty_trash
            },
        )
        CriticalWarningDialog(
            appLabel    = state.appLabel,
            actionLabel = actionLabel,
            category    = state.classification.category,
            onConfirm = {
                when (val a = state.action) {
                    is TrashViewModel.PendingAction.UninstallOne ->
                        viewModel.uninstallNow(a.packageName, bypassCriticalCheck = true)
                    is TrashViewModel.PendingAction.EmptyTrash ->
                        viewModel.emptyTrash(bypassCriticalCheck = true)
                }
                criticalConfirm = null
            },
            onCancel = { criticalConfirm = null },
        )
    }
}

private data class CriticalConfirmState(
    val classification: com.filestech.appmanager.domain.model.CriticalClassification,
    val action: TrashViewModel.PendingAction,
    val appLabel: String,
)

// ---------------------------------------------------------------------------
// Body
// ---------------------------------------------------------------------------

@Composable
private fun TrashBody(
    innerPadding: PaddingValues,
    items: List<TrashItem>,
    totalLabel: String,
    onRestore: (TrashItem) -> Unit,
    onUninstall: (TrashItem) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        if (items.isEmpty()) {
            EmptyState(
                icon  = Icons.Outlined.Delete,
                title = stringResource(R.string.trash_empty_title),
                body  = stringResource(R.string.trash_empty_body),
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text     = stringResource(R.string.trash_subtitle, items.size, totalLabel),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = BrandDanger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = items, key = { it.packageName }) { item ->
                        TrashRow(
                            item        = item,
                            onRestore   = { onRestore(item) },
                            onUninstall = { onUninstall(item) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashRow(
    item: TrashItem,
    onRestore: () -> Unit,
    onUninstall: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val sizeLabel = remember(item.totalSizeBytes) {
        Formatter.formatShortFileSize(context, item.totalSizeBytes)
    }
    val addedLabel = remember(item.addedAt) { dateFormat.format(Date(item.addedAt)) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = item.packageName, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = item.label,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    text     = "${item.packageName} · $sizeLabel",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = stringResource(R.string.trash_added_on, addedLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick  = onRestore,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.trash_action_restore))
            }
            OutlinedButton(
                onClick  = onUninstall,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = BrandDanger)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.trash_action_uninstall_now), color = BrandDanger)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialog state
// ---------------------------------------------------------------------------

private sealed interface TrashDialog : java.io.Serializable {
    data class Restore(val packageName: String, val label: String) : TrashDialog
    data object EmptyAll : TrashDialog
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun launchIntent(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Timber.w(it, "Failed to launch intent %s", intent) }
}
