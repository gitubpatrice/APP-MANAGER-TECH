package com.filestech.appmanager.ui.screens.zombies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.ZombieApp
import com.filestech.appmanager.domain.usecase.GetZombieAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Zombie-apps ViewModel — surfaces apps never opened or unused for too long.
 */
@HiltViewModel
class ZombiesViewModel @Inject constructor(
    private val getZombies: GetZombieAppsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val outcome = getZombies()
            _state.update {
                it.copy(
                    isLoading = false,
                    outcome   = outcome,
                )
            }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val outcome: Outcome<List<ZombieApp>> = Outcome.Loading,
    )
}
