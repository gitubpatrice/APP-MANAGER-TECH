package com.filestech.appmanager.ui.screens.smartcleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.SmartSuggestion
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * Smart Cleaner — single ranked list of "what would save the most space if
 * you cleaned it now". One row per actionable suggestion, with the
 * recommended action and the estimated reclaimable size.
 *
 * App Manager Tech innovation: unlike SD Maid which scatters
 * zombies / cache hogs / duplicates across separate tabs, this screen fuses
 * all signals and sorts by impact, so a user with 60 seconds of attention
 * gets the biggest wins first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCleanerScreen(
    onBack: () -> Unit,
    viewModel: SmartCleanerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SmartCleanerViewModel.Event.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
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
                title = { Text(stringResource(R.string.screen_smart_cleaner_title)) },
                actions = {
                    IconButton(onClick = viewModel::analyze, enabled = !state.isAnalyzing) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SmartCleanerBody(state = state, innerPadding = innerPadding)
    }
}

@Composable
private fun SmartCleanerBody(
    state: SmartCleanerViewModel.UiState,
    innerPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        when {
            state.isAnalyzing && state.report.suggestions.isEmpty() ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.report.suggestions.isEmpty() -> EmptyState(
                icon  = Icons.Outlined.AutoFixHigh,
                title = stringResource(R.string.smart_cleaner_empty_title),
                body  = stringResource(R.string.smart_cleaner_empty_body),
            )
            else -> SmartCleanerList(state = state)
        }
    }
}

@Composable
private fun SmartCleanerList(state: SmartCleanerViewModel.UiState) {
    val context = LocalContext.current
    val totalLabel = remember(state.report.totalReclaimableBytes) {
        Formatter.formatShortFileSize(context, state.report.totalReclaimableBytes)
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text       = totalLabel,
                        style      = MaterialTheme.typography.displaySmall,
                        color      = BrandBlue,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text  = stringResource(
                            R.string.smart_cleaner_subtitle,
                            totalLabel,
                            state.report.suggestions.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(items = state.report.suggestions, key = { it.appInfo.packageName + it.category.name }) { suggestion ->
            SuggestionRow(suggestion = suggestion)
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: SmartSuggestion) {
    val context = LocalContext.current
    val sizeLabel = remember(suggestion.reclaimableBytes) {
        Formatter.formatShortFileSize(context, suggestion.reclaimableBytes)
    }
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = suggestion.appInfo.packageName, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = suggestion.appInfo.label,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category = suggestion.category)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text  = suggestion.reasonShort,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text       = sizeLabel,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = BrandBlue,
            )
            Text(
                text  = actionLabel(suggestion.recommendedAction),
                style = MaterialTheme.typography.labelSmall,
                color = when (suggestion.recommendedAction) {
                    SmartSuggestion.RecommendedAction.UNINSTALL          -> BrandDanger
                    SmartSuggestion.RecommendedAction.CLEAR_CACHE        -> MaterialTheme.colorScheme.tertiary
                    SmartSuggestion.RecommendedAction.REVIEW_AND_DECIDE  -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: SmartSuggestion.Category) {
    val (color, label) = when (category) {
        SmartSuggestion.Category.ZOMBIE_NEVER_OPENED   -> BrandDanger to stringResource(R.string.smart_cleaner_category_zombie_never)
        SmartSuggestion.Category.ZOMBIE_UNUSED_LONG    -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.smart_cleaner_category_zombie_unused)
        SmartSuggestion.Category.CACHE_HOG             -> MaterialTheme.colorScheme.secondary to stringResource(R.string.smart_cleaner_category_cache_hog)
        SmartSuggestion.Category.OVERSIZED_RARELY_USED -> BrandBlue to stringResource(R.string.smart_cleaner_category_oversized)
        SmartSuggestion.Category.DUPLICATE_CATEGORY    -> MaterialTheme.colorScheme.primary to stringResource(R.string.smart_cleaner_category_duplicate)
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

@Composable
private fun actionLabel(action: SmartSuggestion.RecommendedAction): String = stringResource(
    when (action) {
        SmartSuggestion.RecommendedAction.UNINSTALL          -> R.string.smart_cleaner_action_uninstall
        SmartSuggestion.RecommendedAction.CLEAR_CACHE        -> R.string.smart_cleaner_action_clear_cache
        SmartSuggestion.RecommendedAction.REVIEW_AND_DECIDE  -> R.string.smart_cleaner_action_review
    }
)
