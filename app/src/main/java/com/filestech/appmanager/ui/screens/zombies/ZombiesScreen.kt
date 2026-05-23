package com.filestech.appmanager.ui.screens.zombies

import android.text.format.Formatter
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.ZombieApp
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.components.state.ErrorState
import com.filestech.appmanager.ui.components.state.LoadingState
import com.filestech.appmanager.ui.theme.BrandDanger

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
    Scaffold(
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
                title = { Text(stringResource(R.string.screen_zombies_title)) },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(items = o.value, key = { it.info.packageName }) { zombie ->
                                ZombieRow(zombie = zombie, onClick = { onItemClick(zombie.info.packageName) })
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
    Row(
        modifier          = Modifier
            .fillMaxWidth()
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
        IconButton(onClick = onClick) {
            Icon(
                imageVector        = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_view_details),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
