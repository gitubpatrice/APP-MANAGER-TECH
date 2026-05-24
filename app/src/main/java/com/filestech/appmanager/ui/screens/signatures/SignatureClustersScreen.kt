package com.filestech.appmanager.ui.screens.signatures

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.SignatureCluster
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.components.state.ErrorState
import com.filestech.appmanager.ui.components.state.LoadingState
import com.filestech.appmanager.ui.theme.BrandBlue

/**
 * v0.3.3 — Signature Clusters screen.
 *
 * Lists groups of installed apps sharing the same signing certificate
 * SHA-256. Shared clusters (size > 1) are pinned at the top — they're the
 * actionable insight ("two apps from the same editor").
 *
 * Each card:
 *  - "Shared by N apps" badge for size > 1 clusters; raw count for singletons.
 *  - SHA-256 fingerprint in monospace, ellipsised so the row stays compact.
 *  - List of apps in the cluster (icon + label + package mono + size).
 *  - Tap an app row → drill down to AppDetail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureClustersScreen(
    onBack: () -> Unit,
    onAppClick: (String) -> Unit,
    viewModel: SignatureClustersViewModel = hiltViewModel(),
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
                title = { BrandedTitle(stringResource(R.string.screen_signatures_title)) },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = state.outcome !is Outcome.Loading,
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SignatureBody(
            innerPadding = innerPadding,
            outcome      = state.outcome,
            onAppClick   = onAppClick,
            onRetry      = viewModel::refresh,
        )
    }
}

@Composable
private fun SignatureBody(
    innerPadding: PaddingValues,
    outcome: Outcome<List<SignatureCluster>>,
    onAppClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        when (outcome) {
            Outcome.Loading    -> LoadingState()
            is Outcome.Failure -> ErrorState(message = outcome.error.toString(), onRetry = onRetry)
            is Outcome.Success -> {
                if (outcome.value.isEmpty()) {
                    EmptyState(
                        icon  = Icons.Outlined.Fingerprint,
                        title = stringResource(R.string.signatures_empty_title),
                        body  = stringResource(R.string.signatures_empty_body),
                    )
                } else {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(outcome.value, key = { it.signatureSha256 }) { cluster ->
                            ClusterCard(cluster = cluster, onAppClick = onAppClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClusterCard(
    cluster: SignatureCluster,
    onAppClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val sizeLabel = remember(cluster.totalBytes) {
        Formatter.formatShortFileSize(context, cluster.totalBytes)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header — shared badge (or singleton count) + total size.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (cluster.isShared) BrandBlue.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text     = if (cluster.isShared)
                            stringResource(R.string.signatures_shared_badge, cluster.size)
                        else
                            stringResource(R.string.signatures_solo_badge),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = if (cluster.isShared) BrandBlue
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text     = sizeLabel,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text       = cluster.signatureSha256,
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            cluster.apps.forEach { app ->
                AppRowInCluster(
                    packageName = app.packageName,
                    label       = app.label,
                    onClick     = { onAppClick(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun AppRowInCluster(
    packageName: String,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = packageName, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                style      = MaterialTheme.typography.bodyMedium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text       = packageName,
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
    }
}

