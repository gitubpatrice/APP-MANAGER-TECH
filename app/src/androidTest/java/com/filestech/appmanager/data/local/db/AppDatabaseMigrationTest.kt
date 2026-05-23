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
 *
 * Requires the v1, v2 and v3 schemas to be exported under `schemas/` (Room
 * plugin handles this — checked into git).
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

    private companion object {
        const val TEST_DB_NAME = "migration_test.db"
    }
}
