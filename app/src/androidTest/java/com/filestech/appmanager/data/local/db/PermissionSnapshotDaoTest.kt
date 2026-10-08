package com.filestech.appmanager.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filestech.appmanager.data.local.db.dao.PermissionSnapshotDao
import com.filestech.appmanager.data.local.db.entity.PermissionSnapshotEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * v0.5.1 — Retention of [PermissionSnapshotDao]: the purge must keep, for each (package, permission)
 * pair, the newest row older than the cutoff, because the drift feed only shows a change that has a
 * predecessor. It deleted every old row, so a change still inside the retention vanished with its
 * baseline.
 */
@RunWith(AndroidJUnit4::class)
class PermissionSnapshotDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PermissionSnapshotDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // tests only
            .build()
        dao = db.permissionSnapshotDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun purge_keeps_the_baseline_of_a_change_still_inside_the_retention() = runBlocking<Unit> {
        // The reported case: 90-day retention, baseline on day 0, change on day 80, purge on day 91.
        dao.insert(row("com.a", "CAMERA", granted = false, day = 0))
        val change = dao.insert(row("com.a", "CAMERA", granted = true, day = 80))

        val deleted = dao.purgeOlderThan(cutoffMs = (91 - 90) * DAY)

        assertThat(deleted).isEqualTo(0)
        val drifts = dao.observeDrifts(sinceMs = 61 * DAY).first()
        assertThat(drifts.map { it.id }).containsExactly(change)
    }

    @Test
    fun purge_keeps_only_the_newest_row_before_the_cutoff_of_each_pair() = runBlocking<Unit> {
        // Changed three times before the cutoff, once after: only the last old state stays.
        dao.insert(row("com.b", "MICROPHONE", granted = false, day = 1))
        dao.insert(row("com.b", "MICROPHONE", granted = true, day = 2))
        val lastOldState = dao.insert(row("com.b", "MICROPHONE", granted = false, day = 3))
        val youngChange = dao.insert(row("com.b", "MICROPHONE", granted = true, day = 85))
        // Unchanged since before the cutoff: its only row is the current state, it stays.
        val stable = dao.insert(row("com.c", "LOCATION", granted = true, day = 5))
        // Same package as above, another permission: purged on its own.
        dao.insert(row("com.b", "CAMERA", granted = false, day = 4))
        val cameraOld = dao.insert(row("com.b", "CAMERA", granted = true, day = 6))

        val deleted = dao.purgeOlderThan(cutoffMs = 10 * DAY)

        assertThat(deleted).isEqualTo(3)
        assertThat(remainingIds()).containsExactly(lastOldState, youngChange, stable, cameraOld)
        val drifts = dao.observeDrifts(sinceMs = 0L).first()
        assertThat(drifts.map { it.id }).containsExactly(youngChange)
    }

    @Test
    fun purge_and_getLatest_break_a_timestamp_tie_on_the_id() = runBlocking<Unit> {
        dao.insert(row("com.d", "SMS", granted = false, day = 4))
        val second = dao.insert(row("com.d", "SMS", granted = true, day = 4))

        // Measured: SQLite's index scan already returns the higher id without `id DESC` (Robolectric,
        // 2026-10-08), so this line documents the order rather than catching its removal. The purge
        // assertion below does fail on the old query.
        assertThat(dao.getLatest("com.d", "SMS")?.id).isEqualTo(second)

        dao.purgeOlderThan(cutoffMs = 10 * DAY)

        assertThat(remainingIds()).containsExactly(second)
    }

    @Test
    fun purge_leaves_rows_at_or_after_the_cutoff() = runBlocking<Unit> {
        val atCutoff = dao.insert(row("com.e", "CONTACTS", granted = false, day = 10))
        val after = dao.insert(row("com.e", "CONTACTS", granted = true, day = 11))

        val deleted = dao.purgeOlderThan(cutoffMs = 10 * DAY)

        assertThat(deleted).isEqualTo(0)
        assertThat(remainingIds()).containsExactly(atCutoff, after)
    }

    // ---------------------------------------------------------------------------

    // The DAO has no "select all" (the app never needs one): read the table directly.
    private fun remainingIds(): List<Long> =
        db.openHelper.readableDatabase.query("SELECT id FROM permission_snapshot ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }

    private fun row(pkg: String, perm: String, granted: Boolean, day: Int) = PermissionSnapshotEntity(
        packageName = pkg,
        permission  = perm,
        granted     = granted,
        capturedAt  = day * DAY,
    )

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
