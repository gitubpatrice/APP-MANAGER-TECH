package com.filestech.appmanager.ui.screens.journal

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filestech.appmanager.R
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AmtActionEvent
import com.filestech.appmanager.domain.model.AmtActionResult
import com.filestech.appmanager.domain.model.AmtActionType
import com.filestech.appmanager.domain.usecase.ObserveAmtActionsUseCase
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
 * v0.4.0 — Action Journal screen.
 *
 * Layout :
 *  - TopAppBar `BrandedTitle("Journal d'actions")`.
 *  - 3-segment window picker (30 jours / 90 jours / Tout) — same shape
 *    as the LifecycleHistory picker for consistency.
 *  - LazyColumn timeline (newest first). Each row :
 *    - colored action-type badge with icon (UNINSTALL / FORCE_STOP /
 *      DISABLE / ENABLE / CLEAR_CACHE / CLEAR_DATA / MOVE_TO_TRASH /
 *      RESTORE_FROM_TRASH / QUARANTINE_HARD / QUARANTINE_SOFT),
 *    - app icon + label (falls back to packageName),
 *    - relative timestamp + the result chip
 *      (INTENT_REQUESTED / SUCCESS / FAILED).
 *
 * Tap is intentionally a no-op — this is a forensic read-only journal
 * (the user can navigate to AppDetail via the AppList / Tools cards if
 * they need to act on a package).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionJournalScreen(
    onBack: () -> Unit,
    viewModel: ActionJournalViewModel = hiltViewModel(),
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
                title = { BrandedTitle(stringResource(R.string.screen_action_journal_title)) },
            )
        },
    ) { innerPadding ->
        Body(
            innerPadding = innerPadding,
            window       = window,
            events       = events,
            onWindow     = viewModel::setWindow,
        )
    }
}

@Composable
private fun Body(
    innerPadding: PaddingValues,
    window: ObserveAmtActionsUseCase.Window,
    events: Outcome<List<AmtActionEvent>>,
    onWindow: (ObserveAmtActionsUseCase.Window) -> Unit,
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
                        icon  = Icons.Outlined.History,
                        title = stringResource(R.string.action_journal_empty_title),
                        body  = stringResource(R.string.action_journal_empty_body),
                    )
                } else {
                    Timeline(events = events.value)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowPicker(
    window: ObserveAmtActionsUseCase.Window,
    onWindow: (ObserveAmtActionsUseCase.Window) -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WindowChip(
            label    = stringResource(R.string.lifecycle_window_30),
            selected = window == ObserveAmtActionsUseCase.Window.LAST_30,
            onClick  = { onWindow(ObserveAmtActionsUseCase.Window.LAST_30) },
        )
        WindowChip(
            label    = stringResource(R.string.lifecycle_window_90),
            selected = window == ObserveAmtActionsUseCase.Window.LAST_90,
            onClick  = { onWindow(ObserveAmtActionsUseCase.Window.LAST_90) },
        )
        WindowChip(
            label    = stringResource(R.string.lifecycle_window_all),
            selected = window == ObserveAmtActionsUseCase.Window.ALL,
            onClick  = { onWindow(ObserveAmtActionsUseCase.Window.ALL) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label) },
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BrandBlue.copy(alpha = 0.18f),
            selectedLabelColor     = BrandBlue,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled             = true,
            selected            = selected,
            borderColor         = MaterialTheme.colorScheme.outline,
            selectedBorderColor = BrandBlue,
        ),
    )
}

@Composable
private fun Timeline(events: List<AmtActionEvent>) {
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(events, key = { it.id }) { event ->
            ActionRow(event = event, dateFormat = dateFormat)
        }
    }
}

@Composable
private fun ActionRow(event: AmtActionEvent, dateFormat: DateFormat) {
    val (typeLabel, typeColor, typeIcon) = actionTypePresentation(event.actionType)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(label = typeLabel, color = typeColor, icon = typeIcon)
                Spacer(Modifier.width(8.dp))
                AppIcon(packageName = event.packageName, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = event.labelSnapshot ?: event.packageName,
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
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text  = dateFormat.format(Date(event.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                ResultChip(event.result)
            }
        }
    }
}

@Composable
private fun TypeBadge(label: String, color: Color, icon: ImageVector) {
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
private fun ResultChip(result: AmtActionResult) {
    val (label, color) = resultPresentation(result)
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            color    = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * v0.4.0 — Centralised AppTag-style mapping : presentation triple for
 * one [AmtActionType]. Returning a Triple keeps the call site dense
 * and avoids a `when` expression scattered across `ActionRow`. Reused
 * for any future surface (Settings stats card, future per-app journal
 * dialog…).
 */
@Composable
private fun actionTypePresentation(type: AmtActionType): Triple<String, Color, ImageVector> {
    val danger    = BrandDanger
    val primary   = BrandBlue
    val muted     = MaterialTheme.colorScheme.onSurfaceVariant
    return when (type) {
        AmtActionType.UNINSTALL          -> Triple(
            stringResource(R.string.action_journal_type_uninstall),
            danger,
            Icons.Outlined.Delete,
        )
        AmtActionType.FORCE_STOP         -> Triple(
            stringResource(R.string.action_journal_type_force_stop),
            primary,
            Icons.Outlined.Stop,
        )
        AmtActionType.DISABLE            -> Triple(
            stringResource(R.string.action_journal_type_disable),
            danger,
            Icons.Outlined.Block,
        )
        AmtActionType.ENABLE             -> Triple(
            stringResource(R.string.action_journal_type_enable),
            primary,
            Icons.Outlined.CheckCircle,
        )
        AmtActionType.CLEAR_CACHE        -> Triple(
            stringResource(R.string.action_journal_type_clear_cache),
            primary,
            Icons.Outlined.Refresh,
        )
        AmtActionType.CLEAR_DATA         -> Triple(
            stringResource(R.string.action_journal_type_clear_data),
            danger,
            Icons.Outlined.DeleteForever,
        )
        AmtActionType.MOVE_TO_TRASH      -> Triple(
            stringResource(R.string.action_journal_type_move_to_trash),
            danger,
            Icons.Outlined.Inventory2,
        )
        AmtActionType.RESTORE_FROM_TRASH -> Triple(
            stringResource(R.string.action_journal_type_restore_from_trash),
            primary,
            Icons.Outlined.Restore,
        )
        AmtActionType.QUARANTINE_HARD    -> Triple(
            stringResource(R.string.action_journal_type_quarantine_hard),
            danger,
            Icons.Outlined.Lock,
        )
        AmtActionType.QUARANTINE_SOFT    -> Triple(
            stringResource(R.string.action_journal_type_quarantine_soft),
            muted,
            Icons.Outlined.LockOpen,
        )
    }
}

@Composable
private fun resultPresentation(result: AmtActionResult): Pair<String, Color> {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (result) {
        AmtActionResult.INTENT_REQUESTED -> Pair(
            stringResource(R.string.action_journal_result_intent_requested),
            muted,
        )
        AmtActionResult.SUCCESS -> Pair(
            stringResource(R.string.action_journal_result_success),
            BrandBlue,
        )
        AmtActionResult.FAILED -> Pair(
            stringResource(R.string.action_journal_result_failed),
            BrandDanger,
        )
    }
}
