package com.filestech.appmanager.ui.screens.quarantine

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.QuarantineEntry
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.dialogs.ConfirmDialog
import com.filestech.appmanager.ui.components.dialogs.DestructiveDialog
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.theme.BrandDanger
import timber.log.Timber
import java.text.DateFormat
import java.util.Date

/**
 * Quarantine list — read-only view of currently quarantined apps with
 * per-row Restore + Drop actions and a FAB to open the [QuarantinePicker]
 * which selects + configures a new quarantine.
 *
 * Visual cues:
 *  - Expired entries (restoreAt <= now) → amber/red "Échue" badge so user
 *    knows the timer is up.
 *  - HARD vs SOFT mode badge in slate / blue to disambiguate at a glance.
 *  - Drop action is destructive (BrandDanger) — confirmation dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarantineScreen(
    onBack: () -> Unit,
    onPickApp: () -> Unit,
    viewModel: QuarantineViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var dialog by rememberSaveable { mutableStateOf<QuarantineDialog?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is QuarantineViewModel.Event.LaunchIntent -> launchIntent(context, event.intent)
                is QuarantineViewModel.Event.ShowError -> snackbarHostState.showSnackbar(event.message)
                is QuarantineViewModel.Event.BackupMissing -> dialog = QuarantineDialog.BackupMissing(event.packageName)
                QuarantineViewModel.Event.NeedsBackupFolder ->
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.quarantine_error_needs_backup_folder),
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
                title = { BrandedTitle(stringResource(R.string.screen_quarantine_title)) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickApp,
                icon    = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text    = { Text(stringResource(R.string.quarantine_fab_add)) },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (items.isEmpty()) {
                EmptyState(
                    icon  = Icons.Outlined.Inventory2,
                    title = stringResource(R.string.quarantine_empty_title),
                    body  = stringResource(R.string.quarantine_empty_body),
                )
            } else {
                QuarantineList(
                    items     = items,
                    onRestore = { dialog = QuarantineDialog.ConfirmRestore(it) },
                    onDrop    = { dialog = QuarantineDialog.ConfirmDrop(it) },
                )
            }
        }
    }

    when (val d = dialog) {
        is QuarantineDialog.ConfirmRestore -> {
            // v0.2.0 audit M-4 fix — HARD restore launches PackageInstaller
            // (modifies device state, non-trivial to undo) → DestructiveDialog
            // (red). SOFT restore just deep-links to OS Settings → safe →
            // ConfirmDialog (blue). The visual cue matches the brand
            // discipline (BrandDanger for state-mutating actions).
            val mode = items.firstOrNull { it.packageName == d.entry.packageName }?.mode
                ?: d.entry.mode
            if (mode == QuarantineMode.HARD_UNINSTALL) {
                DestructiveDialog(
                    title     = stringResource(R.string.quarantine_dialog_restore_title, d.entry.label),
                    body      = stringResource(R.string.quarantine_dialog_restore_body_hard),
                    onConfirm = {
                        viewModel.restoreEntry(d.entry.packageName)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            } else {
                ConfirmDialog(
                    title     = stringResource(R.string.quarantine_dialog_restore_title, d.entry.label),
                    body      = stringResource(R.string.quarantine_dialog_restore_body_soft),
                    onConfirm = {
                        viewModel.restoreEntry(d.entry.packageName)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }
        }
        is QuarantineDialog.ConfirmDrop -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title   = { Text(stringResource(R.string.quarantine_dialog_drop_title, d.entry.label)) },
                text    = { Text(stringResource(R.string.quarantine_dialog_drop_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dropEntry(d.entry.packageName)
                        dialog = null
                    }) {
                        Text(stringResource(R.string.quarantine_dialog_drop_confirm), color = BrandDanger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
        is QuarantineDialog.BackupMissing -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                icon  = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = BrandDanger) },
                title = { Text(stringResource(R.string.quarantine_dialog_backup_missing_title)) },
                text  = { Text(stringResource(R.string.quarantine_dialog_backup_missing_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dropEntry(d.packageName)
                        dialog = null
                    }) {
                        Text(stringResource(R.string.quarantine_dialog_backup_missing_drop), color = BrandDanger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(stringResource(R.string.action_keep))
                    }
                },
            )
        }
        null -> Unit
    }
}

@Composable
private fun QuarantineList(
    items: List<QuarantineEntry>,
    onRestore: (QuarantineEntry) -> Unit,
    onDrop: (QuarantineEntry) -> Unit,
) {
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp), // breathing room above FAB
    ) {
        items(items = items, key = { it.packageName }) { entry ->
            QuarantineRow(
                entry        = entry,
                restoreLabel = dateFormat.format(Date(entry.restoreAt)),
                onRestore    = { onRestore(entry) },
                onDrop       = { onDrop(entry) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun QuarantineRow(
    entry: QuarantineEntry,
    restoreLabel: String,
    onRestore: () -> Unit,
    onDrop: () -> Unit,
) {
    val expired = entry.isExpired()

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = entry.packageName, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = entry.label,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    text     = entry.packageName,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = stringResource(
                        if (expired) R.string.quarantine_row_expired_on
                        else R.string.quarantine_row_restore_on,
                        restoreLabel,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (expired) BrandDanger
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ModeBadge(mode = entry.mode, expired = expired)
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
                Text(stringResource(R.string.quarantine_action_restore))
            }
            OutlinedButton(
                onClick  = onDrop,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = BrandDanger)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.quarantine_action_drop), color = BrandDanger)
            }
        }
    }
}

@Composable
private fun ModeBadge(mode: QuarantineMode, expired: Boolean) {
    val accent = when {
        expired                              -> BrandDanger
        mode == QuarantineMode.HARD_UNINSTALL -> MaterialTheme.colorScheme.primary
        else                                  -> MaterialTheme.colorScheme.tertiary
    }
    val labelRes = when {
        expired                              -> R.string.quarantine_badge_expired
        mode == QuarantineMode.HARD_UNINSTALL -> R.string.quarantine_badge_hard
        else                                  -> R.string.quarantine_badge_soft
    }
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text       = stringResource(labelRes),
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color      = accent,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private sealed interface QuarantineDialog : java.io.Serializable {
    data class ConfirmRestore(val entry: QuarantineEntry) : QuarantineDialog
    data class ConfirmDrop(val entry: QuarantineEntry) : QuarantineDialog
    data class BackupMissing(val packageName: String) : QuarantineDialog
}

private fun launchIntent(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Timber.w(it, "Failed to launch intent %s", intent) }
}
