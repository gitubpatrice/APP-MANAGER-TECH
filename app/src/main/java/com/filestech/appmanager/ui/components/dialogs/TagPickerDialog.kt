package com.filestech.appmanager.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
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
import com.filestech.appmanager.domain.model.AppTag
import com.filestech.appmanager.ui.theme.BrandBlue

/**
 * v0.3.3 — Pick a user tag for an app, or clear the existing tag.
 *
 * UX:
 * - Radio list of the 5 [AppTag] presets + a "None" entry at the top
 *   (selecting None and confirming clears the tag).
 * - Initial selection mirrors the current assignment ([current]).
 * - Confirm button is always enabled (clearing is a valid action).
 * - Dismiss via back / scrim is non-destructive (no DataStore write).
 */
@Composable
fun TagPickerDialog(
    appLabel: String,
    current: AppTag?,
    onConfirm: (AppTag?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(stringResource(R.string.tag_picker_title, appLabel)) },
        text             = {
            androidx.compose.foundation.layout.Column {
                NoneRow(selected = selected == null, onSelect = { selected = null })
                AppTag.entries.forEach { tag ->
                    TagRow(
                        tag      = tag,
                        selected = selected == tag,
                        onSelect = { selected = tag },
                    )
                }
            }
        },
        confirmButton    = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.action_confirm), color = BrandBlue)
            }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun NoneRow(selected: Boolean, onSelect: () -> Unit) {
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
            text  = stringResource(R.string.tag_picker_none),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TagRow(tag: AppTag, selected: Boolean, onSelect: () -> Unit) {
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
            text  = stringResource(appTagLabelRes(tag)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * v0.3.3 — Centralised tag → string resource mapping. Reused by the dialog,
 * the AppListRow chip, and any future filter UI.
 */
fun appTagLabelRes(tag: AppTag): Int = when (tag) {
    AppTag.WORK   -> R.string.tag_label_work
    AppTag.FAMILY -> R.string.tag_label_family
    AppTag.GAME   -> R.string.tag_label_game
    AppTag.TOOLS  -> R.string.tag_label_tools
    AppTag.MEDIA  -> R.string.tag_label_media
}
