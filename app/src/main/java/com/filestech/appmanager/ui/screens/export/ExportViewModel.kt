package com.filestech.appmanager.ui.screens.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.model.ExportFormat
import com.filestech.appmanager.domain.model.ExportReport
import com.filestech.appmanager.domain.usecase.ExportReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Export Screen — picks a format, hands a SAF Uri to the use case.
 *
 * The format is bound to the user's persisted preference (Scanner.exportFormat)
 * so toggling here also remembers for next time.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val exportReport: ExportReportUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val preferredFormat: StateFlow<ExportFormat> = settings.flow
        .map { it.scanner.exportFormat }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = ExportFormat.JSON,
        )

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    fun setFormat(format: ExportFormat) = viewModelScope.launch {
        settings.update { copy(scanner = scanner.copy(exportFormat = format)) }
    }

    fun export(uri: Uri, includeSystemApps: Boolean = false) {
        _state.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val format = preferredFormat.value
            // The JSON/CSV launcher must never produce a PDF Uri. If the
            // persisted preference somehow drifted to PDF (legacy migration,
            // race during in-flight format change), coerce back to JSON to
            // keep the lightweight backup pipeline reachable.
            val safeFormat = if (format == ExportFormat.PDF) ExportFormat.JSON else format
            when (val outcome = exportReport(uri, safeFormat, includeSystemApps)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isExporting = false, lastReport = outcome.value) }
                    _events.trySend(Event.Done(outcome.value))
                }
                is Outcome.Failure -> {
                    _state.update { it.copy(isExporting = false) }
                    _events.trySend(Event.ShowError(outcome.error.toString()))
                }
                Outcome.Loading -> Unit
            }
        }
    }

    /**
     * v0.2.2 — Streams the Diagnostic PDF to a user-picked SAF Uri. Bypasses
     * the JSON/CSV format preference because the PDF SAF launcher pins MIME =
     * `application/pdf` and the export pipeline is fundamentally different
     * (uses [BuildDiagnosticReportUseCase] under the hood).
     */
    fun exportDiagnosticPdf(uri: Uri) {
        _state.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            when (val outcome = exportReport(uri, ExportFormat.PDF, includeSystemApps = false)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isExporting = false, lastReport = outcome.value) }
                    _events.trySend(Event.Done(outcome.value))
                }
                is Outcome.Failure -> {
                    _state.update { it.copy(isExporting = false) }
                    _events.trySend(Event.ShowError(outcome.error.toString()))
                }
                Outcome.Loading -> Unit
            }
        }
    }

    data class UiState(
        val isExporting: Boolean = false,
        val lastReport: ExportReport? = null,
    )

    sealed interface Event {
        data class Done(val report: ExportReport) : Event
        data class ShowError(val message: String) : Event
    }
}
