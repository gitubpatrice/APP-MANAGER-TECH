package com.filestech.appmanager.ui.screens.safety

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.domain.model.CriticalCategory
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.settings.SectionHeader
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * v0.3.1 — Apps protégées : Settings sub-screen for the Safety Guardrails
 * user-customisation.
 *
 * Two layers :
 *  - **Read-only** sections per built-in category (AUTHENTICATION, PASSWORD
 *    MANAGERS, BANKING_FR, HEALTH, MESSAGING_E2E, TRANSPORT_FR). Tells the
 *    user EXACTLY which apps trigger the hold-3s — full transparency about
 *    what AMT ships hardcoded.
 *  - **Vos apps protégées** : the user's own packages. Editable: add via the
 *    picker (next screen), remove via the trailing icon button (no swipe to
 *    avoid accidental removal — explicit tap with confirmation dialog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedAppsScreen(
    onBack: () -> Unit,
    onOpenPicker: () -> Unit,
    viewModel: ProtectedAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRemove by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProtectedAppsViewModel.Event.Added ->
                    snackbarHostState.showSnackbar(event.packageName)
                is ProtectedAppsViewModel.Event.Removed ->
                    snackbarHostState.showSnackbar(event.packageName)
                is ProtectedAppsViewModel.Event.ShowError ->
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
                title = { BrandedTitle(stringResource(R.string.screen_protected_apps_title)) },
                actions = {
                    IconButton(onClick = onOpenPicker) {
                        Icon(
                            imageVector        = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.protected_apps_action_add),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        ProtectedAppsBody(
            innerPadding = innerPadding,
            state        = state,
            onRemove     = { pkg -> pendingRemove = pkg },
        )
    }

    pendingRemove?.let { pkg ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title            = { Text(stringResource(R.string.protected_apps_remove_title)) },
            text             = { Text(stringResource(R.string.protected_apps_remove_body, pkg)) },
            confirmButton    = {
                TextButton(onClick = {
                    viewModel.remove(pkg)
                    pendingRemove = null
                }) {
                    Text(stringResource(R.string.protected_apps_remove_confirm), color = BrandDanger)
                }
            },
            dismissButton    = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProtectedAppsBody(
    innerPadding: PaddingValues,
    state: ProtectedAppsViewModel.UiState,
    onRemove: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Intro card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors   = CardDefaults.cardColors(
                containerColor = BrandBlue.copy(alpha = 0.08f),
            ),
        ) {
            Row(
                modifier              = Modifier.padding(16.dp),
                verticalAlignment     = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint               = BrandBlue,
                )
                Text(
                    text  = stringResource(R.string.protected_apps_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // User-added section first (most important — editable)
        SectionHeader(
            stringResource(
                R.string.protected_apps_user_section_n,
                state.userAdded.size,
            ),
        )
        UserAddedCard(entries = state.userAdded, onRemove = onRemove)

        // Built-in sections (read-only) — one card per category
        CriticalCategory.entries.filter { it != CriticalCategory.USER_PROTECTED }.forEach { category ->
            val packages = state.builtIn[category].orEmpty()
            if (packages.isEmpty()) return@forEach
            SectionHeader(
                stringResource(
                    R.string.protected_apps_builtin_section_n,
                    stringResource(categoryLabelRes(category)),
                    packages.size,
                ),
            )
            BuiltInCard(packages = packages)
        }
    }
}

@Composable
private fun UserAddedCard(
    entries: List<ProtectedAppsViewModel.UserProtectedEntry>,
    onRemove: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        if (entries.isEmpty()) {
            Text(
                text     = stringResource(R.string.protected_apps_user_empty),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Column {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider()
                    UserAddedRow(entry = entry, onRemove = { onRemove(entry.packageName) })
                }
            }
        }
    }
}

@Composable
private fun UserAddedRow(
    entry: ProtectedAppsViewModel.UserProtectedEntry,
    onRemove: () -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = entry.packageName, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = entry.label ?: entry.packageName,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text       = entry.packageName,
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector        = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.protected_apps_remove_action_cd),
                tint               = BrandDanger,
            )
        }
    }
}

@Composable
private fun BuiltInCard(packages: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            packages.forEach { pkg ->
                Text(
                    text       = pkg,
                    style      = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier   = Modifier.padding(vertical = 2.dp),
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * v0.3.1 — Companion picker screen: user picks an installed app to add to
 * the user-protected list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedAppsPickerScreen(
    onBack: () -> Unit,
    onPicked: (String) -> Unit,
    viewModel: ProtectedAppsPickerViewModel = hiltViewModel(),
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()

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
                title = { BrandedTitle(stringResource(R.string.protected_apps_picker_title)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(candidates, key = { it.packageName }) { app ->
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .clickable { onPicked(app.packageName) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(packageName = app.packageName, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = app.label,
                            style      = MaterialTheme.typography.bodyMedium,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                        Text(
                            text       = app.packageName,
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun categoryLabelRes(category: CriticalCategory): Int = when (category) {
    CriticalCategory.AUTHENTICATION    -> R.string.critical_category_authentication
    CriticalCategory.PASSWORD_MANAGERS -> R.string.critical_category_password_managers
    CriticalCategory.BANKING_FR        -> R.string.critical_category_banking_fr
    CriticalCategory.HEALTH            -> R.string.critical_category_health
    CriticalCategory.MESSAGING_E2E     -> R.string.critical_category_messaging_e2e
    CriticalCategory.TRANSPORT_FR      -> R.string.critical_category_transport_fr
    CriticalCategory.USER_PROTECTED    -> R.string.critical_category_user_protected
}
