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
 * The whole migration chain at once, from schema 1 to the current one. [AppDatabaseMigrationTest] checks
 * each step on its own; a user who never updated since v0.1 jumps them all in one opening, so the chain
 * must be complete and in order ([Migrations.ALL_MIGRATIONS]), every schema must validate against its
 * exported JSON, and the data must come through.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseFullChainMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate_v1_to_latest_in_one_go_keeps_the_data() {
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

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            AppDatabase.SCHEMA_VERSION,
            true,
            *Migrations.ALL_MIGRATIONS,
        )

        migrated.query("SELECT package_name, label FROM app_info").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("com.example")
            assertThat(it.getString(1)).isEqualTo("Example")
        }
        // The tables added on the way exist, and the last column is there.
        migrated.query("SELECT requested_dangerous_perms FROM app_lifecycle_event").use {
            assertThat(it.count).isEqualTo(0)
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB_NAME = "full_chain_migration_test.db"
    }
}
