package com.filestech.appmanager.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.filestech.appmanager.ui.screens.about.AboutScreen
import com.filestech.appmanager.ui.screens.appdetail.AppDetailScreen
import com.filestech.appmanager.ui.screens.cleaner.CleanerSettingsScreen
import com.filestech.appmanager.ui.shell.HomeShell
import com.filestech.appmanager.ui.screens.expert.ExpertScreen
import com.filestech.appmanager.ui.screens.export.ExportScreen
import com.filestech.appmanager.ui.screens.ignorelist.IgnoreListScreen
import com.filestech.appmanager.ui.screens.lifecycle.LifecycleHistoryScreen
import com.filestech.appmanager.ui.screens.lifecycle.LifecycleReasonHost
import com.filestech.appmanager.ui.screens.permissiondrift.PermissionDriftScreen
import com.filestech.appmanager.ui.screens.permissionfilter.PermissionFilterScreen
import com.filestech.appmanager.ui.screens.quarantine.QuarantinePickerScreen
import com.filestech.appmanager.ui.screens.quarantine.QuarantineScreen
import com.filestech.appmanager.ui.screens.rarelyused.RarelyUsedScreen
import com.filestech.appmanager.ui.screens.securityaudit.SecurityAuditScreen
import com.filestech.appmanager.ui.screens.settings.SettingsScreen
import com.filestech.appmanager.ui.screens.smartcleaner.SmartCleanerScreen
import com.filestech.appmanager.ui.screens.storage.StorageScreen
import com.filestech.appmanager.ui.screens.trackers.TrackersScreen
import com.filestech.appmanager.ui.screens.transparency.TransparencyScreen
import com.filestech.appmanager.ui.screens.trash.TrashScreen
import com.filestech.appmanager.ui.screens.zombies.ZombiesScreen

