package com.filestech.appmanager.data.repository

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.data.local.db.dao.AppLifecycleEventDao
import com.filestech.appmanager.data.local.db.entity.AppLifecycleEventEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The declared dangerous permissions of a lifecycle event (schema 10), read through the repository's
 * mapper: NULL is "not recorded" (rows before 0.5.3, where the drift falls back on granted ones), "" is
 * "none declared", and a list round-trips. A NULL read as empty would make an old row look like a version
 * that declared nothing — and every permission of the next one "added".
 */
class AppLifecycleRepositoryImplTest {

    private val dao: AppLifecycleEventDao = mockk()
    private val repository = AppLifecycleRepositoryImpl(dao, Dispatchers.Unconfined)

    @Test
    fun `NULL stays unknown, empty stays empty, a list round-trips`() = runTest {
        every { dao.observeByPackage(PKG) } returns flowOf(
            listOf(
                entity(id = 1, declared = null),
                entity(id = 2, declared = ""),
                entity(id = 3, declared = "$CAMERA|$MIC"),
            ),
        )

        val events = (repository.observeByPackage(PKG).first() as Outcome.Success).value

        assertThat(events.single { it.id == 1L }.requestedDangerousPermissions).isNull()
        assertThat(events.single { it.id == 2L }.requestedDangerousPermissions).isEmpty()
        assertThat(events.single { it.id == 3L }.requestedDangerousPermissions).containsExactly(CAMERA, MIC).inOrder()
    }

    private fun entity(id: Long, declared: String?) = AppLifecycleEventEntity(
        id                      = id,
        packageName             = PKG,
        label                   = "Example",
        type                    = "REPLACED",
        capturedAt              = id,
        versionName             = "1.0",
        versionCode             = id,
        installerPackage        = null,
        totalSizeBytes          = 0L,
        grantedDangerousPerms   = "",
        userReason              = null,
        apkSha256               = null,
        requestedDangerousPerms = declared,
    )

    private companion object {
        const val PKG = "com.example.app"
        const val CAMERA = "android.permission.CAMERA"
        const val MIC = "android.permission.RECORD_AUDIO"
    }
}
