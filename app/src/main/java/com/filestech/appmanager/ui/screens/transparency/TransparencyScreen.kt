package com.filestech.appmanager.ui.screens.transparency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.settings.NavigationRow
import com.filestech.appmanager.ui.components.settings.SectionHeader
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.theme.BrandDanger
import timber.log.Timber

/**
 * Transparency screen — full disclosure of what App Manager Tech reads,
 * stores, and sends.
 *
 * **App Manager Tech innovation**: most apps bury their privacy story in a
 * 30-page PDF policy that no one reads. We make the contract explicit on a
 * dedicated screen with one-line bullet points and a clear hierarchy:
 *
 *   - What we READ from your device
 *   - What we STORE on your device
 *   - What we SEND off your device  ← always "Nothing"
 *   - Third-party SDKs embedded     ← always "Zero"
 *   - Open source proof
 *
 * Goal: a user can read the whole screen in 60 seconds and walk away
 * convinced the app does no telemetry. No legal jargon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransparencyScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Timber.w(it, "Failed to open URL %s", url) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                title = { BrandedTitle(stringResource(R.string.screen_transparency_title)) },
            )
        },
    ) { innerPadding ->
        TransparencyBody(innerPadding = innerPadding, onOpenUrl = openUrl)
    }
}

@Composable
private fun TransparencyBody(
    innerPadding: PaddingValues,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text     = stringResource(R.string.transparency_intro),
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )

        SectionHeader(stringResource(R.string.transparency_section_reads))
        StatementCard(icon = Icons.Outlined.Visibility, tint = BrandBlue) {
            BulletText(stringResource(R.string.transparency_reads_packages))
            BulletText(stringResource(R.string.transparency_reads_sizes))
        }

        SectionHeader(stringResource(R.string.transparency_section_stores))
        StatementCard(icon = Icons.Outlined.Folder, tint = BrandBlue) {
            BulletText(stringResource(R.string.transparency_stores_room))
            BulletText(stringResource(R.string.transparency_stores_prefs))
        }

        SectionHeader(stringResource(R.string.transparency_section_sends))
        StatementCard(
            icon  = Icons.Outlined.SearchOff,
            tint  = BrandBlue,
            highlight = true,
        ) {
            BulletText(
                text       = stringResource(R.string.transparency_sends_nothing),
                fontWeight = FontWeight.SemiBold,
            )
        }

        SectionHeader(stringResource(R.string.transparency_section_third_parties))
        StatementCard(
            icon  = Icons.Outlined.Block,
            tint  = BrandDanger,
            highlight = true,
        ) {
            BulletText(
                text       = stringResource(R.string.transparency_third_parties_none),
                fontWeight = FontWeight.SemiBold,
            )
        }

        SectionHeader(stringResource(R.string.transparency_section_open_source))
        StatementCard(icon = Icons.Outlined.VerifiedUser, tint = BrandBlue) {
            BulletText(stringResource(R.string.transparency_open_source_body))
        }

        SectionHeader(stringResource(R.string.about_section_links))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column {
                NavigationRow(
                    title       = stringResource(R.string.transparency_link_privacy),
                    leadingIcon = Icons.Outlined.Security,
                    onClick     = { onOpenUrl("https://github.com/gitubpatrice/APP-MANAGER-TECH/blob/main/PRIVACY.md") },
                )
                NavigationRow(
                    title       = stringResource(R.string.transparency_link_terms),
                    leadingIcon = Icons.Outlined.Security,
                    onClick     = { onOpenUrl("https://github.com/gitubpatrice/APP-MANAGER-TECH/blob/main/TERMS.md") },
                )
                NavigationRow(
                    title       = stringResource(R.string.transparency_link_security),
                    leadingIcon = Icons.Outlined.Security,
                    onClick     = { onOpenUrl("https://github.com/gitubpatrice/APP-MANAGER-TECH/blob/main/SECURITY.md") },
                )
                NavigationRow(
                    title       = stringResource(R.string.transparency_link_third_parties),
                    leadingIcon = Icons.Outlined.Security,
                    onClick     = { onOpenUrl("https://github.com/gitubpatrice/APP-MANAGER-TECH/blob/main/THIRD_PARTY_NOTICES.md") },
                )
            }
        }
    }
}

@Composable
private fun StatementCard(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (highlight) tint.copy(alpha = 0.08f)
                             else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = tint,
            )
            Column {
                content()
            }
        }
    }
}

@Composable
private fun BulletText(
    text: String,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text       = "• $text",
        style      = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight,
        modifier   = Modifier.padding(vertical = 2.dp),
    )
}
