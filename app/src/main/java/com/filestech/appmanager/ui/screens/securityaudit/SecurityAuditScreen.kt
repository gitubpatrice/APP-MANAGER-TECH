package com.filestech.appmanager.ui.screens.securityaudit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.settings.SectionHeader

/**
 * Security audit screen — surfaces apps with elevated OS privileges that are
 * common abuse vectors: device administrators (can block their own uninstall)
 * and accessibility services (can read screen, generate inputs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(
    onBack: () -> Unit,
    viewModel: SecurityAuditViewModel = hiltViewModel(),
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
                title = { Text(stringResource(R.string.screen_security_audit_title)) },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SecurityAuditBody(
            innerPadding         = innerPadding,
            isLoading            = state.isLoading,
            deviceAdmins         = state.deviceAdmins,
            accessibilityServices = state.accessibilityServices,
        )
    }
}

@Composable
private fun SecurityAuditBody(
    innerPadding: PaddingValues,
    isLoading: Boolean,
    deviceAdmins: List<String>,
    accessibilityServices: List<String>,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(32.dp).align(Alignment.TopCenter),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text     = stringResource(R.string.security_audit_subtitle),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            SectionHeader(stringResource(R.string.security_audit_section_device_admin))
            AuditCard(
                description = stringResource(R.string.security_audit_device_admin_desc),
                packages    = deviceAdmins,
            )

            SectionHeader(stringResource(R.string.security_audit_section_accessibility))
            AuditCard(
                description = stringResource(R.string.security_audit_accessibility_desc),
                packages    = accessibilityServices,
            )
        }
    }
}

@Composable
private fun AuditCard(description: String, packages: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (packages.isEmpty()) {
                Text(
                    text     = stringResource(R.string.security_audit_empty),
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                packages.forEach { pkg ->
                    PackageRow(packageName = pkg)
                }
            }
        }
    }
}

@Composable
private fun PackageRow(packageName: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = packageName, size = 32.dp)
        Text(
            text     = packageName,
            style    = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
