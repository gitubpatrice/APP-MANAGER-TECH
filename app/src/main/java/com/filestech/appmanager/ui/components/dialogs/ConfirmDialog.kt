package com.filestech.appmanager.ui.components.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.filestech.appmanager.R
import com.filestech.appmanager.ui.theme.BrandBlue

/**
 * Brand-blue confirmation dialog.
 *
 * Use for any **non-destructive** confirmation: "Apply changes?", "Reload?",
 * "Open Settings?". Confirm button paints BrandBlue.
 *
 * For destructive intent (uninstall, delete, force stop) use
 * [DestructiveDialog] instead — colour signals intent before the user reads.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.dialog_confirm),
    dismissLabel: String = stringResource(R.string.dialog_cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(title) },
        text             = { Text(body) },
        confirmButton    = {
            TextButton(
                onClick = onConfirm,
                colors  = ButtonDefaults.textButtonColors(contentColor = BrandBlue),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
