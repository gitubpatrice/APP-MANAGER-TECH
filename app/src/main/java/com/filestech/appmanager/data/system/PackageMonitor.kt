package com.filestech.appmanager.data.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.filestech.appmanager.core.ext.isValidPackageName
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.di.ApplicationScope
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.usecase.RecordLifecycleEventUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v0.3.0 — Runtime-registered receiver that observes OS package events and
 * dispatches them to [RecordLifecycleEventUseCase] for persistence.
 *
 * **Why runtime registration (not manifest):**
 *  - Android 8+ restricts implicit manifest broadcasts; `PACKAGE_ADDED` and
 *    `PACKAGE_REMOVED` are still allowed there but at the cost of waking the
 *    app for EVERY install/uninstall on the device even when the feature is
 *    disabled.
 *  - Runtime registration is gated by the user's `lifecycle.enabled` setting:
 *    no toggle → no broadcast subscription → zero battery overhead.
 *  - We are NOT a background-tied feature (no notif fires from the receiver
 *    itself); the app process needs to be alive to observe — that is OK
 *    because the broadcast also fires later when the app is reopened, and
 *    [BaselineLifecycleScanUseCase] re-detects state at next launch via the
 *    BASELINE path.
 *
 * Lifecycle:
 *  - [start] is called from `MainApplication.onCreate` (and from Settings
 *    when the user enables the toggle).
 *  - [stop] is called when the user disables the toggle.
 *  - Idempotent under repeated [start] / [stop] calls thanks to the
 *    [AtomicBoolean] guard.
 *
 * Event stream:
 *  - [events] exposes a `SharedFlow` of every captured [LifecycleEvent] so
 *    the UI layer can react (e.g. show the uninstall-reason dialog right
 *    after an UNINSTALLED row was inserted). Replay = 0 to avoid stale
 *    events on a fresh subscription; BufferOverflow.DROP_OLDEST so the
 *    receiver thread is never blocked.
 */
@Singleton
class PackageMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val recordEvent: RecordLifecycleEventUseCase,
) {

    private val isRegistered = AtomicBoolean(false)

    private val _events = MutableSharedFlow<LifecycleEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<LifecycleEvent> = _events.asSharedFlow()

    /**
     * Receiver kept as a field so [stop] can unregister the exact same
     * instance (passing a fresh lambda-built receiver would throw
     * `IllegalArgumentException: Receiver not registered`).
     */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            val pkg = extractPackageName(intent) ?: return
            // Defence-in-depth: even though the OS only delivers well-formed
            // package strings, validate before persistence so a hypothetical
            // tampered intent (debugger, IPC fuzzer) cannot poison the DB.
            if (!pkg.isValidPackageName()) {
                Timber.w("PackageMonitor: invalid package name in intent: %s", pkg)
                return
            }
            // Ignore self — App Manager Tech updating itself is noise.
            if (pkg == ctx?.packageName) return

            val type = classify(intent) ?: return

            appScope.launch {
                when (val r = recordEvent(pkg, type)) {
                    is Outcome.Success -> {
                        Timber.d("PackageMonitor: %s for %s (event id=%d)", type, pkg, r.value)
                        emitInsertedEvent(r.value, pkg, type)
                    }
                    is Outcome.Failure -> Timber.w(
                        "PackageMonitor: record failed for %s: %s",
                        pkg, r.error,
                    )
                    Outcome.Loading -> Unit
                }
            }
        }
    }

    /**
     * Registers the receiver against the package-event broadcasts. Idempotent
     * — repeated calls after the first are no-ops until [stop] is called.
     */
    fun start() {
        if (!isRegistered.compareAndSet(false, true)) {
            Timber.d("PackageMonitor.start: already registered, skipping")
            return
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            // `data` filter required for package broadcasts (otherwise the OS
            // silently drops registration on some API levels).
            addDataScheme(SCHEME_PACKAGE)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        Timber.i("PackageMonitor: receiver registered")
    }

    fun stop() {
        if (!isRegistered.compareAndSet(true, false)) {
            Timber.d("PackageMonitor.stop: not registered, skipping")
            return
        }
        runCatching { context.unregisterReceiver(receiver) }
            .onFailure { Timber.w(it, "PackageMonitor: unregister failed (already gone?)") }
        Timber.i("PackageMonitor: receiver unregistered")
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun extractPackageName(intent: Intent): String? = intent.data?.schemeSpecificPart

    /**
     * Maps an OS broadcast to our [LifecycleEventType].
     *
     * Subtlety: `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REMOVED` carry
     * `EXTRA_REPLACING = true` when the event is part of an update — in that
     * case the OS fires BOTH the REMOVED (old version) and the ADDED (new
     * version), with the REPLACING extra on both. We collapse this pair into
     * a single REPLACED event by:
     *   - Ignoring the REMOVED side when `replacing == true` (we'd otherwise
     *     log a phantom UNINSTALLED followed by an INSTALLED).
     *   - Mapping the ADDED side to REPLACED in that case.
     *   - The dedicated `ACTION_PACKAGE_REPLACED` broadcast that follows is
     *     also collapsed to REPLACED — we keep that mapping so power-user
     *     OEMs that emit only REPLACED still produce a row.
     *
     * The result: exactly ONE event row per logical state transition.
     */
    private fun classify(intent: Intent): LifecycleEventType? {
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        return when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> if (replacing) {
                LifecycleEventType.REPLACED
            } else {
                LifecycleEventType.INSTALLED
            }
            Intent.ACTION_PACKAGE_REMOVED -> if (replacing) {
                null
            } else {
                LifecycleEventType.UNINSTALLED
            }
            Intent.ACTION_PACKAGE_REPLACED -> LifecycleEventType.REPLACED
            else -> null
        }
    }

    /**
     * Builds a lightweight [LifecycleEvent] for the SharedFlow consumer
     * without re-reading from the DAO. Captures the minimum the UI dialog
     * needs (id, packageName, label, type). Subscribers that need the full
     * row (perms, sizes) should query the repository directly.
     *
     * The label is intentionally left null here because the receiver thread
     * must stay short; the dialog will look up the cached label via
     * [com.filestech.appmanager.domain.repository.AppInfoRepository.getApp].
     */
    private suspend fun emitInsertedEvent(id: Long, pkg: String, type: LifecycleEventType) {
        val skeleton = LifecycleEvent(
            id                          = id,
            packageName                 = pkg,
            label                       = null,
            type                        = type,
            capturedAt                  = System.currentTimeMillis(),
            versionName                 = null,
            versionCode                 = 0L,
            installerPackage            = null,
            totalSizeBytes              = 0L,
            grantedDangerousPermissions = emptyList(),
            userReason                  = null,
        )
        _events.emit(skeleton)
    }

    private companion object {
        const val SCHEME_PACKAGE: String = "package"
        /** SharedFlow extra buffer — bounded so a stalled collector doesn't OOM. */
        const val EVENT_BUFFER: Int = 32
    }
}
