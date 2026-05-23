package com.filestech.appmanager.ui.screens.quarantine

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.dialogs.CriticalWarningDialog
import com.filestech.appmanager.ui.components.dialogs.QuarantineConfigDialog
import com.filestech.appmanager.ui.components.dialogs.SoftQuarantineActionDialog
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.domain.model.CriticalClassification
import timber.log.Timber

/**
 * Quarantine Picker — choose an app + configure mode + duration, then fire
 * the [com.filestech.appmanager.domain.usecase.QuarantineAppUseCase].
 *
 * On confirm:
 *  - HARD mode → launches the OS uninstall confirm intent; user must approve.
 *  - SOFT mode → launches OS App-info screen so user can disable manually.
 *
 * Both modes immediately persist the quarantine entry so the next time the
 * user opens the Quarantine list, the entry is there.
 *
 * Navigates back to the Quarantine list after a successful action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarantinePickerScreen(
    onBack: () -> Unit,
    viewModel: QuarantinePickerViewModel = hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val query by viewModel.search.collectAsStateWithLifecycle()
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedApp by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLabel by rememberSaveable { mutableStateOf<String?>(null) }
    // Intent is Parcelable not Serializable — plain `remember`; rotation drops
    // the dialog (entry is already persisted, user can re-trigger from list).
    var softQuarantinePending by remember { mutableStateOf<SoftPrompt?>(null) }
    var criticalConfirm by remember { mutableStateOf<PickerCriticalConfirm?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is QuarantinePickerViewModel.Event.QuarantineLaunched -> {
                    launchIntent(context, event.intent)
                    onBack()
                }
                is QuarantinePickerViewModel.Event.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
                QuarantinePickerViewModel.Event.NeedsBackupFolder ->
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.quarantine_error_needs_backup_folder),
                    )
                is QuarantinePickerViewModel.Event.SoftQuarantineCreated ->
                    softQuarantinePending = SoftPrompt(
                        intent       = event.intent,
                        label        = event.label,
                        durationDays = event.durationDays,
                    )
                is QuarantinePickerViewModel.Event.RequiresCriticalConfirmation ->
                    criticalConfirm = PickerCriticalConfirm(
                        packageName    = event.packageName,
                        label          = event.label,
                        classification = event.classification,
                        durationDays   = event.durationDays,
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
                title = { BrandedTitle(stringResource(R.string.screen_quarantine_picker_title)) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value         = query,
                onValueChange = viewModel::setSearch,
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder   = { Text(stringResource(R.string.quarantine_picker_search_placeholder)) },
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            HorizontalDivider()
            if (apps.isEmpty()) {
                EmptyState(
                    icon  = Icons.Outlined.Apps,
                    title = stringResource(R.string.quarantine_picker_empty_title),
                    body  = stringResource(R.string.quarantine_picker_empty_body),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = apps, key = { it.packageName }) { app ->
                        AppPickerRow(
                            app     = app,
                            onClick = {
                                selectedApp   = app.packageName
                                selectedLabel = app.label
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val pkg = selectedApp
    val lbl = selectedLabel
    if (pkg != null && lbl != null) {
        QuarantineConfigDialog(
            label     = lbl,
            isWorking = isWorking,
            onDismiss = {
                selectedApp = null
                selectedLabel = null
            },
            onConfirm = { mode, days ->
                viewModel.quarantine(pkg, mode, days)
                selectedApp = null
                selectedLabel = null
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
                onBack()
            },
            onLater = {
                softQuarantinePending = null
                onBack()
            },
        )
    }

    criticalConfirm?.let { confirm ->
        val actionLabel = stringResource(R.string.critical_action_quarantine_hard)
        CriticalWarningDialog(
            appLabel    = confirm.label,
            actionLabel = actionLabel,
            category    = confirm.classification.category,
            onConfirm   = {
                viewModel.quarantine(
                    packageName  = confirm.packageName,
                    mode         = com.filestech.appmanager.domain.model.QuarantineMode.HARD_UNINSTALL,
                    durationDays = confirm.durationDays,
                    bypassCriticalCheck = true,
                )
                criticalConfirm = null
            },
            onCancel = { criticalConfirm = null },
        )
    }
}

private data class SoftPrompt(
    val intent: Intent,
    val label: String,
    val durationDays: Int,
)

private data class PickerCriticalConfirm(
    val packageName: String,
    val label: String,
    val classification: CriticalClassification,
    val durationDays: Int,
)

@Composable
private fun AppPickerRow(app: AppInfo, onClick: () -> Unit) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
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
        }
    }
}

private fun launchIntent(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Timber.w(it, "QuarantinePicker: failed to launch intent %s", intent) }
}
