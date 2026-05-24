package com.filestech.appmanager.ui.screens.zombies

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.ZombieApp
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.usecase.GetZombieAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Zombie-apps ViewModel — surfaces apps never opened or unused for too long.
 *
 * v0.2.1 — added [Event.RefreshDone] snackbar feedback: the use case responds
 * in < 50 ms so the spinner is invisible and the user perceives the refresh
 * button as broken (user report: "si je tape sur la roue refresh, rien ne
 * se passe ?"). The explicit snackbar makes the action's effect perceivable.
 *
 * v0.2.1 audits C2b + C8b — added `usageStatsGranted` probe + `onResumed()`
 * + `requestUsageStatsPermission()`. Without PACKAGE_USAGE_STATS, every
 * app's `lastUsedTime == 0` → `GetZombieAppsUseCase` classifies the entire
 * user-app catalogue as NEVER_OPENED (massive false-positive). Banner now
 * surfaces the missing permission.
 *
 * NOTE: re-entrancy `isRefreshing` AtomicBoolean + `Event.RefreshDone` were
 * fixed in an earlier v0.2.1 patch — DO NOT refactor those in this file.
 */
@HiltViewModel
class ZombiesViewModel @Inject constructor(
    private val getZombies: GetZombieAppsUseCase,
    private val appInfoRepo: AppInfoRepository,
    private val intents: IntentFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(
        UiState(usageStatsGranted = appInfoRepo.hasUsageStatsAccess()),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    /** Re-entrancy guard separated from [UiState.isLoading] (same pattern
     *  as SecurityAuditViewModel after v0.2.1 fix). */
    private val isRefreshing = AtomicBoolean(false)

    init {
        refresh()
    }

    fun refresh() {
        if (!isRefreshing.compareAndSet(false, true)) return
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val outcome = getZombies()
                _state.update {
                    it.copy(
                        isLoading = false,
                        outcome   = outcome,
                    )
                }
                val count = (outcome as? Outcome.Success)?.value?.size ?: 0
                _events.trySend(Event.RefreshDone(count))
            } finally {
                isRefreshing.set(false)
            }
        }
    }

    /**
     * v0.2.1 audit C8b — re-probe PACKAGE_USAGE_STATS on ON_RESUME. When the
     * user grants the permission in Settings and comes back, banner disappears
     * AND we trigger a refresh so the now-meaningful `lastUsedTime` re-classifies
     * the zombie list correctly (was all `NEVER_OPENED` before the grant).
     */
    fun onResumed() {
        val wasGranted = _state.value.usageStatsGranted
        val nowGranted = appInfoRepo.hasUsageStatsAccess()
        if (wasGranted != nowGranted) {
            _state.update { it.copy(usageStatsGranted = nowGranted) }
            if (nowGranted) refresh()
        }
    }

    fun requestUsageStatsPermission() {
        _events.trySend(Event.LaunchIntent(intents.usageAccessSettingsIntent()))
    }

    data class UiState(
        val isLoading: Boolean = false,
        val outcome: Outcome<List<ZombieApp>> = Outcome.Loading,
        /** v0.2.1 — false → UsageStatsAccessBanner visible in screen. */
        val usageStatsGranted: Boolean = true,
    )

    sealed interface Event {
        /** Fired after refresh() completes — payload = count of zombie apps found. */
        data class RefreshDone(val count: Int) : Event
        /** v0.2.1 — Settings → Usage access deep-link emitted by the banner CTA. */
        data class LaunchIntent(val intent: Intent) : Event
    }
}
