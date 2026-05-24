package com.filestech.appmanager.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.AppTag
import com.filestech.appmanager.ui.theme.BrandBlue

/**
 * v0.3.3 / v0.3.4 — Pick the user-assigned tag set for an app.
 *
 * UX:
 * - Multi-select Checkbox list of the 5 [AppTag] presets.
 * - Initial selection mirrors the current assignment ([current]).
 * - "Aucun tag" footer label shows when no checkbox is ticked, so the user
 *   knows that confirming an empty selection clears the tag set.
 * - Confirm button is always enabled (clearing is a valid action).
 * - Dismiss via back / scrim is non-destructive (no DataStore write).
 *
 * v0.3.4 widens the data model from a single-tag radio to a multi-tag
 * checkbox so an app can carry several categories at once (e.g. WORK +
 * TOOLS). Passing an empty set on confirm clears every tag for the app.
 */
@Composable
fun TagPickerDialog(
    appLabel: String,
    current: Set<AppTag>,
    onConfirm: (Set<AppTag>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Saveable: persist a Set<AppTag> across config changes. Encoded as
    // List<String> (enum names) — Set + enum aren't Saveable by default
    // but the listSaver bridges them cleanly without a TypeConverter. We
    // hand the saver in as `stateSaver` (not `saver`) so it applies to the
    // Set<AppTag> *inside* the MutableState, not to the MutableState itself.
    var selected by rememberSaveable(current, stateSaver = appTagSetSaver()) {
        mutableStateOf(current.toSet())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(stringResource(R.string.tag_picker_title, appLabel)) },
        text             = {
            Column {
                AppTag.entries.forEach { tag ->
                    TagRow(
                        tag      = tag,
                        checked  = tag in selected,
                        onToggle = { isChecked ->
                            selected = if (isChecked) selected + tag else selected - tag
                        },
                    )
                }
                if (selected.isEmpty()) {
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        text  = stringResource(R.string.tag_picker_none),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
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
private fun TagRow(tag: AppTag, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .toggleable(
                value         = checked,
                onValueChange = onToggle,
                role          = Role.Checkbox,
            )
            .padding(vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // v0.3.4 audit MEDIUM-5 fix — `onCheckedChange = null` so the
        // parent Row's `toggleable` is the single source of click
        // handling. Without this, tapping the Checkbox could fire the
        // toggle twice on some Compose versions (Checkbox handles its
        // own tap AND the Row's toggleable propagates) — net effect:
        // double-toggle = no-op. The Checkbox stays visually clickable
        // because its parent Row drives the state; accessibility role
        // is set on the Row itself via `role = Role.Checkbox`.
        Checkbox(
            checked         = checked,
            onCheckedChange = null,
            colors          = CheckboxDefaults.colors(checkedColor = BrandBlue),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = stringResource(appTagLabelRes(tag)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Bridges `Set<AppTag>` to Saveable via the stable enum `name`. Unknown
 * names (downgrade, persisted tampering) are dropped on restore — the
 * picker simply opens with one less tick.
 */
private fun appTagSetSaver() = listSaver<Set<AppTag>, String>(
    save    = { set -> set.map { it.name } },
    restore = { names ->
        names.mapNotNull { name ->
            runCatching { AppTag.valueOf(name) }.getOrNull()
        }.toSet()
    },
)

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
