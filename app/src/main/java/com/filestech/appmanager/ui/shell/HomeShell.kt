package com.filestech.appmanager.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.filestech.appmanager.R
import com.filestech.appmanager.ui.screens.applist.AppListScreen
import com.filestech.appmanager.ui.screens.tools.ToolsScreen

/**
 * Root shell with a 2-tab Material 3 [NavigationBar] (Accueil / Outils),
 * mirroring Read Files Tech UX so the user always has a clear discovery
 * path:
 *
 * - **Accueil** = the live installed-apps list (search, sort, filter, batch
 *   actions, refresh, pull-to-refresh).
 * - **Outils** = a 3-column grid of icon cards opening every secondary
 *   feature (Smart Cleaner, Trackers, Security Audit, Rarely-used, Zombies,
 *   Permission filter, Storage, Cleaner, Exclusion list, Export, Transparency,
 *   **Corbeille**).
 *
 * Owns no business state — just the selected tab — and routes every
 * navigation intent to the host NavController via the [onNavigate*] callbacks.
 * That keeps the shell trivially testable and the NavGraph in [AppRoot]
 * uniformly shaped.
 */
@Composable
fun HomeShell(
    onNavigateToDetail: (packageName: String) -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSmartCleaner: () -> Unit,
    onNavigateToTrackers: () -> Unit,
    onNavigateToSecurityAudit: () -> Unit,
    onNavigateToCleaner: () -> Unit,
    onNavigateToRarelyUsed: () -> Unit,
    onNavigateToZombies: () -> Unit,
    onNavigateToPermissionFilter: () -> Unit,
    onNavigateToIgnoreList: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToTransparency: () -> Unit,
    onNavigateToTrash: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick  = { tab = entry },
                        icon     = {
                            Icon(
                                imageVector        = entry.icon,
                                contentDescription = null,
                            )
                        },
                        label    = { Text(stringResource(entry.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        // v0.1.1 audit H-1 fix — `consumeWindowInsets(innerPadding)` signals to
        // the sub-screens' own Scaffolds that the system insets corresponding
        // to our BottomBar have already been consumed by the parent. Without
        // this, the child Scaffolds re-apply the navigation-bar window-insets
        // on top of our padding, hiding the last list items behind the
        // NavigationBar on devices with gesture navigation.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            when (tab) {
                HomeTab.HOME -> AppListScreen(
                    onNavigateToDetail   = onNavigateToDetail,
                    onNavigateToSettings = onNavigateToSettings,
                )
                HomeTab.TOOLS -> ToolsScreen(
                    onSmartCleaner    = onNavigateToSmartCleaner,
                    onTrackers        = onNavigateToTrackers,
                    onSecurityAudit   = onNavigateToSecurityAudit,
                    onCleaner         = onNavigateToCleaner,
                    onRarelyUsed      = onNavigateToRarelyUsed,
                    onZombies         = onNavigateToZombies,
                    onPermissionFilter = onNavigateToPermissionFilter,
                    onStorage         = onNavigateToStorage,
                    onIgnoreList      = onNavigateToIgnoreList,
                    onExport          = onNavigateToExport,
                    onTransparency    = onNavigateToTransparency,
                    onTrash           = onNavigateToTrash,
                )
            }
        }
    }
}

private enum class HomeTab(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: Int,
) {
    HOME(Icons.Outlined.Apps, R.string.bottom_nav_home),
    TOOLS(Icons.Outlined.GridView, R.string.bottom_nav_tools),
}