/**
 * Root composable — owns the [NavHost] and defines all navigation routes.
 *
 * Navigation graph (Phase X final):
 *
 *   AppList (start) ──► AppDetail(packageName)
 *        └──────────► Storage
 *        └──────────► Settings ──► About
 *                              ├──► Cleaner             (Phase VIII.B)
 *                              ├──► IgnoreList          (Phase VIII.B)
 *                              ├──► Export              (Phase VIII.B)
 *                              ├──► SecurityAudit       (Phase VIII.B)
 *                              ├──► SmartCleaner        (Phase IX innovation)
 *                              ├──► Trackers            (Phase IX innovation)
 *                              ├──► Transparency        (Phase IX innovation)
 *                              ├──► RarelyUsed          (Phase X — list-by-criterion)
 *                              ├──► Zombies             (Phase X — list-by-criterion)
 *                              ├──► PermissionFilter    (Phase X — list-by-criterion)
 *                              └──► Trash               (Phase X — soft-delete staging)
 *
 * From RarelyUsed / Zombies / PermissionFilter the user can drill down into
 * AppDetail(packageName) — same destination as from the main app list.
 *
 * Route arguments use `Uri.encode`/auto-decode via Nav-Compose for safety
 * against package names with reserved URI characters.
 *
 * `launchSingleTop = true` is applied to the Phase X tool routes (RarelyUsed,
 * Zombies, PermissionFilter, Trash) so a double-tap from Settings does not
 * stack two copies on the back stack.
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()

    // v0.3.0 — global host for the uninstall-reason dialog. Mounted once
    // outside the NavHost so the dialog can pop on ANY destination when
    // PackageMonitor records an UNINSTALLED event.
    LifecycleReasonHost()

    NavHost(
        navController = navController,
        startDestination = NavRoute.AppList.route,
    ) {
        composable(NavRoute.AppList.route) {
            // v0.1.3 — Home shell hosts the 3-tab NavigationBar (Accueil / Outils / À propos).
            // AppList lives under "Accueil", every secondary feature is in the
            // "Outils" grid (incl. Paramètres + À propos shortcuts), and the
            // "À propos" tab renders AboutScreen as tab content (no back arrow).
            HomeShell(
                onNavigateToDetail           = { pkg -> navController.navigate(NavRoute.AppDetail.buildRoute(pkg)) },
                onNavigateToStorage          = { navController.navigate(NavRoute.Storage.route) },
                onNavigateToSettings         = { navController.navigate(NavRoute.Settings.route) },
                onNavigateToSmartCleaner     = { navController.navigate(NavRoute.SmartCleaner.route) },
                onNavigateToTrackers         = { navController.navigate(NavRoute.Trackers.route) },
                onNavigateToSecurityAudit    = { navController.navigate(NavRoute.SecurityAudit.route) },
                onNavigateToCleaner          = { navController.navigate(NavRoute.Cleaner.route) },
                onNavigateToRarelyUsed       = { navController.navigate(NavRoute.RarelyUsed.route) { launchSingleTop = true } },
                onNavigateToZombies          = { navController.navigate(NavRoute.Zombies.route) { launchSingleTop = true } },
                onNavigateToPermissionFilter = { navController.navigate(NavRoute.PermissionFilter.route) { launchSingleTop = true } },
                onNavigateToIgnoreList       = { navController.navigate(NavRoute.IgnoreList.route) },
                onNavigateToExport           = { navController.navigate(NavRoute.Export.route) },
                onNavigateToTransparency     = { navController.navigate(NavRoute.Transparency.route) },
                onNavigateToTrash            = { navController.navigate(NavRoute.Trash.route) { launchSingleTop = true } },
                onNavigateToPermissionDrift  = { navController.navigate(NavRoute.PermissionDrift.route) { launchSingleTop = true } },
                onNavigateToQuarantine       = { navController.navigate(NavRoute.Quarantine.route) { launchSingleTop = true } },
                onNavigateToLifecycle        = { navController.navigate(NavRoute.Lifecycle.route) { launchSingleTop = true } },
            )
        }

        composable(
            route = NavRoute.AppDetail.route,
            arguments = listOf(
                navArgument(NavRoute.AppDetail.ARG_PACKAGE) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val pkg = backStackEntry.arguments
                ?.getString(NavRoute.AppDetail.ARG_PACKAGE)
                .orEmpty()
            AppDetailScreen(
                packageName = pkg,
                onBack      = { navController.popBackStack() },
                // v0.2.1 UX add — wire the "Voir la corbeille" shortcut that
                // appears after a successful Move-to-trash from AppDetail.
                onOpenTrash = { navController.navigate(NavRoute.Trash.route) { launchSingleTop = true } },
                // v0.2.2 — "Mode expert" deep-dive into the app's low-level
                // components (activities/services/receivers/providers/perms/
                // signature/SDK/ABI/APK paths/app-ops best-effort).
                onOpenExpert = { navController.navigate(NavRoute.Expert.buildRoute(pkg)) { launchSingleTop = true } },
            )
        }

        composable(NavRoute.Storage.route) {
            StorageScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoute.Settings.route) {
            SettingsScreen(
                onBack               = { navController.popBackStack() },
                onOpenAbout          = { navController.navigate(NavRoute.About.route) },
                onOpenCleaner        = { navController.navigate(NavRoute.Cleaner.route) },
                onOpenIgnoreList     = { navController.navigate(NavRoute.IgnoreList.route) },
                onOpenExport         = { navController.navigate(NavRoute.Export.route) },
                onOpenSecurityAudit  = { navController.navigate(NavRoute.SecurityAudit.route) },
                onOpenSmartCleaner   = { navController.navigate(NavRoute.SmartCleaner.route) },
                onOpenTrackers       = { navController.navigate(NavRoute.Trackers.route) },
                onOpenTransparency   = { navController.navigate(NavRoute.Transparency.route) },
                // Phase X tools — launchSingleTop so a double-tap doesn't stack copies.
                onOpenRarelyUsed     = { navController.navigate(NavRoute.RarelyUsed.route) { launchSingleTop = true } },
                onOpenZombies        = { navController.navigate(NavRoute.Zombies.route) { launchSingleTop = true } },
                onOpenPermissionFilter = { navController.navigate(NavRoute.PermissionFilter.route) { launchSingleTop = true } },
                onOpenTrash          = { navController.navigate(NavRoute.Trash.route) { launchSingleTop = true } },
                // v0.2.1 audit H3 fix — v0.2.0 routes existed in AppRoot but
                // were never wired into Settings → Outils. Adding the
                // launchSingleTop nav so the section is now complete.
                onOpenPermissionDrift = { navController.navigate(NavRoute.PermissionDrift.route) { launchSingleTop = true } },
                onOpenQuarantine     = { navController.navigate(NavRoute.Quarantine.route) { launchSingleTop = true } },
            )
        }

        composable(NavRoute.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        // Phase VIII.B — dedicated screens for advanced features.

        composable(NavRoute.Cleaner.route) {
            CleanerSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoute.IgnoreList.route) {
            IgnoreListScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoute.Export.route) {
            ExportScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoute.SecurityAudit.route) {
            SecurityAuditScreen(
                onBack       = { navController.popBackStack() },
                onAppClick   = { pkg -> navController.navigate(NavRoute.AppDetail.buildRoute(pkg)) },
            )
        }

        // Phase IX — innovation screens (Smart Cleaner, Trackers, Transparency).

        composable(NavRoute.SmartCleaner.route) {
            SmartCleanerScreen(
                onBack       = { navController.popBackStack() },
                onAppClick   = { pkg -> navController.navigate(NavRoute.AppDetail.buildRoute(pkg)) },
            )
        }

        composable(NavRoute.Trackers.route) {
            TrackersScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoute.Transparency.route) {
            TransparencyScreen(onBack = { navController.popBackStack() })
        }

        // Phase X — list / filter / trash screens (use cases already shipped, screens new).

        composable(NavRoute.RarelyUsed.route) {
            RarelyUsedScreen(
                onBack      = { navController.popBackStack() },
                onItemClick = { pkg -> navController.navigate(NavRoute.AppDetail.buildRoute(pkg)) },
            )
        }

        composable(NavRoute.Zombies.route) {
            ZombiesScreen(
                onBack      = { navController.popBackStack() },
                onItemClick = { pkg -> navController.navigate(NavRoute.AppDetail.buildRoute(pkg)) },
            )
        }

        composable(NavRoute.PermissionFilter.route) {
            PermissionFilterScreen(
                onBack      = { navController.popBackStack() },
                onItemClick = { pkg -> navController.navigate(NavRoute.AppDetail.buildRoute(pkg)) },
            )
        }

        composable(NavRoute.Trash.route) {
            TrashScreen(onBack = { navController.popBackStack() })
        }

        // v0.2.0 — Permission Drift Tracker + App Quarantine
        composable(NavRoute.PermissionDrift.route) {
            PermissionDriftScreen(
                onBack     = { navController.popBackStack() },
                onAppClick = { pkg -> navController.navigate(NavRoute.AppDetail.buildRoute(pkg)) },
            )
        }

        composable(NavRoute.Quarantine.route) {
            QuarantineScreen(
                onBack     = { navController.popBackStack() },
                onPickApp  = { navController.navigate(NavRoute.QuarantinePicker.route) { launchSingleTop = true } },
            )
        }

        composable(NavRoute.QuarantinePicker.route) {
            QuarantinePickerScreen(onBack = { navController.popBackStack() })
        }

        // v0.3.0 — App Lifecycle History
        composable(NavRoute.Lifecycle.route) {
            LifecycleHistoryScreen(
                onBack      = { navController.popBackStack() },
                onItemClick = { pkg -> navController.navigate(NavRoute.AppDetail.buildRoute(pkg)) },
            )
        }

        // v0.2.2 — Expert Mode (advanced inspector)
        composable(
            route = NavRoute.Expert.route,
            arguments = listOf(
                navArgument(NavRoute.Expert.ARG_PACKAGE) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val pkg = backStackEntry.arguments
                ?.getString(NavRoute.Expert.ARG_PACKAGE)
                .orEmpty()
            ExpertScreen(
                packageName = pkg,
                onBack      = { navController.popBackStack() },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Type-safe navigation routes
// ---------------------------------------------------------------------------

/**
 * A navigation destination.
 *
 * For destinations with arguments, [route] holds the Navigation-Compose pattern
 * (e.g. `"app_detail/{packageName}"`) used by `composable(route)`, and
 * `buildRoute(...)` returns the concrete URI used by `navController.navigate(...)`.
 */
