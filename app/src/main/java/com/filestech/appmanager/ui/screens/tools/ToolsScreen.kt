package com.filestech.appmanager.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filestech.appmanager.R
import com.filestech.appmanager.ui.theme.BrandBlue
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * "Outils" tab — 3-column grid of icon cards, one per secondary feature.
 *
 * Layout inspired by Read Files Tech: visually scannable, each tool is a
 * coloured icon + short label inside a Material 3 card. Tap → navigates to
 * the target screen via the supplied callback.
 *
 * The Trash card paints [BrandDanger] (icon + label) so the destructive
 * nature reads at a glance — same brand discipline as the Settings entry and
 * the screen itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onSmartCleaner: () -> Unit,
    onTrackers: () -> Unit,
    onSecurityAudit: () -> Unit,
    onCleaner: () -> Unit,
    onRarelyUsed: () -> Unit,
    onZombies: () -> Unit,
    onPermissionFilter: () -> Unit,
    onStorage: () -> Unit,
    onIgnoreList: () -> Unit,
    onExport: () -> Unit,
    onTransparency: () -> Unit,
    onTrash: () -> Unit,
) {
    // 12 entries — recomputing on each composition is cheaper than remembering
    // a list whose closure keys would force invalidation anyway when the
    // navigation lambdas change identity across recompositions.
    val tools = listOf(
        ToolEntry(R.string.screen_smart_cleaner_title,   Icons.Outlined.AutoFixHigh,         BrandBlue,  onSmartCleaner),
        ToolEntry(R.string.screen_trackers_title,        Icons.Outlined.VisibilityOff,       BrandBlue,  onTrackers),
        ToolEntry(R.string.screen_security_audit_title,  Icons.Outlined.Security,            BrandBlue,  onSecurityAudit),
        ToolEntry(R.string.screen_storage_title,         Icons.Outlined.Apps,                BrandBlue,  onStorage),
        ToolEntry(R.string.settings_tools_rarely_used,   Icons.Outlined.AccessTime,          BrandBlue,  onRarelyUsed),
        ToolEntry(R.string.settings_tools_zombies,       Icons.Outlined.SentimentSatisfied,  BrandBlue,  onZombies),
        ToolEntry(R.string.settings_tools_permission_filter, Icons.Outlined.FilterAlt,       BrandBlue,  onPermissionFilter),
        ToolEntry(R.string.screen_cleaner_title,         Icons.Outlined.CleaningServices,    BrandBlue,  onCleaner),
        ToolEntry(R.string.screen_ignore_list_title,     Icons.Outlined.Block,               BrandBlue,  onIgnoreList),
        ToolEntry(R.string.screen_export_title,          Icons.Outlined.FileDownload,        BrandBlue,  onExport),
        ToolEntry(R.string.screen_transparency_title,    Icons.Outlined.VerifiedUser,        BrandBlue,  onTransparency),
        // Trash — destructive intent → BrandDanger
        ToolEntry(R.string.settings_tools_trash,         Icons.Outlined.Delete,              BrandDanger, onTrash),
    )

    Scaffold(
        // v0.1.1 audit H-1 fix — see HomeShell / AppListScreen for rationale.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tools_screen_title)) })
        },
    ) { innerPadding ->
        ToolsGrid(
            entries      = tools,
            innerPadding = innerPadding,
        )
    }
}

@Composable
private fun ToolsGrid(
    entries: List<ToolEntry>,
    innerPadding: PaddingValues,
) {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(3),
        modifier              = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding        = PaddingValues(12.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = entries, key = { it.titleRes }) { entry ->
            ToolCard(entry = entry)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolCard(entry: ToolEntry) {
    // v0.1.1 audit M-2 fix — Card(onClick=...) instead of Modifier.clickable
    // so TalkBack semantics are merged (single "double-tap to activate"
    // announcement per card) and the M3 ripple comes from the Card itself.
    Card(
        onClick  = entry.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector        = entry.icon,
                    contentDescription = null,
                    tint               = entry.tint,
                    modifier           = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text       = stringResource(entry.titleRes),
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color      = entry.tint,
                    textAlign  = TextAlign.Center,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class ToolEntry(
    val titleRes: Int,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)
