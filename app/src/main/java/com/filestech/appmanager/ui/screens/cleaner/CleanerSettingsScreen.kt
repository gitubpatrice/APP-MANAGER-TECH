package com.filestech.appmanager.ui.screens.cleaner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.ExportFormat
import com.filestech.appmanager.domain.model.ScanInterval
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.dialogs.RadioPickerDialog
import com.filestech.appmanager.ui.components.settings.NavigationRow
import com.filestech.appmanager.ui.components.settings.SectionHeader

/**
 * Cleaner settings — scan cadence, cache threshold, rarely-used threshold,
 * export format. Mirrors the SMS Tech SettingsScreen rhythm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerSettingsScreen(
    onBack: () -> Unit,
    viewModel: CleanerSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()

    var scanDialog by rememberSaveable { mutableStateOf(false) }
    var thresholdDialog by rememberSaveable { mutableStateOf(false) }
    var rarelyDialog by rememberSaveable { mutableStateOf(false) }
    var exportDialog by rememberSaveable { mutableStateOf(false) }

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
                title = { BrandedTitle(stringResource(R.string.screen_cleaner_title)) },
            )
        },
    ) { innerPadding ->
        CleanerBody(
            innerPadding         = innerPadding,
            scanIntervalLabel    = scanIntervalLabel(settings.scanner.autoScanInterval),
            thresholdLabel       = thresholdLabel(settings.scanner.cacheThresholdMb),
            rarelyLabel          = rarelyLabel(settings.scanner.rarelyUsedThresholdDays),
            exportLabel          = exportFormatLabel(settings.scanner.exportFormat),
            onScanClick          = { scanDialog = true },
            onThresholdClick     = { thresholdDialog = true },
            onRarelyClick        = { rarelyDialog = true },
            onExportClick        = { exportDialog = true },
        )
    }

    if (scanDialog) {
        RadioPickerDialog(
            title    = stringResource(R.string.cleaner_scan_interval_title),
            options  = ScanInterval.entries,
            selected = settings.scanner.autoScanInterval,
            labelOf  = { scanIntervalLabel(it) },
            onSelect = viewModel::setScanInterval,
            onDismiss = { scanDialog = false },
        )
    }
    if (thresholdDialog) {
        RadioPickerDialog(
            title    = stringResource(R.string.cleaner_threshold_title),
            options  = THRESHOLD_OPTIONS,
            selected = settings.scanner.cacheThresholdMb,
            labelOf  = { thresholdLabel(it) },
            onSelect = viewModel::setCacheThresholdMb,
            onDismiss = { thresholdDialog = false },
        )
    }
    if (rarelyDialog) {
        RadioPickerDialog(
            title    = stringResource(R.string.cleaner_rarely_used_title),
            options  = RARELY_OPTIONS,
            selected = settings.scanner.rarelyUsedThresholdDays,
            labelOf  = { rarelyLabel(it) },
            onSelect = viewModel::setRarelyUsedThresholdDays,
            onDismiss = { rarelyDialog = false },
        )
    }
    if (exportDialog) {
        // v0.2.2 — PDF is a one-shot diagnostic action with its own SAF launcher
        // on the Export screen; it is intentionally NOT exposed as a persistable
        // default format here.
        RadioPickerDialog(
            title    = stringResource(R.string.cleaner_export_format_title),
            options  = listOf(ExportFormat.JSON, ExportFormat.CSV),
            selected = settings.scanner.exportFormat,
            labelOf  = { exportFormatLabel(it) },
            onSelect = viewModel::setExportFormat,
            onDismiss = { exportDialog = false },
        )
    }
}

@Composable
private fun CleanerBody(
    innerPadding: PaddingValues,
    scanIntervalLabel: String,
    thresholdLabel: String,
    rarelyLabel: String,
    exportLabel: String,
    onScanClick: () -> Unit,
    onThresholdClick: () -> Unit,
    onRarelyClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SectionHeader(stringResource(R.string.cleaner_section_scan))
        SettingsCard {
            NavigationRow(
                title       = stringResource(R.string.cleaner_scan_interval_title),
                description = stringResource(R.string.cleaner_scan_interval_desc),
                onClick     = onScanClick,
                currentValue = scanIntervalLabel,
            )
        }

        SectionHeader(stringResource(R.string.cleaner_section_threshold))
        SettingsCard {
            NavigationRow(
                title       = stringResource(R.string.cleaner_threshold_title),
                description = stringResource(R.string.cleaner_threshold_desc),
                onClick     = onThresholdClick,
                currentValue = thresholdLabel,
            )
            NavigationRow(
                title       = stringResource(R.string.cleaner_rarely_used_title),
                description = stringResource(R.string.cleaner_rarely_used_desc),
                onClick     = onRarelyClick,
                currentValue = rarelyLabel,
            )
        }

        SectionHeader(stringResource(R.string.cleaner_section_export))
        SettingsCard {
            NavigationRow(
                title       = stringResource(R.string.cleaner_export_format_title),
                description = stringResource(R.string.cleaner_export_format_desc),
                onClick     = onExportClick,
                currentValue = exportLabel,
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column { content() }
    }
}

@Composable
private fun scanIntervalLabel(interval: ScanInterval): String = stringResource(
    when (interval) {
        ScanInterval.OFF    -> R.string.scan_interval_off
        ScanInterval.DAILY  -> R.string.scan_interval_daily
        ScanInterval.WEEKLY -> R.string.scan_interval_weekly
    }
)

@Composable
private fun thresholdLabel(mb: Int): String =
    if (mb == 0) stringResource(R.string.cleaner_threshold_disabled)
    else stringResource(R.string.cleaner_threshold_value, mb)

@Composable
private fun rarelyLabel(days: Int): String =
    stringResource(R.string.cleaner_rarely_used_value, days)

@Composable
private fun exportFormatLabel(format: ExportFormat): String = stringResource(
    when (format) {
        ExportFormat.JSON -> R.string.export_format_json
        ExportFormat.CSV  -> R.string.export_format_csv
        // PDF is never offered as a persistable default in this picker, but the
        // enum is exhaustive — fall through to its label for safety.
        ExportFormat.PDF  -> R.string.export_format_pdf
    }
)

private val THRESHOLD_OPTIONS: List<Int> = listOf(0, 50, 100, 250, 500, 1000)
private val RARELY_OPTIONS: List<Int>    = listOf(7, 14, 30, 60, 90, 180)
