package com.filestech.appmanager.ui.components.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Centred empty-state placeholder: large icon + bold title + softer body.
 * Used by the App list when no apps match the filter, the Storage screen
 * before the first rescan, etc.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(72.dp),
        )
        Text(
            text     = title,
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text      = body,
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier  = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
