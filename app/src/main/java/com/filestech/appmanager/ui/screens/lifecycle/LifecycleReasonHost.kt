package com.filestech.appmanager.ui.screens.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.data.system.PackageMonitor
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.model.UninstallReason
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import com.filestech.appmanager.ui.components.dialogs.UninstallReasonDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * v0.3.0 — Mounted ONCE at the root of [com.filestech.appmanager.ui.AppRoot]:
 * subscribes to [PackageMonitor.events] and surfaces the
 * [UninstallReasonDialog] right after an UNINSTALLED event is recorded.
 *
 * Why global (not per-screen):
 *  - The OS broadcast can fire at any time, regardless of which screen the
 *    user is on. Attaching the dialog observer to a single screen would miss
 *    events when the user is elsewhere — or worse, fire two dialogs if two
 *    screens observed the same flow.
 *  - The reason capture is a low-frequency UX (an uninstall happens at most
 *    a handful of times per day) so the cost of an always-mounted observer
 *    is negligible.
 *
 * Gated by both `lifecycle.enabled` AND `lifecycle.promptReason` — if the
 * user disabled the prompt, we silently skip the dialog and the event row
 * stays with `user_reason = null` (still useful in the timeline).
 */
@Composable
fun LifecycleReasonHost(viewModel: LifecycleReasonHostViewModel = hiltViewModel()) {
    var pending by remember { mutableStateOf<LifecycleReasonHostViewModel.Prompt?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.prompts.collect { pending = it }
    }

    pending?.let { p ->
        UninstallReasonDialog(
            appLabel  = p.label,
            onConfirm = { reason ->
                viewModel.setReason(p.eventId, reason)
                pending = null
            },
            onDismiss = {
                viewModel.dismiss(p.eventId)
                pending = null
            },
        )
    }
}

/**
 * Backing ViewModel for the dialog host. Subscribes to [PackageMonitor.events]
 * once per ViewModel lifetime, filters UNINSTALLED rows, resolves the cached
 * app label, and pushes a [Prompt] onto a hot SharedFlow consumed by the
 * host composable.
 */
@HiltViewModel
class LifecycleReasonHostViewModel @Inject constructor(
    packageMonitor: PackageMonitor,
    private val settings: SettingsRepository,
    private val appInfoRepository: AppInfoRepository,
    private val lifecycleRepository: AppLifecycleRepository,
) : ViewModel() {

    /**
     * One-shot prompt queue. Replay = 0 so a fresh composition does NOT re-show
     * a dialog that the user has already dismissed. BufferOverflow drops the
     * oldest pending prompt under a flood — better than queuing 20 dialogs.
     */
    private val _prompts = MutableSharedFlow<Prompt>(
        replay = 0,
        extraBufferCapacity = PROMPT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val prompts: Flow<Prompt> = _prompts.asSharedFlow()

    init {
        viewModelScope.launch {
            packageMonitor.events.collect { event ->
                if (event.type != LifecycleEventType.UNINSTALLED) return@collect
                val cfg = settings.flow.first().lifecycle
                if (!cfg.enabled || !cfg.promptReason) return@collect

                // Resolve a human label from the cached app info (the OS no
                // longer has the package by the time we receive the broadcast).
                val label = appInfoRepository.getApp(event.packageName).getOrNull()?.label
                    ?: event.packageName
                _prompts.emit(Prompt(eventId = event.id, packageName = event.packageName, label = label))
            }
        }
    }

    fun setReason(eventId: Long, reason: UninstallReason) {
        viewModelScope.launch {
            when (val r = lifecycleRepository.setReason(eventId, reason)) {
                is Outcome.Success -> if (!r.value) {
                    Timber.d("setReason: row %d not found (purged?)", eventId)
                }
                is Outcome.Failure -> Timber.w("setReason failed for id %d: %s", eventId, r.error)
                Outcome.Loading    -> Unit
            }
        }
    }

    /** Explicit dismiss is recorded as "no reason" — we already wrote NULL on insert, no-op. */
    fun dismiss(eventId: Long) {
        Timber.d("UninstallReason dialog dismissed for event %d", eventId)
    }

    data class Prompt(
        val eventId: Long,
        val packageName: String,
        val label: String,
    )

    private companion object {
        const val PROMPT_BUFFER: Int = 4
    }
}
