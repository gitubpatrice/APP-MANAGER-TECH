package com.filestech.appmanager.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import timber.log.Timber
import com.filestech.appmanager.BuildConfig
import com.filestech.appmanager.R
import com.filestech.appmanager.ui.components.settings.NavigationRow
import com.filestech.appmanager.ui.components.settings.SectionHeader

/**
 * About screen — mirrors the SMS Tech `AboutScreen` structure: logo + app
 * name + version + description + links + licence + permissions narrative.
 *
 * All links are non-clickable placeholders for Phase V; Phase VIII can wire
 * them to actual URLs (GitHub repo, issue tracker) via `Intent.ACTION_VIEW`
 * once the public URLs are confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    /**
     * Pop-back callback. `null` when the screen is rendered as a tab content
     * inside the HomeShell bottom navigation (v0.1.3) — the back arrow then
     * disappears because there is nothing to pop. Non-null when navigated to
     * from Settings → "About App Manager Tech" or from the Tools grid.
     */
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    }
                },
                title = { Text(stringResource(R.string.screen_about_title)) },
            )
        },
    ) { innerPadding ->
        AboutBody(innerPadding)
    }
}

@Composable
private fun AboutBody(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val releasesUrl = stringResource(R.string.about_url_releases)
    val sourceUrl = stringResource(R.string.about_url_source_code)
    val issueUrl  = stringResource(R.string.about_url_report_issue)
    val websiteUrl = stringResource(R.string.about_url_website)
    val licenceUrl = stringResource(R.string.about_url_licence)
    // One resource per language, as in Agenda Tech: each language opens its own PRIVACY.xx.md and
    // TERMS.xx.md. tools/check-translations.py fails the build if one of those files does not exist.
    val privacyUrl = stringResource(R.string.about_url_privacy)
    val termsUrl   = stringResource(R.string.about_url_terms)

    val openUrl: (String) -> Unit = { url ->
        // v0.1.3 audit S-1 fix — defence in depth scheme whitelist. URLs come
        // from hardcoded strings.xml entries so the risk is low, but a future
        // overlay / accidental string edit could inject `javascript:`,
        // `intent:`, `content:`, etc. Reject anything that isn't http(s).
        if (url.startsWith("https://") || url.startsWith("http://")) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { Timber.w(it, "Failed to open URL %s", url) }
        } else {
            Timber.w("Refused to open non-http(s) URL: %s", url)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Logo + name + version + tagline
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter            = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = stringResource(R.string.cd_app_logo),
                modifier           = Modifier.size(96.dp),
            )
            Text(
                text  = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text  = "${stringResource(R.string.about_version_label)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The app has no INTERNET permission (asserted by CI on the release APK): it cannot look for
            // a new version itself. The button opens the releases page in the browser, which is what
            // the line below it says.
            Button(
                onClick  = { openUrl(releasesUrl) },
                colors   = ButtonDefaults.buttonColors(containerColor = LogoBlue, contentColor = Color.White),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text     = stringResource(R.string.about_check_updates),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text      = stringResource(R.string.about_check_updates_hint),
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(top = 8.dp, bottom = 12.dp, start = 24.dp, end = 24.dp),
            )
            Text(
                text  = stringResource(R.string.about_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        SectionHeader(stringResource(R.string.about_section_about))
        AboutCard {
            Text(
                text     = stringResource(R.string.about_description),
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        SectionHeader(stringResource(R.string.about_section_links))
        AboutCard {
            NavigationRow(
                title       = stringResource(R.string.about_link_source_code),
                leadingIcon = Icons.Outlined.Code,
                onClick     = { openUrl(sourceUrl) },
            )
            NavigationRow(
                title       = stringResource(R.string.about_link_report_issue),
                leadingIcon = Icons.Outlined.BugReport,
                onClick     = { openUrl(issueUrl) },
            )
            NavigationRow(
                title       = stringResource(R.string.about_link_website),
                leadingIcon = Icons.Outlined.Language,
                onClick     = { openUrl(websiteUrl) },
            )
        }

        SectionHeader(stringResource(R.string.about_section_legal))
        AboutCard {
            NavigationRow(
                title       = stringResource(R.string.about_link_privacy),
                leadingIcon = Icons.Outlined.PrivacyTip,
                onClick     = { openUrl(privacyUrl) },
            )
            NavigationRow(
                title       = stringResource(R.string.about_link_terms),
                leadingIcon = Icons.Outlined.Description,
                onClick     = { openUrl(termsUrl) },
            )
            NavigationRow(
                title       = stringResource(R.string.about_link_licence),
                description = stringResource(R.string.about_link_licence_desc),
                leadingIcon = Icons.Outlined.Gavel,
                onClick     = { openUrl(licenceUrl) },
            )
        }

        SectionHeader(stringResource(R.string.about_section_permissions))
        AboutCard {
            Text(
                text     = stringResource(R.string.about_permissions_text),
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        Text(
            text     = stringResource(R.string.about_made_in),
            style    = MaterialTheme.typography.labelMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text      = stringResource(R.string.about_legal_body, COPYRIGHT_YEAR, AUTHOR_NAME),
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
        )
    }
}

/** The blue of the logo's checkerboard (`drawable/ic_app_logo`), for the update button. */
private val LogoBlue = Color(0xFF0D6EFD)

// The publisher named in PRIVACY.md and TERMS.md, as in Agenda Tech's About screen.
private const val AUTHOR_NAME = "Patrice Haltaya"
private const val COPYRIGHT_YEAR = "2026"

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    // v0.1.2 — ElevatedCard for the premium drop-shadow RFT-style look,
    // mirrors SettingsCard / OverviewCard / ToolCard.
    ElevatedCard(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column { content() }
    }
}
