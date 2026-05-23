package com.filestech.appmanager.ui.components.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Settings row with label, optional description, optional leading icon, and a
 * trailing chevron-right indicating it opens a sub-screen or dialog.
 *
 * `currentValue` (optional) displays the current selected value at the right
 * end before the chevron — useful for picker rows ("Theme: Dark") so users
 * see the state at a glance without opening the picker.
 */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
    titleColor: Color? = null,
    currentValue: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector        = leadingIcon,
                contentDescription = null,
                tint               = leadingIconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor ?: MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (currentValue != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text  = currentValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector        = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
