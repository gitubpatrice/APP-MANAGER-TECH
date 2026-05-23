package com.filestech.appmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.ui.theme.BrandBlue

/**
 * Reusable banner shown when `hasUsageStatsAccess() == false`.
 *
 * The OS-level `PACKAGE_USAGE_STATS` permission can only be granted by the
 * user from Settings → Special Apps → Usage access. Without it, every
 * `StorageStatsManager.queryStatsForUid` and every `UsageStatsManager`
 * call silently throws `SecurityException` and we fall back to zeros.
 *
 * v0.1.2 — layout switched from a single Row (Icon + Column[Title, Body] +
 * TextButton) to a vertical Column (Header row with Icon + Title, then Body
 * full-width, then a full-width Button). The previous Row layout was
 * unreadable in French: the long body text was squeezed into the ~120dp
 * remaining width after the TextButton, producing one-character-per-line
 * vertical text. The Column layout uses the full card width for both the
 * description and the action button.
 */
@Composable
fun UsageStatsAccessBanner(
    visible: Boolean,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors   = CardDefaults.cardColors(
            containerColor = BrandBlue.copy(alpha = 0.10f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Outlined.Info,
                    contentDescription = null,
                    tint               = BrandBlue,
                    modifier           = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text       = stringResource(R.string.usage_stats_warning_title),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text     = stringResource(R.string.usage_stats_warning_body),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick  = onGrantClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.usage_stats_grant_button))
            }
        }
    }
}
