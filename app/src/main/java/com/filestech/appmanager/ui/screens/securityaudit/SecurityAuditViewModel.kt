package com.filestech.appmanager.ui.screens.securityaudit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.usecase.GetAccessibilityServiceAppsUseCase
import com.filestech.appmanager.domain.usecase.GetDeviceAdminAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Security audit ViewModel — fetches DeviceAdmin + AccessibilityService apps
 * in parallel and exposes them as two lists. Both queries are cheap (single
 * system service call each) so we refresh on every screen entry instead of
 * caching to Room.
 */
@HiltViewModel
class SecurityAuditViewModel @Inject constructor(
    private val getDeviceAdmins: GetDeviceAdminAppsUseCase,
    private val getAccessibility: GetAccessibilityServiceAppsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            coroutineScope {
                val admin = async { getDeviceAdmins() }
                val a11y = async { getAccessibility() }
                val (adminR, a11yR) = awaitAll(admin, a11y)
                _state.update {
                    it.copy(
                        isLoading            = false,
                        deviceAdmins         = (adminR as? Outcome.Success)?.value.orEmpty(),
                        accessibilityServices = (a11yR as? Outcome.Success)?.value.orEmpty(),
                    )
                }
            }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val deviceAdmins: List<String> = emptyList(),
        val accessibilityServices: List<String> = emptyList(),
    )
}
