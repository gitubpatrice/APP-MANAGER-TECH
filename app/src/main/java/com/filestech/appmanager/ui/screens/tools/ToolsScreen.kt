package com.filestech.appmanager.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import com.filestech.appmanager.ui.theme.BrandDanger

/**
 * "Outils" tab — RFT-style premium 2-column grid (v0.1.2 redesign).
 *
 * Visual mirror of Read Files Tech: each tool is a Card with a vibrant icon
 * (no coloured container — the icon itself carries the brand colour) plus a
 * short bold title and a one-line subtitle describing what the tool does.
 *
 * Layout:
 *   - 2 columns (was 3 — RFT uses 2 for breathing room and to fit the
 *     subtitle without truncation).
 *   - childAspectRatio ≈ 1.25 → cards a bit wider than tall.
 *   - 12dp inter-card spacing, 16dp outer padding.
 *   - Card surfaceContainerLow background (matches RFT, avoids the rose
 *     surface tint that Material You used to inject under pink wallpapers).
 *
 * The Trash card paints [BrandDanger] (icon + title + subtitle) so the
 * destructive nature is visible at a glance — consistent with the Settings
 * row and the TrashScreen TopAppBar.
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
    // 12 tools — recomputed each composition (cheap; lambda keys change
    // identity across NavController recompositions anyway).
    val tools = listOf(
        ToolEntry(R.string.screen_smart_cleaner_title,       R.string.tool_subtitle_smart_cleaner,    Icons.Outlined.AutoFixHigh,        ToolColors.Purple,    onSmartCleaner),
        ToolEntry(R.string.screen_trackers_title,            R.string.tool_subtitle_trackers,         Icons.Outlined.VisibilityOff,      ToolColors.Teal,      onTrackers),
        ToolEntry(R.string.screen_security_audit_title,      R.string.tool_subtitle_security_audit,   Icons.Outlined.Security,           ToolColors.Coral,     onSecurityAudit),
        ToolEntry(R.string.screen_storage_title,             R.string.tool_subtitle_storage,          Icons.Outlined.Apps,               ToolColors.Blue,      onStorage),
        ToolEntry(R.string.settings_tools_rarely_used,       R.string.tool_subtitle_rarely_used,      Icons.Outlined.AccessTime,         ToolColors.Amber,     onRarelyUsed),
        ToolEntry(R.string.settings_tools_zombies,           R.string.tool_subtitle_zombies,          Icons.Outlined.SentimentSatisfied, ToolColors.Brown,     onZombies),
        ToolEntry(R.string.settings_tools_permission_filter, R.string.tool_subtitle_permission_filter,Icons.Outlined.FilterAlt,          ToolColors.Indigo,    onPermissionFilter),
        ToolEntry(R.string.screen_cleaner_title,             R.string.tool_subtitle_cleaner,          Icons.Outlined.CleaningServices,   ToolColors.Green,     onCleaner),
        ToolEntry(R.string.screen_ignore_list_title,         R.string.tool_subtitle_ignore_list,      Icons.Outlined.Block,              ToolColors.SoftPurple,onIgnoreList),
        ToolEntry(R.string.screen_export_title,              R.string.tool_subtitle_export,           Icons.Outlined.FileDownload,       ToolColors.DeepPurple,onExport),
        ToolEntry(R.string.screen_transparency_title,        R.string.tool_subtitle_transparency,     Icons.Outlined.VerifiedUser,       ToolColors.Cyan,      onTransparency),
        ToolEntry(R.string.settings_tools_trash,             R.string.tool_subtitle_trash,            Icons.Outlined.Delete,             BrandDanger,          onTrash),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tools_screen_title)) })
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns               = GridCells.Fixed(2),
            modifier              = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding        = PaddingValues(16.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = tools, key = { it.titleRes }) { entry ->
                ToolCard(entry = entry)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolCard(entry: ToolEntry) {
    // v0.1.2 — ElevatedCard for the premium drop-shadow look RFT-style.
    ElevatedCard(
        onClick   = entry.onClick,
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier             = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = entry.icon,
                contentDescription = null,
                tint               = entry.tint,
                modifier           = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text       = stringResource(entry.titleRes),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = entry.tint,
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text      = stringResource(entry.subtitleRes),
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class ToolEntry(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

/**
 * RFT-mirrored vibrant tool palette.
 *
 * v0.1.2 audit fixes:
 * - M-1: DeepPurple was identical to Purple (0xFF7E57C2) — fixed to Material
 *   DeepPurple 700 (0xFF512DA8), distinct hue.
 * - M-2: Blue/Green/Cyan were Material 300-400 pastels — passed WCAG AA on
 *   dark surfaceContainerLow but failed on light (~2.4:1). Bumped to 700
 *   tones (≥ 4.5:1 on both themes for the bodyMedium-sized text).
 */
private object ToolColors {
    val Purple     = Color(0xFF7E57C2)
    val Teal       = Color(0xFF00897B) // Material Teal 600 — AA on light + dark
    val Coral      = Color(0xFFD32F2F) // Material Red 700 — AA both, "danger-soft"
    val Blue       = Color(0xFF1976D2) // Material Blue 700
    val Amber      = Color(0xFFFFB300)
    val Brown      = Color(0xFF6D4C41) // Material Brown 600 — AA on light
    val Indigo     = Color(0xFF3949AB) // Material Indigo 600 — AA both
    val Green      = Color(0xFF388E3C) // Material Green 700
    val SoftPurple = Color(0xFF8E24AA) // Material Purple 600 — AA both
    val DeepPurple = Color(0xFF512DA8) // Material Deep Purple 700 (was duplicate of Purple)
    val Cyan       = Color(0xFF0097A7) // Material Cyan 700
}