sealed class NavRoute(val route: String) {

    data object AppList : NavRoute("app_list")

    data object AppDetail : NavRoute("app_detail/{packageName}") {
        const val ARG_PACKAGE = "packageName"

        /** Builds the concrete route URI; encodes [packageName] for navigation safety. */
        fun buildRoute(packageName: String): String =
            "app_detail/${android.net.Uri.encode(packageName)}"
    }

    data object Storage : NavRoute("storage")
    data object Settings : NavRoute("settings")
    data object About : NavRoute("about")

    // Phase VIII.B routes
    data object Cleaner : NavRoute("cleaner")
    data object IgnoreList : NavRoute("ignore_list")
    data object Export : NavRoute("export")
    data object SecurityAudit : NavRoute("security_audit")

    // Phase IX routes (innovation)
    data object SmartCleaner : NavRoute("smart_cleaner")
    data object Trackers : NavRoute("trackers")
    data object Transparency : NavRoute("transparency")

    // Phase X routes (list-by-criterion + trash)
    data object RarelyUsed : NavRoute("rarely_used")
    data object Zombies : NavRoute("zombies")
    data object PermissionFilter : NavRoute("permission_filter")
    data object Trash : NavRoute("trash")

    // v0.2.0 routes
    data object PermissionDrift : NavRoute("permission_drift")
    data object Quarantine : NavRoute("quarantine")
    data object QuarantinePicker : NavRoute("quarantine_picker")

    // v0.2.2 — Expert Mode (advanced inspector accessible from AppDetail)
    data object Expert : NavRoute("expert/{packageName}") {
        const val ARG_PACKAGE = "packageName"
        fun buildRoute(packageName: String): String =
            "expert/${android.net.Uri.encode(packageName)}"
    }

    // v0.3.0 — App Lifecycle History
    data object Lifecycle : NavRoute("lifecycle")
}
