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
     * All migrations in version order. Spread (`*ALL_MIGRATIONS`) into
     * `Room.databaseBuilder(...).addMigrations()`.
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
    )
}
