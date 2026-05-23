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
     * All migrations in version order. Spread (`*ALL_MIGRATIONS`) into
     * `Room.databaseBuilder(...).addMigrations()`.
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
    )
}
