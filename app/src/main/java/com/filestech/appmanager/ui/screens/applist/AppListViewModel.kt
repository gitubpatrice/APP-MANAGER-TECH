package com.filestech.appmanager.ui.screens.applist

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filestech.appmanager.core.ext.STATEFLOW_STOP_TIMEOUT_MS
import com.filestech.appmanager.core.ext.asFlow
import com.filestech.appmanager.core.ext.oneShotEvents
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.core.result.map
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.model.AppSortOrder
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.AppAction
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.domain.model.AppTag
import com.filestech.appmanager.domain.model.BatchActionResult
import com.filestech.appmanager.domain.model.FilterOptions
import com.filestech.appmanager.domain.repository.AppInfoRepository
import com.filestech.appmanager.domain.usecase.BatchActionUseCase
import com.filestech.appmanager.domain.usecase.ClearAppCacheUseCase
import com.filestech.appmanager.domain.usecase.GetInstalledAppsUseCase
import com.filestech.appmanager.domain.usecase.RescanAppsUseCase
import com.filestech.appmanager.domain.usecase.UninstallAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the App List screen.
 *
 * Owns four reactive inputs:
 * - [searchQuery]    — substring filter on app label (case-insensitive)
 * - [sortOrder]      — ordering applied by [GetInstalledAppsUseCase]
 * - [filterOptions]  — system/user/disabled split, categories, installer, size envelope
 * - [selectedPackages] — multi-select for batch actions
 *
 * Pipeline:
 * 1. (query, sort, filter) → combine → pipeline state
 * 2. pipeline → flatMapLatest → getInstalledApps(filter, sort)
 * 3. + apply substring search post-filter
 * 4. + combine with selectedPackages → final UiState
 *
 * Why flatMapLatest: when the user changes the filter quickly, only the latest
 * upstream flow is collected — older collectors are cancelled, preventing
 * stale data from racing into the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppListViewModel @Inject constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val uninstallApp: UninstallAppUseCase,
    private val clearAppCache: ClearAppCacheUseCase,
    private val batchAction: BatchActionUseCase,
    private val rescanApps: RescanAppsUseCase,
    private val appInfoRepo: AppInfoRepository,
    /**
     * v0.3.4 — needed for the per-tag filter chip row. We collect the
     * `appTags` map from the settings flow into the pipeline so the visible
     * list reactively shrinks/grows when the user (re)assigns a tag from
     * AppDetail without re-entering the screen.
     */
    private val settings: SettingsRepository,
    private val intents: IntentFactory,
) : ViewModel() {

    // -----------------------------------------------------------------------
    // Reactive inputs
    // -----------------------------------------------------------------------

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(AppSortOrder.NAME_ASC)
    private val _filterOptions = MutableStateFlow(FilterOptions.DEFAULT)
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val _isRefreshing = MutableStateFlow(false)
    private val _usageStatsGranted = MutableStateFlow(appInfoRepo.hasUsageStatsAccess())

    /**
     * v0.3.4 — Currently active tag filter. Empty set = show every app
     * (chip filter pill "Tous"). Non-empty set = show only apps whose
     * appTags set has at least one tag in common with this filter.
     * Multi-select chip row in the UI (a user can keep WORK + TOOLS pinned
     * simultaneously). Not persisted — resetting on process restart matches
     * the existing search/sort behaviour and avoids a sticky filter trap.
     */
    private val _tagFilter = MutableStateFlow<Set<AppTag>>(emptySet())

    // -----------------------------------------------------------------------
    // Pipeline → final UiState
    // -----------------------------------------------------------------------

    private data class Pipeline(
        val query: String,
        val sort: AppSortOrder,
        val filter: FilterOptions,
        /**
         * v0.3.4 — Per-tag visibility filter. Empty = no tag filter (every
         * app visible). Non-empty = keep apps whose tag set intersects this
         * filter (OR semantics across selected chips).
         */
        val tagFilter: Set<AppTag>,
    )

    private val pipeline: StateFlow<Pipeline> = combine(
        _searchQuery,
        _sortOrder,
        _filterOptions,
        _tagFilter,
    ) { query, sort, filter, tagFilter -> Pipeline(query, sort, filter, tagFilter) }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
            initialValue = Pipeline("", AppSortOrder.NAME_ASC, FilterOptions.DEFAULT, emptySet()),
        )

    /**
     * v0.3.4 — Hot stream of the persisted tag assignments
     * (`Map<packageName, Set<AppTag>>`). Re-derived from the settings flow
     * via `distinctUntilChanged` so unrelated DataStore writes (theme,
     * scanner cadence…) do NOT re-trigger a full list refilter.
     */
    private val appTagsFlow: Flow<Map<String, Set<AppTag>>> =
        settings.flow
            .map { it.appTags }
            .distinctUntilChanged()

    private val listFlow: Flow<Outcome<List<AppInfo>>> = combine(
        // Inner: the legacy (query × sort × filter) flatMapLatest pipeline.
        // Kept structurally identical to the v0.3.3 form to avoid behaviour
        // drift on the hot path. The new tagFilter lives at the outer level
        // so we never re-subscribe to the heavy getInstalledApps flow on a
        // chip toggle (only re-filters in-memory).
        pipeline.flatMapLatest { p ->
            // VII C3 fix: direct .map preserves upstream flowOn(io) — see GetInstalledAppsUseCase.
            getInstalledApps(filter = p.filter, sortOrder = p.sort)
                .map { outcome -> outcome.applyQuery(p.query) }
        },
        pipeline,
        appTagsFlow,
    ) { queriedOutcome, p, allTags ->
        queriedOutcome.applyTagFilter(p.tagFilter, allTags)
    }

    val state: StateFlow<UiState> = combine(
        listFlow,
        _selectedPackages,
        pipeline,
        _isRefreshing,
        _usageStatsGranted,
        appTagsFlow,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val outcome = values[0] as Outcome<List<AppInfo>>
        @Suppress("UNCHECKED_CAST")
        val selected = values[1] as Set<String>
        val p = values[2] as Pipeline
        val refreshing = values[3] as Boolean
        val usageGranted = values[4] as Boolean
        @Suppress("UNCHECKED_CAST")
        val allTags = values[5] as Map<String, Set<AppTag>>
        UiState(
            listOutcome        = outcome,
            searchQuery        = p.query,
            sortOrder          = p.sort,
            filterOptions      = p.filter,
            tagFilter          = p.tagFilter,
            appTags            = allTags,
            selectedPackages   = selected,
            isRefreshing       = refreshing,
            usageStatsGranted  = usageGranted,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(STATEFLOW_STOP_TIMEOUT_MS),
        initialValue = UiState(),
    )

    // -----------------------------------------------------------------------
    // One-shot events
    // -----------------------------------------------------------------------

    private val _events = oneShotEvents<Event>()
    val events: Flow<Event> = _events.asFlow()

    // -----------------------------------------------------------------------
    // Reactive input setters
    // -----------------------------------------------------------------------

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSortOrderChanged(order: AppSortOrder) {
        _sortOrder.value = order
    }

    fun onFilterChanged(options: FilterOptions) {
        _filterOptions.value = options
    }

    /** Convenience for the common "toggle include system apps" switch. */
    fun onToggleSystemApps(include: Boolean) {
        _filterOptions.update { it.copy(includeSystemApps = include) }
    }

    /**
     * v0.3.4 — Multi-select chip behaviour: tapping a chip flips its
     * membership in the current filter set. Tapping "Tous" (or every chip
     * deselected) restores the empty-set "no filter" state. The chip row
     * never holds itself in an invalid state — empty set = visible all.
     */
    fun onToggleTagFilter(tag: AppTag) {
        _tagFilter.update { current ->
            if (tag in current) current - tag else current + tag
        }
    }

    /** Resets the per-tag filter to "no filter" (every app visible). */
    fun onClearTagFilter() {
        _tagFilter.value = emptySet()
    }

    // -----------------------------------------------------------------------
    // Selection (multi-select)
    // -----------------------------------------------------------------------

    fun toggleSelection(packageName: String) {
        _selectedPackages.update { current ->
            if (packageName in current) current - packageName else current + packageName
        }
    }

    fun clearSelection() {
        _selectedPackages.value = emptySet()
    }

    fun selectAll(visible: List<AppInfo>) {
        _selectedPackages.value = visible.map { it.packageName }.toSet()
    }

    // -----------------------------------------------------------------------
    // Batch actions
    // -----------------------------------------------------------------------

    /**
     * Builds one Uninstall Intent per selected package and emits them as a
     * single one-shot event. The UI launches them sequentially — the OS
     * shows its own confirmation dialog per package.
     */
    fun batchUninstall() = viewModelScope.launch {
        val selection = _selectedPackages.value
        if (selection.isEmpty()) return@launch
        val list = selection.mapNotNull { pkg -> uninstallApp(pkg).getOrNull() }
        if (list.isNotEmpty()) {
            _events.trySend(Event.LaunchIntentsSequentially(list))
        }
        clearSelection()
    }

    /**
     * Builds one "open App info" Settings Intent per selected package — the
     * user clears the cache themselves from each screen.
     */
    fun batchClearCache() = viewModelScope.launch {
        val selection = _selectedPackages.value
        if (selection.isEmpty()) return@launch
        val list = selection.mapNotNull { pkg -> clearAppCache(pkg).getOrNull() }
        if (list.isNotEmpty()) {
            _events.trySend(Event.LaunchIntentsSequentially(list))
        }
        clearSelection()
    }

    /** Silent batch — only ForceStop qualifies on non-root Android. */
    fun batchForceStop() = viewModelScope.launch {
        val selection = _selectedPackages.value
        if (selection.isEmpty()) return@launch
        when (val outcome = batchAction(selection.toList(), AppAction.ForceStop)) {
            is Outcome.Success -> _events.trySend(Event.BatchDone(outcome.value))
            is Outcome.Failure -> _events.trySend(Event.ShowError(outcome.error.toString()))
            Outcome.Loading    -> Unit
        }
        clearSelection()
    }

    // -----------------------------------------------------------------------
    // Direct intents
    // -----------------------------------------------------------------------

    fun requestUsageStatsPermission() {
        _events.trySend(Event.LaunchIntent(intents.usageAccessSettingsIntent()))
    }

    /**
     * Forces a full rescan. Wired to the toolbar Refresh button and to the
     * Material 3 PullToRefreshBox. Idempotent — multiple rapid calls fold
     * into a single in-flight scan thanks to the `_isRefreshing` guard.
     */
    fun refresh() {
        // v0.1.1 audit M-3 fix — atomic compareAndSet so a simultaneous swipe
        // (PullToRefreshBox) + tap (toolbar Refresh) on the same frame folds
        // into a single in-flight scan instead of stacking two rescans.
        if (!_isRefreshing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                when (val r = rescanApps()) {
                    is Outcome.Success -> Timber.d("Manual rescan complete")
                    is Outcome.Failure -> _events.trySend(Event.ShowError(r.error.toString()))
                    Outcome.Loading    -> Unit
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Called from the Composable on `ON_RESUME` — re-probes
     * `PACKAGE_USAGE_STATS` (the user may have just granted it via OS Settings)
     * and triggers an automatic rescan when the permission has just flipped
     * from denied → granted. This guarantees that sizes / last-used dates
     * appear without the user having to tap Refresh themselves.
     */
    fun onResumed() {
        val wasGranted = _usageStatsGranted.value
        val nowGranted = appInfoRepo.hasUsageStatsAccess()
        if (wasGranted != nowGranted) {
            _usageStatsGranted.value = nowGranted
            if (nowGranted) {
                Timber.i("PACKAGE_USAGE_STATS just granted — auto-rescan")
                refresh()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Search post-filter
    // -----------------------------------------------------------------------

    private fun Outcome<List<AppInfo>>.applyQuery(query: String): Outcome<List<AppInfo>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return this
        return this.map { apps ->
            apps.filter { app ->
                app.label.contains(trimmed, ignoreCase = true) ||
                    app.packageName.contains(trimmed, ignoreCase = true)
            }
        }
    }

    /**
     * v0.3.4 — Post-filter by the user-assigned tag set. Empty [tagFilter]
     * is a no-op (full list passes through). Apps with no tag assignment
     * are excluded once the filter is active — the user picked a chip to
     * say "show me Work apps", not "show me Work apps + untagged apps".
     */
    private fun Outcome<List<AppInfo>>.applyTagFilter(
        tagFilter: Set<AppTag>,
        allTags: Map<String, Set<AppTag>>,
    ): Outcome<List<AppInfo>> {
        if (tagFilter.isEmpty()) return this
        return this.map { apps ->
            apps.filter { app ->
                val assigned = allTags[app.packageName].orEmpty()
                assigned.any { it in tagFilter }
            }
        }
    }

    // -----------------------------------------------------------------------
    // UiState + Event
    // -----------------------------------------------------------------------

    data class UiState(
        val listOutcome: Outcome<List<AppInfo>> = Outcome.Loading,
        val searchQuery: String = "",
        val sortOrder: AppSortOrder = AppSortOrder.NAME_ASC,
        val filterOptions: FilterOptions = FilterOptions.DEFAULT,
        /**
         * v0.3.4 — Active per-tag filter chips. Empty = no filter.
         */
        val tagFilter: Set<AppTag> = emptySet(),
        /**
         * v0.3.4 — Snapshot of the full tag assignment map so the
         * AppListItem can render per-row tag chips without each row
         * re-fetching the DataStore. Reactive on settings changes via
         * `appTagsFlow`.
         */
        val appTags: Map<String, Set<AppTag>> = emptyMap(),
        val selectedPackages: Set<String> = emptySet(),
        val isRefreshing: Boolean = false,
        val usageStatsGranted: Boolean = true,
    ) {
        val isInSelectionMode: Boolean get() = selectedPackages.isNotEmpty()
        val selectionCount: Int get() = selectedPackages.size
    }

    sealed interface Event {
        data class ShowError(val message: String) : Event
        data class NavigateToDetail(val packageName: String) : Event
        data class LaunchIntent(val intent: Intent) : Event
        data class LaunchIntentsSequentially(val intents: List<Intent>) : Event
        data class BatchDone(val result: BatchActionResult) : Event
    }

    // VIII C7 fix: STOP_TIMEOUT_MS factored to core.ext.STATEFLOW_STOP_TIMEOUT_MS
    // (shared between this VM and SettingsViewModel).

    init {
        Timber.d("AppListViewModel initialised")
    }
}
