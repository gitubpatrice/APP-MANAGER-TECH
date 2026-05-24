package com.filestech.appmanager.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.UninstallReason
import com.filestech.appmanager.ui.theme.BrandBlue

/**
 * v0.3.0 — Optional dialog shown right after an `UNINSTALLED` lifecycle event
 * is recorded. Lets the user attach a reason category to the row.
 *
 * UX choices:
 *  - 5 fixed-choice radio options — no free text input in v0.3.0 (avoids
 *    shipping a text field that users would rarely fill).
 *  - Confirm wires through [onConfirm]; "Plus tard" (Later) dismisses without
 *    setting a reason → the event row keeps `user_reason = null`.
 *  - Dismiss-on-tap-outside / back hardware is allowed (default AlertDialog) —
 *    not coercive UX.
 */
@Composable
fun UninstallReasonDialog(
    appLabel: String,
    onConfirm: (UninstallReason) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<UninstallReason?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(stringResource(R.string.uninstall_reason_title, appLabel)) },
        text             = {
            Column {
                Text(
                    text  = stringResource(R.string.uninstall_reason_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                UninstallReason.entries.forEach { reason ->
                    ReasonRow(
                        reason   = reason,
                        selected = reason == selected,
                        onSelect = { selected = reason },
                    )
                }
            }
        },
        confirmButton    = {
            TextButton(
                onClick = { selected?.let(onConfirm) ?: onDismiss() },
                enabled = selected != null,
            ) {
                Text(stringResource(R.string.action_confirm), color = BrandBlue)
            }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.uninstall_reason_later))
            }
        },
    )
}

@Composable
private fun ReasonRow(
    reason: UninstallReason,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(
            selected = selected,
            onClick  = onSelect,
            colors   = RadioButtonDefaults.colors(selectedColor = BrandBlue),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = stringResource(reasonStringRes(reason)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun reasonStringRes(reason: UninstallReason): Int = when (reason) {
    UninstallReason.UNUSED              -> R.string.lifecycle_reason_unused
    UninstallReason.REPLACED_BY_ANOTHER -> R.string.lifecycle_reason_replaced_by_another
    UninstallReason.TOO_HEAVY           -> R.string.lifecycle_reason_too_heavy
    UninstallReason.PRIVACY_TRACKER     -> R.string.lifecycle_reason_privacy_tracker
    UninstallReason.OTHER               -> R.string.lifecycle_reason_other
}
