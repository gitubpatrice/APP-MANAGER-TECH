package com.filestech.appmanager.domain.usecase

import android.content.Intent
import com.filestech.appmanager.data.system.ApkBackupManager
import com.filestech.appmanager.data.system.IntentFactory
import com.filestech.appmanager.domain.model.QuarantineEntry
import com.filestech.appmanager.domain.model.QuarantineMode
import com.filestech.appmanager.domain.repository.QuarantineRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * [RestoreFromQuarantineUseCase], HARD mode: the installer reports nothing back, and can be cancelled
 * or refused (Android refused every restore until v0.5.1). The entry must survive the intent and go
 * only once the app came back after its quarantine — until v0.5.1 it was dropped with the intent, so
 * a refused install lost the quarantine record.
 *
 * v0.5.1 — the drop is a reconciliation of every HARD entry, run on every resume of the screen, and
 * "installed" is not enough: an uninstall the user cancelled leaves the app installed, last updated
 * before its quarantine.
 */
class RestoreFromQuarantineUseCaseTest {

    private val repository: QuarantineRepository = mockk(relaxed = true)
    private val apkBackup: ApkBackupManager = mockk()
    private val intents: IntentFactory = mockk()
    private val useCase = RestoreFromQuarantineUseCase(repository, apkBackup, intents)

    private fun entry(mode: QuarantineMode, packageName: String = PKG) = QuarantineEntry(
        packageName        = packageName,
        label              = "Test",
        mode               = mode,
        quarantinedAt      = QUARANTINED_AT,
        restoreAt          = QUARANTINED_AT,
        versionName        = "1.0",
        versionCode        = 1L,
        apkBackupUri       = if (mode == QuarantineMode.HARD_UNINSTALL) "content://backup/test.apk" else null,
        autoRestoreEnabled = false,
        notified           = false,
    )

    @Test
    fun `hard restore hands the intent over and keeps the entry`() = runTest {
        coEvery { repository.getByPackage(PKG) } returns entry(QuarantineMode.HARD_UNINSTALL)
        every { apkBackup.restoreIntent(any()) } returns mockk<Intent>()

        val result = useCase(PKG)

        assertThat(result).isInstanceOf(RestoreFromQuarantineUseCase.Result.HardReinstall::class.java)
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `reconcile drops a hard entry whose app was installed since its quarantine`() = runTest {
        coEvery { repository.getAll() } returns listOf(entry(QuarantineMode.HARD_UNINSTALL))
        coEvery { apkBackup.lastUpdateTime(PKG) } returns QUARANTINED_AT + 1

        assertThat(useCase.reconcileHardRestores()).containsExactly(PKG)
        coVerify(exactly = 1) { repository.delete(PKG) }
    }

    @Test
    fun `reconcile keeps a hard entry whose app is not installed`() = runTest {
        coEvery { repository.getAll() } returns listOf(entry(QuarantineMode.HARD_UNINSTALL))
        coEvery { apkBackup.lastUpdateTime(PKG) } returns null

        assertThat(useCase.reconcileHardRestores()).isEmpty()
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `reconcile keeps a hard entry whose app was last updated before its quarantine`() = runTest {
        // The uninstall was cancelled: installed, but never reinstalled.
        coEvery { repository.getAll() } returns listOf(entry(QuarantineMode.HARD_UNINSTALL))
        coEvery { apkBackup.lastUpdateTime(PKG) } returns QUARANTINED_AT - 1

        assertThat(useCase.reconcileHardRestores()).isEmpty()
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `reconcile never drops a soft entry, even with its app updated since`() = runTest {
        coEvery { repository.getAll() } returns listOf(entry(QuarantineMode.SOFT_REMINDER))
        coEvery { apkBackup.lastUpdateTime(PKG) } returns QUARANTINED_AT + 1

        assertThat(useCase.reconcileHardRestores()).isEmpty()
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `reconcile drops only the entries that qualify, among several`() = runTest {
        coEvery { repository.getAll() } returns listOf(
            entry(QuarantineMode.HARD_UNINSTALL, BACK),
            entry(QuarantineMode.HARD_UNINSTALL, CANCELLED),
            entry(QuarantineMode.HARD_UNINSTALL, GONE),
            entry(QuarantineMode.SOFT_REMINDER, PKG),
        )
        coEvery { apkBackup.lastUpdateTime(BACK) } returns QUARANTINED_AT + 1
        coEvery { apkBackup.lastUpdateTime(CANCELLED) } returns QUARANTINED_AT - 1
        coEvery { apkBackup.lastUpdateTime(GONE) } returns null
        coEvery { apkBackup.lastUpdateTime(PKG) } returns QUARANTINED_AT + 1

        assertThat(useCase.reconcileHardRestores()).containsExactly(BACK)
        coVerify(exactly = 1) { repository.delete(BACK) }
        coVerify(exactly = 1) { repository.delete(any()) }
    }

    private companion object {
        const val PKG = "com.example.quarantined"
        const val BACK = "com.example.back"
        const val CANCELLED = "com.example.cancelled"
        const val GONE = "com.example.gone"
        const val QUARANTINED_AT = 1_000_000L
    }
}
