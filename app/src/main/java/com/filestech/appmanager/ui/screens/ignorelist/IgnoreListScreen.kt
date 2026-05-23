package com.filestech.appmanager.ui.screens.ignorelist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.ext.MAX_IGNORED_PACKAGES
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.state.EmptyState

/**
 * Ignore-list screen — shows the packages excluded from batch actions and
 * background-scan notifications. Each row has a "Remove" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoreListScreen(
    onBack: () -> Unit,
    viewModel: IgnoreListViewModel = hiltViewModel(),
) {
    val ignored by viewModel.ignored.collectAsStateWithLifecycle()
    val sorted = remember(ignored) { ignored.sortedBy { it.lowercase() } }

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
                title = { BrandedTitle(stringResource(R.string.screen_ignore_list_title)) },
            )
        },
    ) { innerPadding ->
        IgnoreListBody(
            innerPadding = innerPadding,
            packages     = sorted,
            onRemove     = viewModel::remove,
        )
    }
}

@Composable
private fun IgnoreListBody(
    innerPadding: PaddingValues,
    packages: List<String>,
    onRemove: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        if (packages.isEmpty()) {
            EmptyState(
                icon  = Icons.Outlined.Block,
                title = stringResource(R.string.ignore_list_empty_title),
                body  = stringResource(R.string.ignore_list_empty_body),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text     = stringResource(
                            R.string.ignore_list_subtitle,
                            packages.size,
                            MAX_IGNORED_PACKAGES,
                        ),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(items = packages, key = { it }) { pkg ->
                    IgnoreRow(
                        packageName = pkg,
                        onRemove    = { onRemove(pkg) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IgnoreRow(packageName: String, onRemove: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = packageName, size = 36.dp)
        Text(
            text     = packageName,
            style    = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.ignore_list_remove),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
