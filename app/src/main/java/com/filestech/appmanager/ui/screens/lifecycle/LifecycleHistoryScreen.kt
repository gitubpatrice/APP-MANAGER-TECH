package com.filestech.appmanager.ui.screens.lifecycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.LifecycleEvent
import com.filestech.appmanager.domain.model.LifecycleEventType
import com.filestech.appmanager.domain.model.UninstallReason
import com.filestech.appmanager.domain.usecase.ObserveLifecycleEventsUseCase
import com.filestech.appmanager.ui.components.AppIcon
import com.filestech.appmanager.ui.components.BrandedTitle
import com.filestech.appmanager.ui.components.state.EmptyState
import com.filestech.appmanager.ui.components.state.ErrorState
import com.filestech.appmanager.ui.components.state.LoadingState
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.theme.BrandDanger
import java.text.DateFormat
import java.util.Date

/**
 * v0.3.0 — Lifecycle History screen.
 *
 * Layout :
 *  - TopAppBar `BrandedTitle("Historique des apps")`.
 *  - 3-segment window picker (30 jours / 90 jours / Tout).
 *  - LazyColumn timeline (newest first). Each row :
 *    - colored type chip (BASELINE / INSTALLED / UNINSTALLED / REPLACED)
 *    - app icon + label
 *    - installer + version
 *    - relative timestamp
 *    - reason chip if `userReason != null`
 *  - Tap a row → drill-down navigation to AppDetail when the app is still
 *    installed; otherwise a snackbar message "App désinstallée" (kept light
 *    for v0.3.0 — a dedicated detail dialog can land in a follow-up).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifecycleHistoryScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: LifecycleHistoryViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val window by viewModel.window.collectAsStateWithLifecycle()

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
                title = { BrandedTitle(stringResource(R.string.screen_lifecycle_title)) },
            )
        },
    ) { innerPadding ->
        LifecycleBody(
            innerPadding = innerPadding,
            window       = window,
            events       = events,
            onWindow     = viewModel::setWindow,
            onItemClick  = onItemClick,
        )
    }
}

@Composable
private fun LifecycleBody(
    innerPadding: PaddingValues,
    window: ObserveLifecycleEventsUseCase.Window,
    events: Outcome<List<LifecycleEvent>>,
    onWindow: (ObserveLifecycleEventsUseCase.Window) -> Unit,
    onItemClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        WindowPicker(window = window, onWindow = onWindow)
        when (events) {
            Outcome.Loading    -> LoadingState()
            is Outcome.Failure -> ErrorState(message = events.error.toString(), onRetry = {})
            is Outcome.Success -> {
                if (events.value.isEmpty()) {
                    EmptyState(
                        icon  = Icons.Outlined.Inventory2,
                        title = stringResource(R.string.lifecycle_empty_title),
                        body  = stringResource(R.string.lifecycle_empty_body),
                    )
                } else {
                    EventTimeline(events = events.value, onItemClick = onItemClick)
                }
            }
        }
    }
}

@Composable
private fun WindowPicker(
    window: ObserveLifecycleEventsUseCase.Window,
    onWindow: (ObserveLifecycleEventsUseCase.Window) -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WindowChip(
            label    = stringResource(R.string.lifecycle_window_30),
            selected = window == ObserveLifecycleEventsUseCase.Window.LAST_30,
            onClick  = { onWindow(ObserveLifecycleEventsUseCase.Window.LAST_30) },
        )
        WindowChip(
            label    = stringResource(R.string.lifecycle_window_90),
            selected = window == ObserveLifecycleEventsUseCase.Window.LAST_90,
            onClick  = { onWindow(ObserveLifecycleEventsUseCase.Window.LAST_90) },
        )
        WindowChip(
            label    = stringResource(R.string.lifecycle_window_all),
            selected = window == ObserveLifecycleEventsUseCase.Window.ALL,
            onClick  = { onWindow(ObserveLifecycleEventsUseCase.Window.ALL) },
        )
    }
}

@Composable
private fun WindowChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label   = { Text(label) },
        colors  = AssistChipDefaults.assistChipColors(
            containerColor      = if (selected) BrandBlue.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
            labelColor          = if (selected) BrandBlue else MaterialTheme.colorScheme.onSurface,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled      = true,
            borderColor  = if (selected) BrandBlue else MaterialTheme.colorScheme.outline,
        ),
    )
}

@Composable
private fun EventTimeline(
    events: List<LifecycleEvent>,
    onItemClick: (String) -> Unit,
) {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(events, key = { it.id }) { event ->
            EventCard(event = event, dateFormat = dateFormat, onClick = { onItemClick(event.packageName) })
        }
    }
}

@Composable
private fun EventCard(
    event: LifecycleEvent,
    dateFormat: DateFormat,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(event.type)
                Spacer(Modifier.width(8.dp))
                AppIcon(packageName = event.packageName, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = event.label ?: event.packageName,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    Text(
                        text     = event.packageName,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            // Metadata row : version + installer + capturedAt
            Text(
                text  = stringResource(
                    R.string.lifecycle_row_meta,
                    event.versionName ?: "—",
                    event.installerPackage ?: stringResource(R.string.installer_sideload),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text  = dateFormat.format(Date(event.capturedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (event.userReason != null) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = BrandBlue.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text     = stringResource(R.string.lifecycle_row_reason, reasonLabel(event.userReason)),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = BrandBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            // Tap-to-detail row — only meaningful when the app is still
            // installed; the use case for an uninstalled package still
            // navigates to AppDetail which will gracefully show "App not
            // found" via the existing repo flow.
            Spacer(Modifier.height(6.dp))
            Text(
                text     = stringResource(R.string.lifecycle_row_open_detail),
                style    = MaterialTheme.typography.labelSmall,
                color    = BrandBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clickableNoIndication(onClick),
            )
        }
    }
}

@Composable
private fun TypeBadge(type: LifecycleEventType) {
    val (label, color, icon) = when (type) {
        LifecycleEventType.BASELINE    -> Triple(
            stringResource(R.string.lifecycle_type_baseline),
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Outlined.Cached,
        )
        LifecycleEventType.INSTALLED   -> Triple(
            stringResource(R.string.lifecycle_type_installed),
            BrandBlue,
            Icons.Outlined.Download,
        )
        LifecycleEventType.UNINSTALLED -> Triple(
            stringResource(R.string.lifecycle_type_uninstalled),
            BrandDanger,
            Icons.Outlined.Delete,
        )
        LifecycleEventType.REPLACED    -> Triple(
            stringResource(R.string.lifecycle_type_replaced),
            BrandBlue,
            Icons.Outlined.Cached,
        )
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier              = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = color,
                modifier           = Modifier.size(14.dp),
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
private fun reasonLabel(reason: UninstallReason): String = stringResource(
    when (reason) {
        UninstallReason.UNUSED              -> R.string.lifecycle_reason_unused
        UninstallReason.REPLACED_BY_ANOTHER -> R.string.lifecycle_reason_replaced_by_another
        UninstallReason.TOO_HEAVY           -> R.string.lifecycle_reason_too_heavy
        UninstallReason.PRIVACY_TRACKER     -> R.string.lifecycle_reason_privacy_tracker
        UninstallReason.OTHER               -> R.string.lifecycle_reason_other
    },
)

/**
 * Small helper so the "Voir la fiche" affordance keeps a flat look (no
 * ripple) — the row itself is the action; the text is just an explicit cue.
 */
@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = source,
        indication        = null,
        onClick           = onClick,
    )
}
