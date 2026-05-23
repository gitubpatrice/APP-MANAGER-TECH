package com.filestech.appmanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * This banner makes the silent failure visible — sizes shown as 0 are
 * accompanied by a one-tap "Grant" button.
 *
 * Use anywhere a list of sizes is rendered: AppListScreen header, Storage
 * screen empty state, AppDetail storage card.
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
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Outlined.Info,
                contentDescription = null,
                tint               = BrandBlue,
                modifier           = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = stringResource(R.string.usage_stats_warning_title),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = stringResource(R.string.usage_stats_warning_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onGrantClick) {
                Text(stringResource(R.string.usage_stats_grant_button))
            }
        }
    }
}
