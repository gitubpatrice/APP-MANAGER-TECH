package com.filestech.appmanager.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.filestech.appmanager.data.local.db.dao.AmtActionEventDao
import com.filestech.appmanager.data.local.db.dao.AppInfoDao
import com.filestech.appmanager.data.local.db.dao.AppLifecycleEventDao
import com.filestech.appmanager.data.local.db.dao.PermissionSnapshotDao
import com.filestech.appmanager.data.local.db.dao.QuarantineEntryDao
import com.filestech.appmanager.data.local.db.dao.TrashItemDao
import com.filestech.appmanager.data.local.db.entity.AmtActionEventEntity
import com.filestech.appmanager.data.local.db.entity.AppInfoEntity
import com.filestech.appmanager.data.local.db.entity.AppLifecycleEventEntity
import com.filestech.appmanager.data.local.db.entity.PermissionSnapshotEntity
import com.filestech.appmanager.data.local.db.entity.QuarantineEntryEntity
import com.filestech.appmanager.data.local.db.entity.TrashItemEntity

/**
 * Room database for App Manager Tech.
 *
 * Schema version history (every bump MUST ship a matching additive Migration
 * in [Migrations] AND a matching `*MigrationTest` in androidTest/):
 *
 * - v1 (2026-05-23, Phase II initial)
 *     `app_info` (15 cols): packageName PK, label, versionName/Code,
 *     install/cache/data sizes, first/lastUpdate/lastUsed times,
 *     is_system_app, is_uninstallable, is_enabled, category, cached_at.
 *     Indices: is_system_app, last_used_time.
 *
 * - v2 (2026-05-23, Phase II.J SD Maid enrichments)
 *     `app_info` adds:
 *     - `installer_package` TEXT NULL — install provenance (Play / F-Droid / sideload)
 *     - `apk_source_dir`    TEXT NULL — base APK path for Phase VI extract feature
 *     New index: `installer_package` (for "filter by installer source" UX).
 *     Migration `MIGRATION_1_2`: ALTER TABLE ADD COLUMN x2 + CREATE INDEX (idempotent).
 *
 * - v3 (2026-05-23, Phase X Corbeille / Trash feature)
 *     Adds `trash_item` table — soft-delete staging area. Apps moved to the
 *     trash stay installed until the user explicitly uninstalls them via the
 *     system intent. Indexed on `added_at` for chronological listing.
 *     Migration `MIGRATION_2_3`: CREATE TABLE + CREATE INDEX IF NOT EXISTS.
 *
 * - v4 (v0.2.0 — Permission Drift Tracker + App Quarantine)
 *     Adds TWO independent tables in a single migration (one release, one
 *     schema bump):
 *     - `permission_snapshot` — append-only history of dangerous-permission
 *       state per (package, permission). Powers the Drift Tracker feature.
 *       Indexed on `(package_name, captured_at)` and `captured_at`.
 *     - `quarantine_entry`   — one row per quarantined app. Powers the App
 *       Quarantine feature (HARD_UNINSTALL with APK backup + SOFT_REMINDER).
 *       Indexed on `restore_at` for the worker's expiry sweep.
 *     Migration `MIGRATION_3_4`: 2× CREATE TABLE + 3× CREATE INDEX IF NOT EXISTS.
 *
 * - v5 (v0.3.0 — App Lifecycle History)
 *     Adds `app_lifecycle_event` append-only log: one row per OS broadcast
 *     (`PACKAGE_ADDED`/`_REMOVED`/`_REPLACED`) plus a one-shot BASELINE row
 *     per already-installed app inserted on first launch post-upgrade. The
 *     log carries snapshot metadata (version, installer, total size, granted
 *     dangerous permissions) so the row stays meaningful even after the app
 *     is uninstalled and `PackageManager` can no longer resolve it. Optional
 *     `user_reason` (enum) captured via the uninstall dialog.
 *     Indices: `(package_name, captured_at)`, `captured_at`, `type`.
 *     Migration `MIGRATION_4_5`: 1× CREATE TABLE + 3× CREATE INDEX IF NOT EXISTS.
 *
 * - v6 (v0.3.2 — OS hibernation surface)
 *     Adds `is_hibernated` boolean column to `app_info`. Populated by the
 *     next rescan via `UsageStatsManager.isAppInactive(pkg)` (API 23+);
 *     pre-existing rows read 0 (false) via DEFAULT until that scan runs.
 *     Migration `MIGRATION_5_6`: 1× ALTER TABLE ADD COLUMN.
 *
 * - v7 (v0.3.4 — APK SHA-256 forensics on lifecycle events)
 *     Adds `apk_sha256` TEXT NULLable column to `app_lifecycle_event`.
 *     Captured by `RecordLifecycleEventUseCase` for INSTALLED / REPLACED /
 *     BASELINE rows by hashing `ApplicationInfo.sourceDir` with
 *     `MessageDigest.getInstance("SHA-256")`. UNINSTALLED rows stay NULL
 *     (the APK is already gone by the time the broadcast fires). The
 *     LifecycleHistoryScreen surfaces a red tamper alert when a REPLACED
 *     row shares the same `versionCode` as the previous event but a
 *     different `apkSha256` — signal of a repackage or sideload swap.
 *     Migration `MIGRATION_6_7`: 1× ALTER TABLE ADD COLUMN (NULLable).
 *
 * - v8 (v0.4.0 — AMT action journal)
 *     Adds `amt_action_event` append-only table — one row per
 *     destructive / state-changing action AMT itself fires
 *     (Uninstall, ForceStop, Disable/Enable, ClearCache/Data,
 *     MoveToTrash, RestoreFromTrash, Quarantine HARD/SOFT). Distinct
 *     from `app_lifecycle_event` which tracks OS broadcasts from any
 *     installer / uninstaller (not only AMT). Powers the
 *     ActionJournalScreen forensic timeline. Three indices
 *     `(package_name, timestamp)` / `timestamp` / `action_type`.
 *     Migration `MIGRATION_7_8`: 1× CREATE TABLE + 3× CREATE INDEX
 *     IF NOT EXISTS.
 *
 * Migration rules (STRICT — enforced by code review):
 * - Every version bump MUST ship an additive Migration in [Migrations].
 * - Only `ALTER TABLE ... ADD COLUMN`, `CREATE INDEX IF NOT EXISTS`, `CREATE TABLE` are allowed.
 * - `DROP`, `RENAME`, column-type changes are PROHIBITED.
 * - `fallbackToDestructiveMigration` is NEVER used — data integrity > convenience.
 * - Each migration must ship with a `MigrationTest` in `androidTest/`.
 */
@Database(
    version = AppDatabase.SCHEMA_VERSION,
    exportSchema = true,
    entities = [
        AppInfoEntity::class,
        TrashItemEntity::class,
        PermissionSnapshotEntity::class,
        QuarantineEntryEntity::class,
        AppLifecycleEventEntity::class,
        AmtActionEventEntity::class,
    ],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appInfoDao(): AppInfoDao
    abstract fun trashItemDao(): TrashItemDao
    abstract fun permissionSnapshotDao(): PermissionSnapshotDao
    abstract fun quarantineEntryDao(): QuarantineEntryDao
    abstract fun appLifecycleEventDao(): AppLifecycleEventDao
    abstract fun amtActionEventDao(): AmtActionEventDao

    companion object {
        const val DATABASE_NAME = "app_manager_tech.db"
        const val SCHEMA_VERSION = 8
    }
}
