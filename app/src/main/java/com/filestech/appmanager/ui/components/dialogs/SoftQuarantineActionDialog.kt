package com.filestech.appmanager.ui.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R

/**
 * SOFT-mode quarantine post-confirm dialog — explains the user what to do
 * next, then offers to either deep-link them to Android Settings (recommended)
 * or dismiss and let them act later.
 *
 * The SOFT mode by design cannot disable the app itself (non-root Android
 * limit). Without this dialog, users land on the OS App-info screen with a
 * row of OS buttons (Ouvrir / Archiver / Désactiver / Forcer l'arrêt) and
 * have no context about which one to tap — surfaced as the v0.2.0 UX issue
 * "je tombe sur les Infos de l'application, je dois faire quoi ?".
 *
 * Used by every consumer of [com.filestech.appmanager.domain.usecase.QuarantineAppUseCase.Result.SoftReady]
 * (AppDetailScreen + QuarantinePickerScreen) so the explanatory step is
 * uniform across the app.
 */
@Composable
fun SoftQuarantineActionDialog(
    label: String,
    durationDays: Int,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        icon  = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
        title = { Text(stringResource(R.string.quarantine_soft_action_title, label)) },
        text  = {
            Column {
                Text(
                    text  = stringResource(R.string.quarantine_soft_action_body_summary, durationDays),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text  = stringResource(R.string.quarantine_soft_action_body_step1),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text  = stringResource(R.string.quarantine_soft_action_body_step2),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text  = stringResource(R.string.quarantine_soft_action_body_step3),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.quarantine_soft_action_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.quarantine_soft_action_later))
            }
        },
    )
}
