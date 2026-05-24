package com.filestech.appmanager.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates every additive Room migration:
 *
 * - `MIGRATION_1_2`: v1 row survives, new columns `installer_package` +
 *   `apk_source_dir` appear NULL, new index `index_app_info_installer_package`
 *   is present.
 * - `MIGRATION_2_3` (Phase X Trash): existing v2 row in `app_info` survives,
 *   the new `trash_item` table is created (insert succeeds + PK enforced),
 *   and the index `index_trash_item_added_at` is present.
 * - `MIGRATION_3_4` (v0.2.0 Permission Drift + App Quarantine): existing
 *   `app_info` + `trash_item` data survive; the two new tables
 *   `permission_snapshot` and `quarantine_entry` accept inserts; the three
 *   new indices are present.
 * - `MIGRATION_4_5` (v0.3.0 App Lifecycle History): existing data survives;
 *   the new `app_lifecycle_event` table accepts inserts (with the autoincrement
 *   PK + default `total_size_bytes`); the three new indices are present.
 * - `MIGRATION_5_6` (v0.3.2 OS hibernation surface): existing data survives;
 *   the new `is_hibernated` column on `app_info` reads `0` (false) for
 *   pre-existing rows via the column DEFAULT and accepts INSERT with the
 *   field omitted (DEFAULT applies).
 * - `MIGRATION_6_7` (v0.3.4 APK SHA-256 forensics on lifecycle events):
 *   existing data survives; the new `apk_sha256` TEXT column on
 *   `app_lifecycle_event` reads NULL for pre-existing rows (no DEFAULT
 *   clause needed — the column is NULLable) and accepts INSERT both with
 *   and without the field.
 *
 * Requires the v1..v7 schemas to be exported under `schemas/` (Room plugin
 * handles this — checked into git).
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate_v1_to_v2_preserves_row_and_adds_columns() {
        // 1. Create v1 schema with a sample row.
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO app_info (
                  package_name, label, version_name, version_code,
                  install_size_bytes, cache_size_bytes, data_size_bytes,
                  first_install_time, last_update_time, last_used_time,
                  is_system_app, is_uninstallable, is_enabled, category, cached_at
                ) VALUES (
                  'com.example', 'Example', '1.0', 1,
                  1000, 100, 500,
                  0, 0, 0,
                  0, 1, 1, 'UNDEFINED', 0
                )
                """.trimIndent(),
            )
            close()
        }

        // 2. Run migration to v2.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            2,
            true,
            Migrations.MIGRATION_1_2,
        )

        // 3. Old row survives unchanged.
        val cursor = migrated.query(
            "SELECT package_name, installer_package, apk_source_dir FROM app_info",
        )
        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example")
            assertThat(it.isNull(1)).isTrue() // new column NULL
            assertThat(it.isNull(2)).isTrue() // new column NULL
        }

        // 4. New index exists.
        val idxCursor = migrated.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_app_info_installer_package'",
        )
        idxCursor.use {
            assertThat(it.moveToFirst()).isTrue()
        }

        migrated.close()
    }

    @Test
    fun migrate_v2_to_v3_creates_trash_table_and_preserves_app_info() {
        // 1. Seed v2 database with an app_info row.
        helper.createDatabase(TEST_DB_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO app_info (
                  package_name, label, version_name, version_code,
                  install_size_bytes, cache_size_bytes, data_size_bytes,
                  first_install_time, last_update_time, last_used_time,
                  is_system_app, is_uninstallable, is_enabled, category, cached_at,
                  installer_package, apk_source_dir
                ) VALUES (
                  'com.example', 'Example', '1.0', 1,
                  1000, 100, 500,
                  0, 0, 0,
                  0, 1, 1, 'UNDEFINED', 0,
                  NULL, NULL
                )
                """.trimIndent(),
            )
            close()
        }

        // 2. Run migration to v3.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            3,
            true,
            Migrations.MIGRATION_2_3,
        )

        // 3. trash_item table accepts inserts with the expected schema.
        migrated.execSQL(
            """
            INSERT INTO trash_item (package_name, label, total_size_bytes, added_at)
            VALUES ('com.test', 'Test', 2048, 1700000000000)
            """.trimIndent(),
        )
        val trashCursor = migrated.query(
            "SELECT package_name, label, total_size_bytes, added_at FROM trash_item",
        )
        trashCursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.test")
            assertThat(it.getString(1)).isEqualTo("Test")
            assertThat(it.getLong(2)).isEqualTo(2048L)
            assertThat(it.getLong(3)).isEqualTo(1700000000000L)
        }

        // 4. Index on added_at is present (perf-critical for ORDER BY queries).
        val idxCursor = migrated.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_trash_item_added_at'",
        )
        idxCursor.use {
            assertThat(it.moveToFirst()).isTrue()
        }

        // 5. Pre-existing app_info row survives the migration unchanged.
        val appCursor = migrated.query("SELECT package_name FROM app_info")
        appCursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example")
        }

        migrated.close()
    }

    @Test
    fun migrate_v3_to_v4_creates_drift_and_quarantine_tables_and_preserves_data() {
        // 1. Seed v3 database with one app_info row and one trash_item row.
        helper.createDatabase(TEST_DB_NAME, 3).apply {
            execSQL(
                """
                INSERT INTO app_info (
                  package_name, label, version_name, version_code,
                  install_size_bytes, cache_size_bytes, data_size_bytes,
                  first_install_time, last_update_time, last_used_time,
                  is_system_app, is_uninstallable, is_enabled, category, cached_at,
                  installer_package, apk_source_dir
                ) VALUES (
                  'com.example', 'Example', '1.0', 1,
                  1000, 100, 500, 0, 0, 0,
                  0, 1, 1, 'UNDEFINED', 0,
                  NULL, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO trash_item (package_name, label, total_size_bytes, added_at)
                VALUES ('com.test', 'Test', 2048, 1700000000000)
                """.trimIndent(),
            )
            close()
        }

        // 2. Run migration to v4.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            4,
            true,
            Migrations.MIGRATION_3_4,
        )

        // 3. Pre-existing app_info + trash_item rows survive.
        migrated.query("SELECT package_name FROM app_info").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example")
        }
        migrated.query("SELECT package_name FROM trash_item").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.test")
        }

        // 4. permission_snapshot accepts an insert + round-trips.
        migrated.execSQL(
            """
            INSERT INTO permission_snapshot
              (package_name, permission, granted, captured_at)
            VALUES ('com.example', 'android.permission.CAMERA', 1, 1700000000000)
            """.trimIndent(),
        )
        migrated.query(
            "SELECT package_name, permission, granted, captured_at FROM permission_snapshot",
        ).use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example")
            assertThat(it.getString(1)).isEqualTo("android.permission.CAMERA")
            assertThat(it.getInt(2)).isEqualTo(1)
            assertThat(it.getLong(3)).isEqualTo(1700000000000L)
        }

        // 5. quarantine_entry accepts an insert + applies the default columns.
        migrated.execSQL(
            """
            INSERT INTO quarantine_entry
              (package_name, label, mode, quarantined_at, restore_at, version_name, version_code, apk_backup_uri)
            VALUES ('com.example', 'Example', 'HARD_UNINSTALL', 1700000000000, 1702000000000, '1.0', 1, 'content://x/y')
            """.trimIndent(),
        )
        migrated.query(
            """
            SELECT mode, auto_restore_enabled, notified
            FROM quarantine_entry WHERE package_name = 'com.example'
            """.trimIndent(),
        ).use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("HARD_UNINSTALL")
            assertThat(it.getInt(1)).isEqualTo(1) // default 1
            assertThat(it.getInt(2)).isEqualTo(0) // default 0
        }

        // 6. The three new indices exist.
        val expectedIndices = listOf(
            "index_permission_snapshot_package_name_captured_at",
            "index_permission_snapshot_captured_at",
            "index_quarantine_entry_restore_at",
        )
        for (idx in expectedIndices) {
            migrated.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='$idx'",
            ).use {
                assertThat(it.moveToFirst()).isTrue()
            }
        }

        migrated.close()
    }

    @Test
    fun migrate_v4_to_v5_creates_lifecycle_event_table_and_preserves_data() {
        // 1. Seed v4 database with rows in app_info, trash_item, permission_snapshot,
        //    quarantine_entry to verify pre-existing data survives the migration.
        helper.createDatabase(TEST_DB_NAME, 4).apply {
            execSQL(
                """
                INSERT INTO app_info (
                  package_name, label, version_name, version_code,
                  install_size_bytes, cache_size_bytes, data_size_bytes,
                  first_install_time, last_update_time, last_used_time,
                  is_system_app, is_uninstallable, is_enabled, category, cached_at,
                  installer_package, apk_source_dir
                ) VALUES (
                  'com.example', 'Example', '1.0', 1,
                  1000, 100, 500, 0, 0, 0,
                  0, 1, 1, 'UNDEFINED', 0,
                  NULL, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO trash_item (package_name, label, total_size_bytes, added_at)
                VALUES ('com.test', 'Test', 2048, 1700000000000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO permission_snapshot
                  (package_name, permission, granted, captured_at)
                VALUES ('com.example', 'android.permission.CAMERA', 1, 1700000000000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO quarantine_entry
                  (package_name, label, mode, quarantined_at, restore_at, version_name, version_code, apk_backup_uri)
                VALUES ('com.example', 'Example', 'HARD_UNINSTALL', 1700000000000, 1702000000000, '1.0', 1, 'content://x/y')
                """.trimIndent(),
            )
            close()
        }

        // 2. Run the v4 → v5 migration.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            5,
            true,
            Migrations.MIGRATION_4_5,
        )

        // 3. Pre-existing rows in all four prior tables survive untouched.
        migrated.query("SELECT package_name FROM app_info").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example")
        }
        migrated.query("SELECT package_name FROM trash_item").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.test")
        }
        migrated.query("SELECT permission FROM permission_snapshot").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("android.permission.CAMERA")
        }
        migrated.query("SELECT mode FROM quarantine_entry").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("HARD_UNINSTALL")
        }

        // 4. app_lifecycle_event accepts an insert + applies the default total_size_bytes.
        migrated.execSQL(
            """
            INSERT INTO app_lifecycle_event
              (package_name, label, type, captured_at, version_name, version_code,
               installer_package, granted_dangerous_perms, user_reason)
            VALUES ('com.example', 'Example', 'BASELINE', 1700000000000,
                    '1.0', 1, NULL, '', NULL)
            """.trimIndent(),
        )
        migrated.query(
            """
            SELECT package_name, type, total_size_bytes, granted_dangerous_perms, user_reason
            FROM app_lifecycle_event WHERE package_name = 'com.example'
            """.trimIndent(),
        ).use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example")
            assertThat(it.getString(1)).isEqualTo("BASELINE")
            assertThat(it.getLong(2)).isEqualTo(0L) // default 0
            assertThat(it.getString(3)).isEqualTo("")
            assertThat(it.isNull(4)).isTrue() // user_reason NULL
        }

        // 5. The three new indices exist.
        val expectedIndices = listOf(
            "index_app_lifecycle_event_package_name_captured_at",
            "index_app_lifecycle_event_captured_at",
            "index_app_lifecycle_event_type",
        )
        for (idx in expectedIndices) {
            migrated.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='$idx'",
            ).use {
                assertThat(it.moveToFirst()).isTrue()
            }
        }

        migrated.close()
    }

    @Test
    fun migrate_v5_to_v6_adds_is_hibernated_column_with_default_zero() {
        // 1. Seed v5 with one app_info row (no is_hibernated column yet).
        helper.createDatabase(TEST_DB_NAME, 5).apply {
            execSQL(
                """
                INSERT INTO app_info (
                  package_name, label, version_name, version_code,
                  install_size_bytes, cache_size_bytes, data_size_bytes,
                  first_install_time, last_update_time, last_used_time,
                  is_system_app, is_uninstallable, is_enabled, category, cached_at,
                  installer_package, apk_source_dir
                ) VALUES (
                  'com.example', 'Example', '1.0', 1,
                  1000, 100, 500, 0, 0, 0,
                  0, 1, 1, 'UNDEFINED', 0,
                  NULL, NULL
                )
                """.trimIndent(),
            )
            close()
        }

        // 2. Migrate to v6.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            6,
            true,
            Migrations.MIGRATION_5_6,
        )

        // 3. Pre-existing row reads is_hibernated = 0 via the column DEFAULT.
        migrated.query("SELECT package_name, is_hibernated FROM app_info").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example")
            assertThat(it.getInt(1)).isEqualTo(0)
        }

        // 4. A fresh INSERT without the new column applies the DEFAULT.
        migrated.execSQL(
            """
            INSERT INTO app_info (
              package_name, label, version_name, version_code,
              install_size_bytes, cache_size_bytes, data_size_bytes,
              first_install_time, last_update_time, last_used_time,
              is_system_app, is_uninstallable, is_enabled, category, cached_at,
              installer_package, apk_source_dir
            ) VALUES (
              'com.fresh', 'Fresh', '1.0', 1,
              0, 0, 0, 0, 0, 0,
              0, 1, 1, 'UNDEFINED', 0,
              NULL, NULL
            )
            """.trimIndent(),
        )
        migrated.query("SELECT is_hibernated FROM app_info WHERE package_name = 'com.fresh'").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getInt(0)).isEqualTo(0)
        }

        migrated.close()
    }

    @Test
    fun migrate_v6_to_v7_adds_apk_sha256_column_nullable() {
        // 1. Seed v6 with a lifecycle event row (no apk_sha256 column yet).
        helper.createDatabase(TEST_DB_NAME, 6).apply {
            execSQL(
                """
                INSERT INTO app_lifecycle_event
                  (package_name, label, type, captured_at, version_name, version_code,
                   installer_package, total_size_bytes, granted_dangerous_perms, user_reason)
                VALUES ('com.example', 'Example', 'INSTALLED', 1700000000000,
                        '1.0', 1, NULL, 0, '', NULL)
                """.trimIndent(),
            )
            close()
        }

        // 2. Migrate to v7.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            7,
            true,
            Migrations.MIGRATION_6_7,
        )

        // 3. Pre-existing row preserved + apk_sha256 reads NULL.
        migrated.query(
            "SELECT package_name, type, apk_sha256 FROM app_lifecycle_event",
        ).use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example")
            assertThat(it.getString(1)).isEqualTo("INSTALLED")
            assertThat(it.isNull(2)).isTrue() // new column NULL
        }

        // 4. Fresh INSERT omitting apk_sha256 stores NULL.
        migrated.execSQL(
            """
            INSERT INTO app_lifecycle_event
              (package_name, label, type, captured_at, version_name, version_code,
               installer_package, total_size_bytes, granted_dangerous_perms, user_reason)
            VALUES ('com.fresh', 'Fresh', 'REPLACED', 1700000000001,
                    '1.1', 2, NULL, 0, '', NULL)
            """.trimIndent(),
        )
        migrated.query(
            "SELECT apk_sha256 FROM app_lifecycle_event WHERE package_name = 'com.fresh'",
        ).use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.isNull(0)).isTrue()
        }

        // 5. Fresh INSERT WITH apk_sha256 round-trips a non-null hex string.
        val sampleHex = "0".repeat(64) // valid SHA-256 placeholder
        migrated.execSQL(
            """
            INSERT INTO app_lifecycle_event
              (package_name, label, type, captured_at, version_name, version_code,
               installer_package, total_size_bytes, granted_dangerous_perms, user_reason, apk_sha256)
            VALUES ('com.hashed', 'Hashed', 'INSTALLED', 1700000000002,
                    '1.0', 1, NULL, 0, '', NULL, '$sampleHex')
            """.trimIndent(),
        )
        migrated.query(
            "SELECT apk_sha256 FROM app_lifecycle_event WHERE package_name = 'com.hashed'",
        ).use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo(sampleHex)
        }

        migrated.close()
    }

    private companion object {
        const val TEST_DB_NAME = "migration_test.db"
    }
}
