package com.filestech.appmanager.ui.screens.signatures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.SignatureCluster
import com.filestech.appmanager.domain.usecase.GroupAppsBySignatureUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * v0.3.3 — Drives the Signature Clusters screen.
 *
 * One-shot compute on entry via `init`. Pull-to-refresh re-runs the use case
 * — gated by an [AtomicBoolean] guard (same pattern as
 * SecurityAuditViewModel v0.2.1) so accidental double-tap doesn't fan out
 * two concurrent PM IPC sweeps.
 */
@HiltViewModel
class SignatureClustersViewModel @Inject constructor(
    private val groupBySignature: GroupAppsBySignatureUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val isComputing = AtomicBoolean(false)

    init {
        refresh()
    }

    fun refresh() {
        if (!isComputing.compareAndSet(false, true)) return
        _state.update { it.copy(outcome = Outcome.Loading) }
        viewModelScope.launch {
            try {
                val result = groupBySignature()
                _state.update { it.copy(outcome = result) }
            } finally {
                isComputing.set(false)
            }
        }
    }

    data class UiState(
        val outcome: Outcome<List<SignatureCluster>> = Outcome.Loading,
    )
}
