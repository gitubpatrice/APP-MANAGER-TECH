package com.filestech.appmanager.ui.components.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.filestech.appmanager.R
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * Brand-red destructive dialog — for **destructive intent ONLY**: uninstall,
 * delete, force stop, clear data, disable system app.
 *
 * Confirm button paints BrandDanger; a warning icon prefixes the title so
 * the destructive intent is conveyed even before the user reads. Use
 * [ConfirmDialog] for any non-destructive confirmation.
 *
 * Cross-theme guarantee: BrandDanger is hard-coded here (not via
 * `colorScheme.error`) so the dialog reads identically under Material You,
 * dark theme, and the static brand palette.
 */
@Composable
fun DestructiveDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.dialog_confirm),
    dismissLabel: String = stringResource(R.string.dialog_cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = {
            Icon(
                imageVector       = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint               = BrandDanger,
            )
        },
        title            = { Text(title) },
        text             = { Text(body) },
        confirmButton    = {
            TextButton(
                onClick = onConfirm,
                colors  = ButtonDefaults.textButtonColors(contentColor = BrandDanger),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
