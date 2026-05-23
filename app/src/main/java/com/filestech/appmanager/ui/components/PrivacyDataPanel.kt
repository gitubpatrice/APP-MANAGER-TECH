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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.PrivacyScore
import com.filestech.appmanager.domain.model.PrivacyTier
import com.filestech.appmanager.domain.model.TrackerReport
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = stringResource(R.string.appdetail_privacy_panel_title),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.size(12.dp))

            // Privacy score badge
            if (privacyScore != null) {
                PrivacyScoreBadge(privacyScore)
                Spacer(modifier = Modifier.size(12.dp))
            }

            // What the app can do (clear-text permissions)
            val dangerousLabels = mapDangerousPermissions(requestedPermissions, grantedPermissions)
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
                            color = if (granted) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            // Trackers row
            if (trackerReport == null || trackerReport.isClean) {
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
                        text       = stringResource(
                            R.string.appdetail_privacy_panel_trackers_count,
                            trackerReport.detectedTrackers.size,
                        ),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                trackerReport.detectedTrackers.forEach { tracker ->
                    Text(
                        text     = "• ${tracker.name} (${tracker.category})",
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

/**
 * Map FQCN permissions to plain-language labels. Returns pairs of
 * (humanLabel, isGranted) for each dangerous permission requested, in
 * a stable order (most sensitive first).
 *
 * The full list of mapped permissions matches the perm_group_* strings
 * defined in res/values/strings.xml. Unknown permissions are skipped —
 * they show up in the standard "declared permissions" list elsewhere.
 */
/**
 * Ordered table of (Android permission FQCN → human-label string resource).
 * Used by [mapDangerousPermissions] — declared at top level so the
 * @Composable mapping function stays a clean for-loop.
 */
private val DANGEROUS_PERMISSION_LABELS: List<Pair<String, Int>> = listOf(
    "android.permission.ACCESS_FINE_LOCATION"          to R.string.perm_group_location,
    "android.permission.ACCESS_COARSE_LOCATION"        to R.string.perm_group_location_approx,
    "android.permission.ACCESS_BACKGROUND_LOCATION"    to R.string.perm_group_location_background,
    "android.permission.CAMERA"                        to R.string.perm_group_camera,
    "android.permission.RECORD_AUDIO"                  to R.string.perm_group_microphone,
    "android.permission.READ_CONTACTS"                 to R.string.perm_group_contacts,
    "android.permission.WRITE_CONTACTS"                to R.string.perm_group_contacts,
    "android.permission.READ_CALENDAR"                 to R.string.perm_group_calendar,
    "android.permission.READ_SMS"                      to R.string.perm_group_sms,
    "android.permission.SEND_SMS"                      to R.string.perm_group_sms,
    "android.permission.READ_CALL_LOG"                 to R.string.perm_group_call_log,
    "android.permission.READ_PHONE_STATE"              to R.string.perm_group_phone,
    "android.permission.READ_EXTERNAL_STORAGE"         to R.string.perm_group_storage,
    "android.permission.WRITE_EXTERNAL_STORAGE"        to R.string.perm_group_storage,
    "android.permission.READ_MEDIA_IMAGES"             to R.string.perm_group_media,
    "android.permission.READ_MEDIA_VIDEO"              to R.string.perm_group_media,
    "android.permission.READ_MEDIA_AUDIO"              to R.string.perm_group_media,
    "android.permission.ACTIVITY_RECOGNITION"          to R.string.perm_group_activity,
    "android.permission.BODY_SENSORS"                  to R.string.perm_group_sensors,
    "android.permission.BLUETOOTH_CONNECT"             to R.string.perm_group_bluetooth,
    "android.permission.POST_NOTIFICATIONS"            to R.string.perm_group_notifications,
    "android.permission.INTERNET"                      to R.string.perm_group_internet,
    "android.permission.SYSTEM_ALERT_WINDOW"           to R.string.perm_group_overlay,
    "android.permission.BIND_DEVICE_ADMIN"             to R.string.perm_group_device_admin,
    "android.permission.BIND_ACCESSIBILITY_SERVICE"    to R.string.perm_group_accessibility,
)

/**
 * Map FQCN permissions to plain-language labels. Returns pairs of
 * (humanLabel, isGranted) for each dangerous permission requested, in
 * a stable order (most sensitive first). Deduplicated by label.
 */
@Composable
private fun mapDangerousPermissions(
    requested: List<String>,
    granted: List<String>,
): List<Pair<String, Boolean>> {
    val requestedSet = requested.toSet()
    val grantedSet = granted.toSet()
    val seenLabels = mutableSetOf<String>()
    val pairs = mutableListOf<Pair<String, Boolean>>()
    for ((permission, labelRes) in DANGEROUS_PERMISSION_LABELS) {
        if (permission !in requestedSet) continue
        val label = stringResource(labelRes)
        if (label in seenLabels) continue
        seenLabels += label
        pairs += label to (permission in grantedSet)
    }
    return pairs
}

@Suppress("unused") private val _unused: Color = BrandBlue
