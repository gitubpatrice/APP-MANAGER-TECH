package com.filestech.appmanager.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * 3-way uninstall choice dialog — Cancel / Move-to-trash / Uninstall-now.
 *
 * Both forward actions are destructive intent (corbeille is *staged* uninstall),
 * so both confirm-style buttons paint [BrandDanger]. The trash route is the
 * **primary recommended** path (reviewable, recoverable) — it occupies the
 * Material 3 `confirmButton` slot (rightmost). The `dismissButton` slot holds
 * the secondary destructive path (Uninstall now) in the centre.
 *
 * **Material 3 deviation, intentional**: M3 AlertDialog assumes `dismissButton`
 * is a "safe" neutral cancel. Here the slot hosts a 2-button Row
 * (Cancel + Uninstall-now). This is the cleanest layout for a 3-action
 * destructive choice without bespoke layout. Visual hierarchy distinguishes
 * the three: Cancel = grey TextButton, Uninstall-now = BrandDanger + icon,
 * Move-to-trash = BrandDanger + icon (rightmost = primary affordance).
 *
 * Phase X — surfaces the "ou pouvoir faire les 2" choice the user explicitly
 * asked for (move to corbeille for review, OR uninstall immediately).
 */
@Composable
fun UninstallChoiceDialog(
    label: String,
    onCancel: () -> Unit,
    onMoveToTrash: () -> Unit,
    onUninstallNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon             = {
            Icon(
                imageVector        = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint               = BrandDanger,
            )
        },
        title            = { Text(stringResource(R.string.dialog_trash_choice_title, label)) },
        text             = { Text(stringResource(R.string.dialog_trash_choice_body)) },
        confirmButton    = {
            // Primary recommended path (rightmost per Material 3): move to trash —
            // reviewable & recoverable. Still BrandDanger because the *intent* is
            // destructive (the app will be uninstalled if the user later confirms
            // from the Trash screen).
            TextButton(
                onClick = onMoveToTrash,
                colors  = ButtonDefaults.textButtonColors(contentColor = BrandDanger),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text(stringResource(R.string.dialog_trash_choice_move))
                }
            }
        },
        dismissButton    = {
            // Cancel (neutral, leftmost) + Uninstall-now (centre, destructive).
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.dialog_cancel))
                }
                TextButton(
                    onClick = onUninstallNow,
                    colors  = ButtonDefaults.textButtonColors(contentColor = BrandDanger),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                        Text(stringResource(R.string.dialog_trash_choice_now))
                    }
                }
            }
        },
    )
}
