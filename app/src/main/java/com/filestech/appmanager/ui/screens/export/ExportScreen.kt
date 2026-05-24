package com.filestech.appmanager.ui.screens.export

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.ExportFormat
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.dialogs.RadioPickerDialog
import com.filestech.appmanager.ui.components.settings.NavigationRow
import com.filestech.appmanager.ui.components.settings.SectionHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export Screen — pick format, then launch SAF `CreateDocument` to obtain a
 * user-chosen destination Uri. The Storage Access Framework is the F-Droid-
 * friendly path: no MANAGE_EXTERNAL_STORAGE, full user control of the
 * destination, OS-managed permission scope per Uri.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val format by viewModel.preferredFormat.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var formatDialog by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeOf(format)),
    ) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    // v0.2.2 — dedicated launcher for the PDF diagnostic. SAF needs the MIME
    // pinned per launcher; we keep a separate instance instead of recreating
    // the JSON/CSV one based on a mutable mode.
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) viewModel.exportDiagnosticPdf(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExportViewModel.Event.Done ->
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.export_success,
                            event.report.appCount,
                            Formatter.formatShortFileSize(context, event.report.bytesWritten),
                        ),
                    )
                is ExportViewModel.Event.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
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
                title = { BrandedTitle(stringResource(R.string.screen_export_title)) },
            )
        },
    ) { innerPadding ->
        ExportBody(
            innerPadding = innerPadding,
            format       = format,
            isExporting  = state.isExporting,
            onFormatClick = { formatDialog = true },
            onExportClick = { launcher.launch(suggestedFileName(format)) },
            onExportPdfClick = { pdfLauncher.launch(suggestedPdfFileName()) },
        )
    }

    if (formatDialog) {
        // PDF is exposed via the dedicated diagnostic button below the JSON/CSV
        // export; keeping the format picker scoped to JSON/CSV avoids confusing
        // users (the PDF flow has a different payload and a different SAF MIME).
        RadioPickerDialog(
            title    = stringResource(R.string.cleaner_export_format_title),
            options  = listOf(ExportFormat.JSON, ExportFormat.CSV),
            selected = format,
            labelOf  = { exportFormatLabel(it) },
            onSelect = viewModel::setFormat,
            onDismiss = { formatDialog = false },
        )
    }
}

@Composable
private fun ExportBody(
    innerPadding: PaddingValues,
    format: ExportFormat,
    isExporting: Boolean,
    onFormatClick: () -> Unit,
    onExportClick: () -> Unit,
    onExportPdfClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Text(
                text     = stringResource(R.string.export_description),
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }

        SectionHeader(stringResource(R.string.cleaner_section_export))
        Card(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column {
                NavigationRow(
                    title        = stringResource(R.string.cleaner_export_format_title),
                    onClick      = onFormatClick,
                    currentValue = exportFormatLabel(format),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick  = onExportClick,
                enabled  = !isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.FileDownload,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.export_action))
            }
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
        }

        // v0.2.2 — Diagnostic PDF (separate section + button to avoid confusion
        // with the lightweight JSON/CSV inventory above).
        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.export_pdf_section_export))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Text(
                text     = stringResource(R.string.export_pdf_description),
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick  = onExportPdfClick,
                enabled  = !isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.export_action_pdf))
            }
        }
    }
}

@Composable
private fun exportFormatLabel(format: ExportFormat): String = stringResource(
    when (format) {
        ExportFormat.JSON -> R.string.export_format_json
        ExportFormat.CSV  -> R.string.export_format_csv
        // PDF is never exposed via the JSON/CSV picker, but the enum is
        // exhaustive so we provide a fallback label for safety.
        ExportFormat.PDF  -> R.string.export_format_pdf
    }
)

private fun mimeOf(format: ExportFormat): String = when (format) {
    ExportFormat.JSON -> "application/json"
    ExportFormat.CSV  -> "text/csv"
    // The PDF flow uses its own SAF launcher pinned to "application/pdf";
    // the JSON/CSV launcher would never be invoked with PDF. Defensive default
    // so the function stays total over the enum.
    ExportFormat.PDF  -> "application/pdf"
}

private fun suggestedFileName(format: ExportFormat): String {
    val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())
    val ext = when (format) {
        ExportFormat.JSON -> "json"
        ExportFormat.CSV  -> "csv"
        ExportFormat.PDF  -> "pdf"
    }
    return "app_manager_tech_$ts.$ext"
}

private fun suggestedPdfFileName(): String {
    val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())
    return "app_manager_tech_diagnostic_$ts.pdf"
}

