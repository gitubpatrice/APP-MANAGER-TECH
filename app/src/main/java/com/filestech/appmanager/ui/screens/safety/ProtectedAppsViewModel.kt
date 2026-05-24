package com.filestech.appmanager.ui.screens.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.CriticalAppDetector
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.CriticalCategory
import com.filestech.appmanager.domain.repository.AppInfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v0.3.1 — Drives the "Apps protégées" screen.
 *
 * The screen surfaces:
 *  - a READ-ONLY section per built-in [CriticalCategory] (one card per
 *    category, listing the package names AMT ships hardcoded — useful for
 *    transparency: the user can see exactly which apps trigger the
 *    Safety Guardrails hold-3s),
 *  - the user's own additions ("Vos apps protégées") with swipe-to-remove,
 *  - an "Ajouter une app" action launching the package picker.
 *
 * The state combines:
 *  - the static built-in whitelists (read once at init from the detector),
 *  - the live `settings.flow.safety.userProtectedPackages` (DataStore),
 *  - the live `appInfoRepository.observeApps(includeSystemApps=false)` so
 *    the user-added entries can be rendered with their app label and icon
 *    (not just a raw package name).
 */
@HiltViewModel
class ProtectedAppsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val appInfoRepository: AppInfoRepository,
    detector: CriticalAppDetector,
) : ViewModel() {

    /** Built-in whitelists — snapshot at construction; never mutated at runtime. */
    private val builtInByCategory: Map<CriticalCategory, List<String>> =
        CriticalCategory.entries
            .filter { it != CriticalCategory.USER_PROTECTED }
            .associateWith { detector.builtInPackagesFor(it).sorted() }

    val state: kotlinx.coroutines.flow.StateFlow<UiState> = combine(
        settings.flow,
        appInfoRepository.observeApps(includeSystemApps = false),
    ) { snapshot, appsOutcome ->
        val labels = (appsOutcome.getOrNull().orEmpty()).associateBy({ it.packageName }, { it.label })
        UiState(
            builtIn  = builtInByCategory,
            userAdded = snapshot.safety.userProtectedPackages
                .map { pkg -> UserProtectedEntry(packageName = pkg, label = labels[pkg]) }
                .sortedBy { (it.label ?: it.packageName).lowercase() },
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
        initialValue = UiState(builtIn = builtInByCategory, userAdded = emptyList()),
    )

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    /**
     * Adds [packageName] to the user-protected set. Validates the package
     * name + applies the 200-entry defensive cap. Idempotent — re-adding the
     * same package is a no-op.
     */
    fun add(packageName: String) {
        if (!packageName.isValidPackageName()) {
            _events.trySend(Event.ShowError("Invalid package name"))
            return
        }
        viewModelScope.launch {
            settings.update {
                if (packageName in safety.userProtectedPackages) return@update this
                if (safety.userProtectedPackages.size >= MAX_CAP) {
                    _events.trySend(Event.ShowError("Liste pleine ($MAX_CAP max)"))
                    return@update this
                }
                copy(safety = safety.copy(
                    userProtectedPackages = safety.userProtectedPackages + packageName,
                ))
            }
            _events.trySend(Event.Added(packageName))
        }
    }

    /** Removes [packageName] from the user-protected set. Idempotent. */
    fun remove(packageName: String) {
        viewModelScope.launch {
            settings.update {
                copy(safety = safety.copy(
                    userProtectedPackages = safety.userProtectedPackages - packageName,
                ))
            }
            _events.trySend(Event.Removed(packageName))
        }
    }

    data class UiState(
        val builtIn: Map<CriticalCategory, List<String>>,
        val userAdded: List<UserProtectedEntry>,
    )

    data class UserProtectedEntry(
        val packageName: String,
        /** Cached app label — null when the app is not installed (orphan entry). */
        val label: String?,
    )

    sealed interface Event {
        data class Added(val packageName: String) : Event
        data class Removed(val packageName: String) : Event
        data class ShowError(val message: String) : Event
    }

    private companion object {
        const val MAX_CAP: Int = 200
    }
}

/**
 * Picker state shared with the screen. Loads the device's user-installed apps
 * (excluding already-protected ones) when the picker is opened, sorted by label.
 */
@HiltViewModel
class ProtectedAppsPickerViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val appInfoRepository: AppInfoRepository,
) : ViewModel() {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val candidates: kotlinx.coroutines.flow.StateFlow<List<AppInfo>> = combine(
        appInfoRepository.observeApps(includeSystemApps = false),
        settings.flow,
    ) { appsOutcome, snapshot ->
        val protected = snapshot.safety.userProtectedPackages
        appsOutcome.getOrNull().orEmpty()
            .filter { it.packageName !in protected }
            .sortedBy { it.label.lowercase() }
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
        initialValue = emptyList(),
    )
}
