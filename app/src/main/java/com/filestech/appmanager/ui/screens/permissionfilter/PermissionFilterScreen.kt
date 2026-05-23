package com.filestech.appmanager.ui.screens.permissionfilter

import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.settings.ToggleRow
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.components.state.ErrorState

/**
 * Permission-filter screen — input a permission FQCN and list every user
 * app that declares (or has granted) it.
 *
 * Quick-pick chips speed up the common case (CAMERA / LOCATION / SMS /
 * RECORD_AUDIO / READ_CONTACTS / INTERNET / SYSTEM_ALERT_WINDOW).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionFilterScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: PermissionFilterViewModel = hiltViewModel(),
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
                title = { BrandedTitle(stringResource(R.string.screen_permission_filter_title)) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        ) {
            // Search input
            OutlinedTextField(
                value          = state.permission,
                onValueChange  = viewModel::setPermission,
                singleLine     = true,
                label          = { Text(stringResource(R.string.permission_filter_input_label)) },
                placeholder    = { Text("android.permission.CAMERA") },
                trailingIcon   = {
                    IconButton(onClick = viewModel::search, enabled = !state.isSearching) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.cd_search))
                    }
                },
                modifier       = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Quick-pick chips
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PermissionFilterViewModel.QUICK_PICKS.forEach { perm ->
                    FilterChip(
                        selected = state.permission == perm,
                        onClick  = {
                            viewModel.setPermission(perm)
                            viewModel.search()
                        },
                        label    = { Text(perm.substringAfterLast('.')) },
                    )
                }
            }

            ToggleRow(
                title           = stringResource(R.string.permission_filter_only_granted),
                checked         = state.onlyGranted,
                onCheckedChange = viewModel::setOnlyGranted,
            )

            Button(
                onClick  = viewModel::search,
                enabled  = !state.isSearching && state.permission.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (state.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(stringResource(R.string.permission_filter_search_button))
            }

            Box(modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            ) {
                when (val o = state.outcome) {
                    Outcome.Loading      -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    is Outcome.Failure   -> ErrorState(message = o.error.toString())
                    is Outcome.Success   -> {
                        if (o.value.isEmpty() && state.permission.isNotBlank() && !state.isSearching) {
                            EmptyState(
                                icon  = Icons.Outlined.PrivacyTip,
                                title = stringResource(R.string.permission_filter_empty_title),
                                body  = stringResource(R.string.permission_filter_empty_body),
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(items = o.value, key = { it.packageName }) { app ->
                                    PermAppRow(app = app, onClick = { onItemClick(app.packageName) })
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
private fun PermAppRow(app: AppInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val sizeLabel = remember(app.totalSizeBytes) {
        Formatter.formatShortFileSize(context, app.totalSizeBytes)
    }
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName, size = 36.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = app.label,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text     = "${app.packageName} · $sizeLabel",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
