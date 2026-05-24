package com.filestech.appmanager.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * All Room migrations for App Manager Tech, in version order.
 *
 * Naming convention: `MIGRATION_X_Y` where X → Y is the version increment.
 *
 * Rules (STRICT):
 * - Only `ALTER TABLE ... ADD COLUMN`, `CREATE TABLE`, `CREATE INDEX IF NOT EXISTS`.
 * - Never `DROP` or `RENAME` (breaks downgrade-safety).
 * - Each migration must ship with a matching `*MigrationTest` in `androidTest/`.
 * - Use `IF NOT EXISTS` on CREATE INDEX so re-runs of the same migration on
 *   already-migrated databases are idempotent.
 */
object Migrations {

    /**
     * Phase II.J SD Maid enrichments: adds install provenance + APK path for
     * extract feature, plus an index on installer_package for the future
     * "filter by installer" UX.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `app_info` ADD COLUMN `installer_package` TEXT")
            db.execSQL("ALTER TABLE `app_info` ADD COLUMN `apk_source_dir` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_info_installer_package` ON `app_info` (`installer_package`)")
        }
    }

    /**
     * Phase X Trash (Corbeille): adds `trash_item` table — soft-delete staging
     * area. Indexed on `added_at` for chronological listing in the UI.
     *
     * `IF NOT EXISTS` on both statements keeps the migration idempotent under
     * partially-applied / re-run scenarios (e.g. dev rollbacks).
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `trash_item` (
                  `package_name` TEXT NOT NULL,
                  `label` TEXT NOT NULL,
                  `total_size_bytes` INTEGER NOT NULL DEFAULT 0,
                  `added_at` INTEGER NOT NULL,
                  PRIMARY KEY(`package_name`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trash_item_added_at` ON `trash_item` (`added_at`)")
        }
    }

    /**
     * v0.2.0 — Permission Drift Tracker + App Quarantine features.
     *
     * Adds two independent tables in a SINGLE migration so both v0.2.0
     * features ship under one schema bump (avoids two MigrationTests for
     * what is a single user-facing release):
     *
     *  - `permission_snapshot`: append-only history of dangerous-permission
     *    state per (package, permission). Inserted only on change. Indexed
     *    on `(package_name, captured_at)` for fast latest-snapshot lookup
     *    + drift detection, and on `captured_at` alone for retention purge.
     *  - `quarantine_entry`: one active row per package. Indexed on
     *    `restore_at` to power the worker's expired-rows query.
     *
     * Both `CREATE TABLE` + `CREATE INDEX` use `IF NOT EXISTS` for
     * idempotency under partial-rollback dev scenarios.
     */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // permission_snapshot
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `permission_snapshot` (
                  `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  `package_name` TEXT NOT NULL,
                  `permission` TEXT NOT NULL,
                  `granted` INTEGER NOT NULL,
                  `captured_at` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_permission_snapshot_package_name_captured_at` " +
                    "ON `permission_snapshot` (`package_name`, `captured_at`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_permission_snapshot_captured_at` " +
                    "ON `permission_snapshot` (`captured_at`)",
            )

