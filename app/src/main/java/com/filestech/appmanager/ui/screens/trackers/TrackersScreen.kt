package com.filestech.appmanager.ui.screens.trackers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.TrackerReport
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.settings.SectionHeader
import com.filestech.appmanager.ui.components.settings.ToggleRow
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * Trackers screen — bulk scan of every installed app's declared Android
 * components against a curated tracker signature database.
 *
 * App Manager Tech innovation: this is the **only F-Droid-only Android app
 * manager** that does Exodus-style tracker detection 100% locally.
 *
 * The screen renders:
 * - A summary card (apps scanned / apps with trackers / contamination % / category histogram)
 * - A "Apps containing trackers" section, sorted by tracker count desc
 * - A "Clean apps" collapsible section
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackersScreen(
    onBack: () -> Unit,
    viewModel: TrackersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TrackersViewModel.Event.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
                // v0.2.1 audit C3a — concrete refresh feedback (was missing).
                is TrackersViewModel.Event.ScanDone ->
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.trackers_scan_done,
                            event.appsScanned,
                            event.trackersFound,
                        ),
                    )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                title = { BrandedTitle(stringResource(R.string.screen_trackers_title)) },
                actions = {
                    IconButton(onClick = viewModel::rescan, enabled = !state.isScanning) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        TrackersBody(
            innerPadding   = innerPadding,
            state          = state,
            onToggleSystem = viewModel::toggleIncludeSystem,
        )
    }
}

@Composable
private fun TrackersBody(
    innerPadding: PaddingValues,
    state: TrackersViewModel.UiState,
    onToggleSystem: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        if (state.isScanning && state.result == null) {
            // Initial scan
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text  = stringResource(R.string.trackers_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Box
        }

        val result = state.result
        if (result == null || result.totalAppCount == 0) {
            EmptyState(
                icon  = Icons.Outlined.VerifiedUser,
                title = stringResource(R.string.trackers_empty_title),
                body  = stringResource(R.string.trackers_empty_body),
            )
            return@Box
        }

        val withTrackers = result.reports.filter { !it.isClean }
        val clean = result.reports.filter { it.isClean }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                SummaryCard(result = result, isScanning = state.isScanning)
            }
            item {
                ToggleRow(
                    title           = stringResource(R.string.filter_include_system_apps),
                    checked         = state.includeSystemApps,
                    onCheckedChange = onToggleSystem,
                    enabled         = !state.isScanning,
                )
            }

            if (withTrackers.isNotEmpty()) {
                item {
                    SectionHeader(
                        stringResource(R.string.trackers_section_with_trackers, withTrackers.size),
                    )
                }
                items(items = withTrackers, key = { it.packageName }) { report ->
                    TrackerRow(report = report, appLabel = result.apps[report.packageName]?.label ?: report.packageName)
                }
            }

            if (clean.isNotEmpty()) {
                item {
                    SectionHeader(
                        stringResource(R.string.trackers_section_clean, clean.size),
                    )
                }
                items(items = clean, key = { it.packageName }) { report ->
                    CleanRow(packageName = report.packageName, label = result.apps[report.packageName]?.label ?: report.packageName)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    result: com.filestech.appmanager.domain.usecase.ScanAllTrackersUseCase.Result,
    isScanning: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Big number — contamination %
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = "${result.contaminationPercent}%",
                    style      = MaterialTheme.typography.displayMedium,
                    color      = when {
                        result.contaminationPercent >= 50 -> BrandDanger
                        result.contaminationPercent >= 20 -> MaterialTheme.colorScheme.tertiary
                        else                              -> BrandBlue
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text       = stringResource(R.string.trackers_summary_contamination),
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text  = stringResource(
                            R.string.trackers_summary_subtitle,
                            result.appsWithTrackers,
                            result.totalAppCount,
                            result.totalDetections,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (result.categoryHistogram.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text  = stringResource(R.string.trackers_summary_categories),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                result.categoryHistogram.forEach { (category, count) ->
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text     = category,
                            style    = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text       = count.toString(),
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TrackerRow(report: TrackerReport, appLabel: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = report.packageName, size = 36.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = appLabel,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text     = report.detectedTrackers.joinToString(", ") { it.name },
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            shape        = RoundedCornerShape(50),
            color        = BrandDanger.copy(alpha = 0.12f),
            contentColor = BrandDanger,
        ) {
            Text(
                text     = report.detectedTrackers.size.toString(),
                style    = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CleanRow(packageName: String, label: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = packageName, size = 32.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = label,
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text     = packageName,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector        = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint               = BrandBlue,
            modifier           = Modifier.size(20.dp),
        )
    }
}

@Suppress("unused") private val _unused = Icons.Outlined.VisibilityOff
