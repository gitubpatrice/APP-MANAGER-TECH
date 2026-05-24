package com.filestech.appmanager.ui.screens.securityaudit

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.usecase.GetAccessibilityServiceAppsUseCase
import com.filestech.appmanager.domain.usecase.GetDeviceAdminAppsUseCase
import com.filestech.appmanager.domain.usecase.UninstallAppUseCase
import com.filestech.appmanager.core.result.getOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Security audit ViewModel — fetches DeviceAdmin + AccessibilityService apps
 * in parallel and exposes them as two lists.
 *
 * v0.1.3 hotfix: re-entrancy guard + `withTimeout(10 s)` so the Refresh button
 * never gets stuck disabled if a system service call hangs.
 *
 * v0.1.3 actions: emits [Event.LaunchIntent] for the two OS-level
 * "disable in Settings" shortcuts (Android does not let third-party apps
 * revoke DeviceAdmin or AccessibilityService programmatically).
 */
@HiltViewModel
class SecurityAuditViewModel @Inject constructor(
    private val getDeviceAdmins: GetDeviceAdminAppsUseCase,
    private val getAccessibility: GetAccessibilityServiceAppsUseCase,
    private val uninstallApp: UninstallAppUseCase,
    private val intents: IntentFactory,
) : ViewModel() {

    // v0.1.3 audit L-2 fix — init with isLoading=true so the very first
    // composition shows the spinner instead of a blank "no apps" empty list
    // before refresh() flips the flag.
    private val _state = MutableStateFlow(UiState(isLoading = true))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    /**
     * v0.2.1 bug fix — dedicated re-entrancy flag, decoupled from
     * [UiState.isLoading]. Was a single isLoading flag doing double duty
     * (UI spinner + re-entry guard) → since [_state] starts with
     * `isLoading = true` (audit L-2 fix for the empty-list flash), the
     * `init { refresh() }` immediately tripped the guard and never ran.
     * User report: "si je tape sur refresh, ça refresh en permanence, comme
     * si c'était bloqué".
     */
    private val isRefreshing = AtomicBoolean(false)

    init {
        refresh()
    }

    fun refresh() {
        if (!isRefreshing.compareAndSet(false, true)) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                withTimeout(REFRESH_TIMEOUT_MS) {
                    coroutineScope {
                        val adminD = async { getDeviceAdmins() }
                        val a11yD  = async { getAccessibility() }
                        val (adminR, a11yR) = awaitAll(adminD, a11yD)
                        val admins  = (adminR as? Outcome.Success)?.value.orEmpty()
                        val a11ySvc = (a11yR  as? Outcome.Success)?.value.orEmpty()
                        _state.update {
                            it.copy(
                                isLoading            = false,
                                deviceAdmins         = admins,
                                accessibilityServices = a11ySvc,
                                error                = null,
                            )
                        }
                        // v0.1.3 user feedback "j'ai pas l'impression que ça
                        // marche" — the system services respond in < 50 ms so
                        // the spinner is invisible. Explicit snackbar makes the
                        // refresh's effect perceivable.
                        _events.trySend(Event.RefreshDone(admins.size, a11ySvc.size))
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "Security audit refresh timed out after %d ms", REFRESH_TIMEOUT_MS)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error     = "Système a mis trop longtemps à répondre",
                    )
                }
            } finally {
                isRefreshing.set(false)
            }
        }
    }

    fun openDeviceAdminSettings() {
        _events.trySend(Event.LaunchIntent(intents.deviceAdminSettingsIntent()))
    }

    fun openAccessibilitySettings() {
        _events.trySend(Event.LaunchIntent(intents.accessibilitySettingsIntent()))
    }

    /** Launches the OS uninstall flow for [packageName]. */
    fun uninstall(packageName: String) {
        uninstallApp(packageName).getOrNull()?.let { intent ->
            _events.trySend(Event.LaunchIntent(intent))
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val deviceAdmins: List<String> = emptyList(),
        val accessibilityServices: List<String> = emptyList(),
        val error: String? = null,
    )

    sealed interface Event {
        data class LaunchIntent(val intent: Intent) : Event
        data class RefreshDone(val adminCount: Int, val a11yCount: Int) : Event
    }

    companion object {
        private const val REFRESH_TIMEOUT_MS: Long = 10_000L

        /**
         * Hardcoded whitelist of well-known publisher packages that legitimately
         * hold DeviceAdmin or AccessibilityService — surfacing them with a "System"
         * badge reduces user anxiety. Conservative: only OS / OEM core apps.
         */
        val SYSTEM_PUBLISHERS: Set<String> = setOf(
            // Google Play services + Find My Device
            "com.google.android.gms",
            "com.google.android.apps.work.clouddpc",
            "com.google.android.apps.accessibility.maui.talkback",
            "com.google.android.marvin.talkback",
            // Samsung Knox / One UI
            "com.samsung.android.knox.containeragent",
            "com.samsung.android.knox.containercore",
            "com.samsung.android.knox.app.networkaccessmanager",
            "com.sec.android.app.SecSetupWizard",
            "com.samsung.android.app.intelligenceservice",
            "com.samsung.android.bixby.agent",
            // OEM Find My Device equivalents
            "com.huawei.android.findmyphone",
            "com.xiaomi.finddevice",
            // Android system
            "com.android.settings",
        )
    }
}
