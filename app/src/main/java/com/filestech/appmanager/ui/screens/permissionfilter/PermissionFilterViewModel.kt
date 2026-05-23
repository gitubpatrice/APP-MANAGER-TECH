package com.filestech.appmanager.ui.screens.permissionfilter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.usecase.GetAppsByPermissionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Apps that declare permission X" ViewModel.
 *
 * User picks (or types) a permission name + an `onlyGranted` toggle and the
 * use case scans every user app via PackageManager.
 */
@HiltViewModel
class PermissionFilterViewModel @Inject constructor(
    private val getAppsByPermission: GetAppsByPermissionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setPermission(name: String) {
        _state.update { it.copy(permission = name) }
    }

    fun setOnlyGranted(value: Boolean) {
        _state.update { it.copy(onlyGranted = value) }
    }

    fun search() {
        val perm = _state.value.permission.trim()
        if (perm.isEmpty()) return
        _state.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            val outcome = getAppsByPermission(
                permission        = perm,
                onlyGranted       = _state.value.onlyGranted,
                includeSystemApps = false,
            )
            _state.update { it.copy(isSearching = false, outcome = outcome) }
        }
    }

    data class UiState(
        val permission: String = "",
        val onlyGranted: Boolean = false,
        val isSearching: Boolean = false,
        val outcome: Outcome<List<AppInfo>> = Outcome.Success(emptyList()),
    )

    companion object {
        /** Quick-pick permissions surfaced as chips in the UI. */
        val QUICK_PICKS: List<String> = listOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.INTERNET",
            "android.permission.SYSTEM_ALERT_WINDOW",
        )
    }
}
