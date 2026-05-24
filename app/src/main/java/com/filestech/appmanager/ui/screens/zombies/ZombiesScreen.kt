package com.filestech.appmanager.ui.screens.zombies

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.ZombieApp
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.UsageStatsAccessBanner
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.components.state.ErrorState
import com.filestech.appmanager.ui.components.state.LoadingState
import com.filestech.appmanager.ui.theme.BrandDanger
import timber.log.Timber

/**
 * Zombie apps screen — lists apps NEVER_OPENED (>7 d install grace) or
 * UNUSED_SINCE (>30 d), sorted by unusedDays desc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZombiesScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: ZombiesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // v0.3.1 — local search filter (label OR package, case-insensitive). Kept
    // VM-free because the filtering is pure UI: the unfiltered Outcome stays
    // the source of truth for refresh / loading / error semantics.
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // v0.2.1 audit C8b fix — re-probe PACKAGE_USAGE_STATS on ON_RESUME so
    // the banner disappears + an auto-refresh triggers when the user returns
    // from Settings after granting the permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    // v0.2.1 — refresh feedback. The use case responds in < 50 ms so the
    // spinner is invisible; the snackbar makes the refresh's effect
    // perceivable (user report "si je tape sur la roue refresh, rien ne
    // se passe ?").
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ZombiesViewModel.Event.RefreshDone ->
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.zombies_refresh_done, event.count),
                    )
                is ZombiesViewModel.Event.LaunchIntent -> {
                    runCatching {
                        context.startActivity(
                            event.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure { Timber.w(it, "ZombiesScreen launch failed") }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                title = { BrandedTitle(stringResource(R.string.screen_zombies_title)) },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                    }
                },
            )
        },
    ) { innerPadding ->
        // v0.2.1 audit C2b fix — Column wrapping so the UsageStatsAccessBanner
        // sits above the list. Without PACKAGE_USAGE_STATS, lastUsedTime = 0
        // for every app → GetZombieAppsUseCase classifies everything as
        // NEVER_OPENED (false positives). The banner makes the cause visible.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            UsageStatsAccessBanner(
                visible      = !state.usageStatsGranted,
                onGrantClick = { viewModel.requestUsageStatsPermission() },
            )
            // v0.3.1 — SearchBar (only when there's at least one item to filter).
            val outcome = state.outcome
            if (outcome is Outcome.Success && outcome.value.isNotEmpty()) {
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon   = {
                        Icon(
                            imageVector        = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    placeholder   = { Text(stringResource(R.string.zombies_search_placeholder)) },
                    singleLine    = true,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (val o = state.outcome) {
                    Outcome.Loading      -> LoadingState()
                    is Outcome.Failure   -> ErrorState(message = o.error.toString(), onRetry = viewModel::refresh)
                    is Outcome.Success   -> {
                        if (o.value.isEmpty()) {
                            EmptyState(
                                icon  = Icons.Outlined.SentimentSatisfied,
                                title = stringResource(R.string.zombies_empty_title),
                                body  = stringResource(R.string.zombies_empty_body),
                            )
                        } else {
                            val filtered = remember(o.value, searchQuery) {
                                if (searchQuery.isBlank()) o.value
                                else {
                                    val q = searchQuery.trim().lowercase()
                                    o.value.filter { z ->
                                        z.info.label.lowercase().contains(q) ||
                                            z.info.packageName.lowercase().contains(q)
                                    }
                                }
                            }
                            if (filtered.isEmpty()) {
                                EmptyState(
                                    icon  = Icons.Outlined.SentimentSatisfied,
                                    title = stringResource(R.string.zombies_search_no_match_title),
                                    body  = stringResource(R.string.zombies_search_no_match_body, searchQuery.trim()),
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(items = filtered, key = { it.info.packageName }) { zombie ->
                                        ZombieRow(zombie = zombie, onClick = { onItemClick(zombie.info.packageName) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZombieRow(zombie: ZombieApp, onClick: () -> Unit) {
    val context = LocalContext.current
    val sizeLabel = remember(zombie.info.totalSizeBytes) {
        Formatter.formatShortFileSize(context, zombie.info.totalSizeBytes)
    }
    // v0.2.1 — entire row clickable (was only the trailing arrow IconButton,
    // unintuitive — user feedback "pouvoir taper sur l'appli afin d'afficher
    // le détail, mieux que la flèche qui est à coté"). The chevron stays as
    // a visual affordance but is no longer the only tap target.
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = zombie.info.packageName, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = zombie.info.label,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReasonBadge(zombie = zombie)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text  = sizeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Visual chevron only — no separate IconButton (the whole Row is
        // the tap target now). Marked with a non-null contentDescription so
        // TalkBack still announces "Détails de l'application".
        Icon(
            imageVector        = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_view_details),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReasonBadge(zombie: ZombieApp) {
    val (color, label) = when (zombie.reason) {
        ZombieApp.Reason.NEVER_OPENED ->
            BrandDanger to stringResource(R.string.zombie_reason_never_opened)
        ZombieApp.Reason.UNUSED_SINCE ->
            MaterialTheme.colorScheme.tertiary to stringResource(R.string.zombie_reason_unused_since, zombie.unusedDays)
    }
    Surface(
        shape        = RoundedCornerShape(50),
        color        = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
