package com.filestech.appmanager.ui.screens.rarelyused

import android.text.format.Formatter
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.ext.MS_PER_DAY
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppInfo
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.dialogs.RadioPickerDialog
import com.filestech.appmanager.ui.components.settings.NavigationRow
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.components.state.ErrorState
import com.filestech.appmanager.ui.components.state.LoadingState

/**
 * Rarely-used apps screen — lists user apps not opened for `thresholdDays`
 * days, sorted oldest first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RarelyUsedScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: RarelyUsedViewModel = hiltViewModel(),
) {
    val outcome by viewModel.listOutcome.collectAsStateWithLifecycle()
    val threshold by viewModel.thresholdDays.collectAsStateWithLifecycle()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

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
                title = { BrandedTitle(stringResource(R.string.screen_rarely_used_title)) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        ) {
            NavigationRow(
                title       = stringResource(R.string.rarely_used_threshold_title),
                onClick     = { pickerOpen = true },
                currentValue = stringResource(R.string.cleaner_rarely_used_value, threshold),
            )
            RarelyUsedBody(outcome = outcome, onItemClick = onItemClick)
        }
    }

    if (pickerOpen) {
        RadioPickerDialog(
            title    = stringResource(R.string.rarely_used_threshold_title),
            options  = RarelyUsedViewModel.THRESHOLD_OPTIONS,
            selected = threshold,
            labelOf  = { stringResource(R.string.cleaner_rarely_used_value, it) },
            onSelect = viewModel::setThresholdDays,
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun RarelyUsedBody(
    outcome: Outcome<List<AppInfo>>,
    onItemClick: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (val o = outcome) {
            Outcome.Loading      -> LoadingState()
            is Outcome.Failure   -> ErrorState(message = o.error.toString())
            is Outcome.Success   -> {
                if (o.value.isEmpty()) {
                    EmptyState(
                        icon  = Icons.Outlined.AccessTime,
                        title = stringResource(R.string.rarely_used_empty_title),
                        body  = stringResource(R.string.rarely_used_empty_body),
                    )
                } else {
                    // Hoist `now` so every row reads the same reference point and we
                    // do not recompute System.currentTimeMillis() per recomposition.
                    val now = remember { System.currentTimeMillis() }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = o.value, key = { it.packageName }) { app ->
                            RarelyUsedRow(
                                app     = app,
                                now     = now,
                                onClick = { onItemClick(app.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RarelyUsedRow(app: AppInfo, now: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val daysLabel = remember(app.lastUsedTime, now) {
        if (app.lastUsedTime == 0L) "—"
        else "${((now - app.lastUsedTime) / MS_PER_DAY).toInt()} d"
    }
    val sizeLabel = remember(app.totalSizeBytes) {
        Formatter.formatShortFileSize(context, app.totalSizeBytes)
    }
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName, size = 40.dp)
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
        Text(
            text     = daysLabel,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
