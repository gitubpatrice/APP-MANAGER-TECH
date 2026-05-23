package com.filestech.appmanager.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * Reusable Quarantine configuration dialog — mode picker (SOFT vs HARD) +
 * duration slider (1–90 days).
 *
 * Surfaces the destructive data-loss warning when HARD mode is selected.
 * The confirm button paints [BrandDanger] in HARD mode to reinforce that the
 * action is irreversible (the app data will be wiped at uninstall).
 *
 * Called from:
 *  - [com.filestech.appmanager.ui.screens.quarantine.QuarantinePickerScreen]
 *    after the user picks an app from the picker list.
 *  - [com.filestech.appmanager.ui.screens.appdetail.AppDetailScreen] from the
 *    "Mettre en quarantaine" action card row.
 *
 * Re-entrancy: [isWorking] disables the confirm button while the underlying
 * use case is in-flight to prevent double submission (HARD mode is
 * particularly sensitive — a double-tap would queue two uninstall intents).
 */
@Composable
fun QuarantineConfigDialog(
    label: String,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (mode: QuarantineMode, durationDays: Int) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(QuarantineMode.SOFT_REMINDER) }
    var days by rememberSaveable { mutableFloatStateOf(14f) }
    val durationDays = days.toInt().coerceIn(1, 365)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
        title = { Text(stringResource(R.string.quarantine_config_title, label)) },
        text  = {
            Column {
                Text(
                    text  = stringResource(R.string.quarantine_config_mode_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                ModeRadioRow(
                    label       = stringResource(R.string.quarantine_mode_soft),
                    description = stringResource(R.string.quarantine_mode_soft_desc),
                    selected    = mode == QuarantineMode.SOFT_REMINDER,
                    onSelect    = { mode = QuarantineMode.SOFT_REMINDER },
                )
                ModeRadioRow(
                    label       = stringResource(R.string.quarantine_mode_hard),
                    description = stringResource(R.string.quarantine_mode_hard_desc),
                    selected    = mode == QuarantineMode.HARD_UNINSTALL,
                    onSelect    = { mode = QuarantineMode.HARD_UNINSTALL },
                )
                if (mode == QuarantineMode.HARD_UNINSTALL) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint               = BrandDanger,
                            modifier           = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text  = stringResource(R.string.quarantine_hard_data_loss_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandDanger,
                        )
                    }
                }
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text  = stringResource(R.string.quarantine_config_duration_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text  = stringResource(R.string.quarantine_config_duration_value, durationDays),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value         = days,
                    onValueChange = { days = it },
                    valueRange    = 1f..90f,
                    steps         = 0,
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { onConfirm(mode, durationDays) },
                enabled  = !isWorking,
                colors   = if (mode == QuarantineMode.HARD_UNINSTALL) {
                    ButtonDefaults.buttonColors(containerColor = BrandDanger)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(stringResource(R.string.quarantine_config_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ModeRadioRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
