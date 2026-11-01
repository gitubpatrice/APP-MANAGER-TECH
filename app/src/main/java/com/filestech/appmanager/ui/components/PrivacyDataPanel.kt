package com.filestech.appmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.PermissionCapability
import com.filestech.appmanager.domain.model.PrivacyScore
import com.filestech.appmanager.domain.model.PrivacyTier
import com.filestech.appmanager.domain.model.TrackerReport
import com.filestech.appmanager.ui.text.trackerCategoryLabel
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * Plain-language summary of "what this app does on your device".
 *
 * Phase X innovation — answers the question the user explicitly asked for:
 * *"a feature that details everything the app collects/uses, so people get
 * it instantly without effort"*.
 *
 * Layout:
 *  - Privacy score badge with tier colour
 *  - "This app can:" + bullet list of dangerous permissions in plain
 *    French/English (camera, location, contacts, SMS, etc.)
 *  - "Trackers detected" with category list, or "No known tracker detected"
 *
 * Designed to be readable in under 30 seconds, no jargon, no permission FQCN.
 */
@Composable
fun PrivacyDataPanel(
    privacyScore: PrivacyScore?,
    trackerReport: TrackerReport?,
    grantedPermissions: List<String>,
    requestedPermissions: List<String>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        // No title of its own: AppDetail heads it with the same "What this app does" section header,
        // as it does the Storage and Actions cards.
        Column(modifier = Modifier.padding(16.dp)) {
            // Privacy score badge
            if (privacyScore != null) {
                PrivacyScoreBadge(privacyScore)
                Spacer(modifier = Modifier.size(12.dp))
            }

            // What the app can do (clear-text permissions)
            val dangerousLabels = panelLines(requestedPermissions, grantedPermissions, privacyScore)
            if (dangerousLabels.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint               = BrandBlue,
                        modifier           = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text  = stringResource(R.string.appdetail_privacy_panel_no_dangerous),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text(
                    text       = stringResource(R.string.appdetail_privacy_panel_can),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.size(4.dp))
                dangerousLabels.forEach { (label, granted) ->
                    Row(
                        modifier          = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector        = if (granted) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint               = if (granted) BrandDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text  = "• $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (granted) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            // Trackers row. No report means the scan failed: say so rather than "no tracker".
            if (trackerReport == null) {
                Text(
                    text  = stringResource(R.string.appdetail_privacy_panel_trackers_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (trackerReport.isClean) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint               = BrandBlue,
                        modifier           = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text  = stringResource(R.string.appdetail_privacy_panel_trackers_clean),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint               = BrandDanger,
                        modifier           = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text       = pluralStringResource(
                            R.plurals.appdetail_privacy_panel_trackers_count, trackerReport.detectedTrackers.size,
                            trackerReport.detectedTrackers.size,
                        ),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                trackerReport.detectedTrackers.forEach { tracker ->
                    Text(
                        text     = "• ${tracker.name} (${trackerCategoryLabel(tracker.category)})",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 22.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyScoreBadge(score: PrivacyScore) {
    val (color, tierLabel) = when (score.tier) {
        PrivacyTier.GREEN  -> BrandBlue to stringResource(R.string.privacy_score_tier_green)
        PrivacyTier.YELLOW -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.privacy_score_tier_yellow)
        PrivacyTier.ORANGE -> MaterialTheme.colorScheme.secondary to stringResource(R.string.privacy_score_tier_orange)
        PrivacyTier.RED    -> BrandDanger to stringResource(R.string.privacy_score_tier_red)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color        = color.copy(alpha = 0.16f),
            contentColor = color,
            shape        = RoundedCornerShape(50),
        ) {
            Text(
                text       = "${score.value} / 100",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
        Column {
            Text(
                text  = stringResource(R.string.privacy_score_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text       = tierLabel,
                style      = MaterialTheme.typography.bodyMedium,
                color      = color,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Panel label of each [PermissionCapability]. */
// CyclomaticComplexMethod: one branch per capability, on purpose — an exhaustive `when` makes the compiler
// refuse a capability added without its label, which a map would not.
@Suppress("CyclomaticComplexMethod")
private fun capabilityLabelRes(capability: PermissionCapability): Int = when (capability) {
    PermissionCapability.LOCATION            -> R.string.perm_group_location
    PermissionCapability.LOCATION_APPROX     -> R.string.perm_group_location_approx
    PermissionCapability.LOCATION_BACKGROUND -> R.string.perm_group_location_background
    PermissionCapability.CAMERA              -> R.string.perm_group_camera
    PermissionCapability.MICROPHONE          -> R.string.perm_group_microphone
    PermissionCapability.CONTACTS            -> R.string.perm_group_contacts
    PermissionCapability.CALENDAR            -> R.string.perm_group_calendar
    PermissionCapability.SMS                 -> R.string.perm_group_sms
    PermissionCapability.CALL_LOG            -> R.string.perm_group_call_log
    PermissionCapability.PHONE               -> R.string.perm_group_phone
    PermissionCapability.HEALTH              -> R.string.perm_group_health
    PermissionCapability.SENSORS             -> R.string.perm_group_sensors
    PermissionCapability.ACTIVITY            -> R.string.perm_group_activity
    PermissionCapability.STORAGE             -> R.string.perm_group_storage
    PermissionCapability.MEDIA               -> R.string.perm_group_media
    PermissionCapability.MEDIA_LOCATION      -> R.string.perm_group_media_location
    PermissionCapability.BLUETOOTH           -> R.string.perm_group_bluetooth
    PermissionCapability.NEARBY              -> R.string.perm_group_nearby
    PermissionCapability.NOTIFICATIONS       -> R.string.perm_group_notifications
}

/**
 * Accesses that are not dangerous permissions but that the score counts, shown after them: every
 * deduction of the score has its line in the panel.
 */
private val SPECIAL_ACCESS_LABELS: List<Pair<String, Int>> = listOf(
    "android.permission.INTERNET"                to R.string.perm_group_internet,
    "android.permission.FOREGROUND_SERVICE"      to R.string.perm_group_foreground_service,
    "android.permission.SYSTEM_ALERT_WINDOW"     to R.string.perm_group_overlay,
    "android.permission.MANAGE_EXTERNAL_STORAGE" to R.string.perm_group_all_files,
)

/**
 * The panel lines, as (label, isGranted): one per [PermissionCapability] the requested permissions give,
 * in that enum's order (most sensitive first) — the same grouping the score takes its penalties from —
 * then the special accesses, then the accessibility service and device-admin receiver the score found
 * among the app's components. A capability is granted if any of its permissions is: an app refused the
 * photos but allowed the music has media access.
 */
@Composable
private fun panelLines(
    requested: List<String>,
    granted: List<String>,
    score: PrivacyScore?,
): List<Pair<String, Boolean>> {
    val grantedSet = granted.toSet()
    val byCapability = requested.groupBy { PermissionCapability.capabilityOf(it) }
    val lines = mutableListOf<Pair<String, Boolean>>()
    for (capability in PermissionCapability.entries) {
        val permissions = byCapability[capability] ?: continue
        lines += stringResource(capabilityLabelRes(capability)) to permissions.any { it in grantedSet }
    }
    for ((permission, labelRes) in SPECIAL_ACCESS_LABELS) {
        if (permission in requested) lines += stringResource(labelRes) to (permission in grantedSet)
    }
    // Declared, not necessarily enabled: shown as a capability, not as a grant.
    if (score?.declaresAccessibilityService == true) {
        lines += stringResource(R.string.perm_group_accessibility) to false
    }
    if (score?.declaresDeviceAdmin == true) lines += stringResource(R.string.perm_group_device_admin) to false
    return lines
}
