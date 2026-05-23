package com.filestech.appmanager.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filestech.appmanager.data.local.db.dao.AppInfoDao
import com.filestech.appmanager.data.local.db.entity.AppInfoEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * In-memory CRUD coverage for [AppInfoDao].
 *
 * Uses `Room.inMemoryDatabaseBuilder` so each test gets a fresh schema-v2
 * database with no I/O cost. Verifies:
 * - insert + read by package
 * - observe flow emits after upsert
 * - aggregate sums match raw inserts
 * - updateEnabled persists
 * - deleteByPackage removes only the target
 * - deleteAll wipes everything
 */
@RunWith(AndroidJUnit4::class)
class AppInfoDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AppInfoDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // tests only
            .build()
        dao = db.appInfoDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_and_getByPackage_round_trip() = runBlocking {
        val entity = sample("com.a")
        dao.upsert(entity)

        val read = dao.getByPackage("com.a")
        assertThat(read).isEqualTo(entity)
    }

    @Test
    fun getAll_returns_all_inserted_rows() = runBlocking {
        dao.upsertAll(listOf(sample("com.a"), sample("com.b"), sample("com.c")))

        val all = dao.getAll()
        assertThat(all.map { it.packageName }).containsExactly("com.a", "com.b", "com.c")
    }

    @Test
    fun observeAll_emits_initial_and_after_insert() = runBlocking {
        // Initial empty emission
        val initial = dao.observeAll().first()
        assertThat(initial).isEmpty()

        dao.upsert(sample("com.x"))

        val afterInsert = dao.observeAll().first()
        assertThat(afterInsert.map { it.packageName }).containsExactly("com.x")
    }

    @Test
    fun getUserApps_excludes_system_apps() = runBlocking {
        dao.upsert(sample("com.user.app", isSystem = false))
        dao.upsert(sample("com.system.app", isSystem = true))

        assertThat(dao.getUserApps().map { it.packageName }).containsExactly("com.user.app")
        assertThat(dao.getSystemApps().map { it.packageName }).containsExactly("com.system.app")
    }

    @Test
    fun aggregateSizes_sums_correctly() = runBlocking {
        dao.upsertAll(listOf(
            sample("com.a", install = 100L, cache = 10L, data = 1L),
            sample("com.b", install = 200L, cache = 20L, data = 2L),
        ))

        val agg = dao.aggregateSizes(includeSystem = true)
        assertThat(agg.appCount).isEqualTo(2)
        assertThat(agg.installSum).isEqualTo(300L)
        assertThat(agg.cacheSum).isEqualTo(30L)
        assertThat(agg.dataSum).isEqualTo(3L)
    }

    @Test
    fun aggregateSizes_excludes_system_when_flag_false() = runBlocking {
        dao.upsert(sample("com.user", install = 100L, isSystem = false))
        dao.upsert(sample("com.sys",  install = 999L, isSystem = true))

        val agg = dao.aggregateSizes(includeSystem = false)
        assertThat(agg.appCount).isEqualTo(1)
        assertThat(agg.installSum).isEqualTo(100L)
    }

    @Test
    fun updateEnabled_persists() = runBlocking {
        dao.upsert(sample("com.x", isEnabled = true))
        val updated = dao.updateEnabled("com.x", enabled = false)
        assertThat(updated).isEqualTo(1)
        assertThat(dao.getByPackage("com.x")?.isEnabled).isFalse()
    }

    @Test
    fun deleteByPackage_removes_only_target() = runBlocking {
        dao.upsertAll(listOf(sample("com.a"), sample("com.b")))
        dao.deleteByPackage("com.a")

        assertThat(dao.getAll().map { it.packageName }).containsExactly("com.b")
    }

    @Test
    fun deleteAll_wipes_table() = runBlocking {
        dao.upsertAll(listOf(sample("com.a"), sample("com.b")))
        val removed = dao.deleteAll()

        assertThat(removed).isEqualTo(2)
        assertThat(dao.count()).isEqualTo(0)
    }

    // ---------------------------------------------------------------------------

    private fun sample(
        pkg: String,
        install: Long = 1_000L,
        cache: Long   = 100L,
        data: Long    = 500L,
        isSystem: Boolean = false,
        isEnabled: Boolean = true,
    ) = AppInfoEntity(
        packageName       = pkg,
        label             = pkg,
        versionName       = "1.0",
        versionCode       = 1L,
        installSizeBytes  = install,
        cacheSizeBytes    = cache,
        dataSizeBytes     = data,
        firstInstallTime  = 0L,
        lastUpdateTime    = 0L,
        lastUsedTime      = 0L,
        isSystemApp       = isSystem,
        isUninstallable   = !isSystem,
        isEnabled         = isEnabled,
        category          = "UNDEFINED",
        cachedAt          = 0L,
        installerPackage  = null,
        apkSourceDir      = null,
    )
}
