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
import androidx.compose.material.icons.outlined.Shop
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
 * them to actual URLs (GitHub repo, F-Droid page) via `Intent.ACTION_VIEW`
 * once the public URLs are confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
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
    val sourceUrl = stringResource(R.string.about_url_source_code)
    val fdroidUrl = stringResource(R.string.about_url_fdroid)
    val issueUrl  = stringResource(R.string.about_url_report_issue)

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Timber.w(it, "Failed to open URL %s", url) }
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
                title       = stringResource(R.string.about_link_fdroid),
                leadingIcon = Icons.Outlined.Shop,
                onClick     = { openUrl(fdroidUrl) },
            )
            NavigationRow(
                title       = stringResource(R.string.about_link_report_issue),
                leadingIcon = Icons.Outlined.BugReport,
                onClick     = { openUrl(issueUrl) },
            )
        }

        SectionHeader(stringResource(R.string.about_section_licence))
        AboutCard {
            Text(
                text     = stringResource(R.string.about_licence_text),
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
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
                .padding(top = 24.dp, bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

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