            // quarantine_entry
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `quarantine_entry` (
                  `package_name` TEXT NOT NULL,
                  `label` TEXT NOT NULL,
                  `mode` TEXT NOT NULL,
                  `quarantined_at` INTEGER NOT NULL,
                  `restore_at` INTEGER NOT NULL,
                  `version_name` TEXT,
                  `version_code` INTEGER NOT NULL,
                  `apk_backup_uri` TEXT,
                  `auto_restore_enabled` INTEGER NOT NULL DEFAULT 1,
                  `notified` INTEGER NOT NULL DEFAULT 0,
                  PRIMARY KEY(`package_name`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_quarantine_entry_restore_at` " +
                    "ON `quarantine_entry` (`restore_at`)",
            )
        }
    }

    /**
     * v0.3.0 — App Lifecycle History feature.
     *
     * Adds the `app_lifecycle_event` append-only log: one row per OS broadcast
     * (`PACKAGE_ADDED` / `_REMOVED` / `_REPLACED`) plus a one-shot BASELINE
     * row per already-installed app inserted on first launch post-upgrade.
     *
     * Three indices (each `IF NOT EXISTS` for idempotency):
     *  - `(package_name, captured_at)` composite — per-package timeline +
     *    `latestByPackage` lookup.
     *  - `captured_at` — global timeline + retention purge.
     *  - `type` — "all UNINSTALLED" filter for the reason-aggregation UX.
     */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `app_lifecycle_event` (
                  `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  `package_name` TEXT NOT NULL,
                  `label` TEXT,
                  `type` TEXT NOT NULL,
                  `captured_at` INTEGER NOT NULL,
                  `version_name` TEXT,
                  `version_code` INTEGER NOT NULL,
                  `installer_package` TEXT,
                  `total_size_bytes` INTEGER NOT NULL DEFAULT 0,
                  `granted_dangerous_perms` TEXT,
                  `user_reason` TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_app_lifecycle_event_package_name_captured_at` " +
                    "ON `app_lifecycle_event` (`package_name`, `captured_at`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_app_lifecycle_event_captured_at` " +
                    "ON `app_lifecycle_event` (`captured_at`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_app_lifecycle_event_type` " +
                    "ON `app_lifecycle_event` (`type`)",
            )
        }
    }

    /**
     * v0.3.2 — adds `is_hibernated` boolean column to `app_info`.
     *
     * Strict additive: `ALTER TABLE ADD COLUMN ... DEFAULT 0` — every
     * pre-existing row reads `0` (`false`) until the next full rescan
     * repopulates the real OS hibernation state.
     */
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `app_info` ADD COLUMN `is_hibernated` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /**
     * v0.3.4 — adds `apk_sha256` TEXT column to `app_lifecycle_event`.
     *
     * Carries the hex SHA-256 of the base APK at capture time. NULL is the
     * default for :
     *  - every pre-v0.3.4 row (the column simply did not exist),
     *  - UNINSTALLED rows (the APK is already gone by the time the
     *    broadcast fires, so the use case can't hash anything),
     *  - rows where reading the APK failed (IO/security exception — we
     *    swallow + log, the row is still written so the audit trail stays
     *    monotonic).
     *
     * Strict additive: `ALTER TABLE ADD COLUMN ... TEXT` — NULLable so no
     * DEFAULT clause is necessary, pre-existing rows read NULL.
     */
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `app_lifecycle_event` ADD COLUMN `apk_sha256` TEXT",
            )
        }
    }

    /**
     * v0.4.0 — adds the `amt_action_event` table.
     *
     * Append-only journal of every destructive / state-changing action
     * AMT itself fired. Distinct from `app_lifecycle_event` which tracks
     * OS broadcasts (any installer / uninstaller, not just AMT).
     *
     * Three indices (each `IF NOT EXISTS` for idempotency under partial
     * rollbacks) :
     *  - `(package_name, timestamp)` composite — per-app journal lookup,
     *  - `timestamp` — global timeline + retention purge,
     *  - `action_type` — type-filter (future "all UNINSTALLs" view).
     *
     * Strict additive : CREATE TABLE + CREATE INDEX IF NOT EXISTS — no
     * destructive change to existing tables.
     */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `amt_action_event` (
                  `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  `package_name` TEXT NOT NULL,
                  `label_snapshot` TEXT,
                  `action_type` TEXT NOT NULL,
                  `result` TEXT NOT NULL,
                  `timestamp` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_amt_action_event_package_name_timestamp` " +
                    "ON `amt_action_event` (`package_name`, `timestamp`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_amt_action_event_timestamp` " +
                    "ON `amt_action_event` (`timestamp`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_amt_action_event_action_type` " +
                    "ON `amt_action_event` (`action_type`)",
            )
        }
    }

    /**
     * All migrations in version order. Spread (`*ALL_MIGRATIONS`) into
     * `Room.databaseBuilder(...).addMigrations()`.
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
    )
}
