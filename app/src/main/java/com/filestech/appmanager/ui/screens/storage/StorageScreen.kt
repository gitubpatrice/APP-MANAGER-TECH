package com.filestech.appmanager.ui.screens.storage

import android.text.format.Formatter
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppCategory
import com.filestech.appmanager.domain.model.StorageReport
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.settings.SectionHeader
import com.filestech.appmanager.ui.components.settings.ToggleRow
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.components.state.ErrorState
import com.filestech.appmanager.ui.components.state.LoadingState
import com.filestech.appmanager.ui.theme.BrandBlue

/**
 * Storage analyser — aggregate stats + top-N apps + per-category breakdown.
 *
 * The data is read from the Room cache (fast), so this screen never blocks
 * on PackageManager. A pull-style "Rescan device" button at the bottom of
 * the card lets users force a fresh scan if they just installed/uninstalled
 * something significant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StorageViewModel.Event.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar       = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                title = { BrandedTitle(stringResource(R.string.screen_storage_title)) },
                actions = {
                    IconButton(onClick = viewModel::rescanAndAnalyze, enabled = !state.isRescanning) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        StorageBody(
            state         = state,
            innerPadding  = innerPadding,
            onToggleSystem = viewModel::toggleIncludeSystemApps,
            onRescan      = viewModel::rescanAndAnalyze,
            onRetry       = viewModel::analyzeAllApps,
        )
    }
}

@Composable
private fun StorageBody(
    state: StorageViewModel.UiState,
    innerPadding: PaddingValues,
    onToggleSystem: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        when (val o = state.reportOutcome) {
            Outcome.Loading -> LoadingState()
            is Outcome.Failure -> ErrorState(message = o.error.toString(), onRetry = onRetry)
            is Outcome.Success -> {
                if (o.value.totalAppCount == 0) {
                    EmptyState(
                        icon  = Icons.Outlined.Apps,
                        title = stringResource(R.string.storage_empty_title),
                        body  = stringResource(R.string.storage_empty_body),
                    )
                } else {
                    StorageReportContent(
                        report           = o.value,
                        includeSystemApps = state.includeSystemApps,
                        onToggleSystem   = onToggleSystem,
                        onRescan         = onRescan,
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageReportContent(
    report: StorageReport,
    includeSystemApps: Boolean,
    onToggleSystem: (Boolean) -> Unit,
    onRescan: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SectionCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text     = stringResource(R.string.storage_subtitle, report.totalAppCount),
                    style    = MaterialTheme.typography.titleMedium,
                    color    = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TotalsRow(
                    label = stringResource(R.string.storage_total_install),
                    bytes = report.totalInstallBytes,
                    color = BrandBlue,
                )
                TotalsRow(
                    label = stringResource(R.string.storage_total_data),
                    bytes = report.totalDataBytes,
                    color = MaterialTheme.colorScheme.secondary,
                )
                TotalsRow(
                    label = stringResource(R.string.storage_total_cache),
                    bytes = report.totalCacheBytes,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                TotalsRow(
                    label = stringResource(R.string.storage_total_grand),
                    bytes = report.grandTotalBytes,
                    color = MaterialTheme.colorScheme.primary,
                    bold  = true,
                )
            }
        }

        ToggleRow(
            title           = stringResource(R.string.filter_include_system_apps),
            checked         = includeSystemApps,
            onCheckedChange = onToggleSystem,
        )

        if (report.perCategory.isNotEmpty()) {
            SectionHeader(stringResource(R.string.storage_per_category))
            SectionCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    report.perCategory.forEach { stats ->
                        CategoryProgressRow(stats = stats, totalBytes = report.grandTotalBytes)
                    }
                }
            }
        }

        if (report.topByTotalSize.isNotEmpty()) {
            SectionHeader(stringResource(R.string.storage_top_by_total))
            SectionCard {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    report.topByTotalSize.forEach { entry ->
                        TopAppRow(entry = entry, useCache = false)
                    }
                }
            }
        }

        if (report.topByCacheSize.isNotEmpty()) {
            SectionHeader(stringResource(R.string.storage_top_by_cache))
            SectionCard {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    report.topByCacheSize.forEach { entry ->
                        TopAppRow(entry = entry, useCache = true)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick  = onRescan,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.storage_rescan_button))
        }
        // Hint kotlin context is used (formatter callers above grab it via LocalContext).
        @Suppress("UNUSED_EXPRESSION") context
    }
}

@Composable
private fun TotalsRow(label: String, bytes: Long, color: Color, bold: Boolean = false) {
    val context = LocalContext.current
    val value = remember(bytes) { Formatter.formatShortFileSize(context, bytes) }
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(12.dp), color = color, shape = RoundedCornerShape(50)) {}
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CategoryProgressRow(stats: StorageReport.CategoryStats, totalBytes: Long) {
    val context = LocalContext.current
    val sizeLabel = remember(stats.totalBytes) { Formatter.formatShortFileSize(context, stats.totalBytes) }
    val progress = if (totalBytes <= 0L) 0f else (stats.totalBytes.toFloat() / totalBytes.toFloat())
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(
                text     = categoryLabel(stats.category),
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text  = "$sizeLabel (${stats.count})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TopAppRow(entry: StorageReport.AppFootprint, useCache: Boolean) {
    val context = LocalContext.current
    val displayBytes = if (useCache) entry.cacheBytes else entry.totalBytes
    val sizeLabel = remember(displayBytes) { Formatter.formatShortFileSize(context, displayBytes) }
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = entry.label,
                style    = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text     = entry.packageName,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text  = sizeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (useCache) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun categoryLabel(category: AppCategory): String = stringResource(
    when (category) {
        AppCategory.GAMES         -> R.string.category_games
        AppCategory.AUDIO         -> R.string.category_audio
        AppCategory.VIDEO         -> R.string.category_video
        AppCategory.IMAGE         -> R.string.category_image
        AppCategory.SOCIAL        -> R.string.category_social
        AppCategory.NEWS          -> R.string.category_news
        AppCategory.MAPS          -> R.string.category_maps
        AppCategory.PRODUCTIVITY  -> R.string.category_productivity
        AppCategory.ACCESSIBILITY -> R.string.category_accessibility
        AppCategory.OTHER         -> R.string.category_other
        AppCategory.UNDEFINED     -> R.string.category_undefined
    }
)

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        content()
    }
}
