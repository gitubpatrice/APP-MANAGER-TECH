package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.repository.AmtActionRepository
import com.filestech.appmanager.domain.repository.AppLifecycleRepository
import com.filestech.appmanager.domain.repository.PermissionSnapshotRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * [EraseDisabledHistoriesUseCase] — the privacy policy says turning a history off erases it (v0.5.1).
 * Until then, turning one off cancelled its purge too, and what it held stayed forever.
 */
class EraseDisabledHistoriesUseCaseTest {

    private val lifecycle: AppLifecycleRepository = mockk {
        coEvery { deleteAll() } returns Outcome.Success(3)
    }
    private val journal: AmtActionRepository = mockk {
        coEvery { deleteAll() } returns Outcome.Success(2)
    }
    private val permissions: PermissionSnapshotRepository = mockk {
        coEvery { deleteAll() } returns 5
    }
    private val useCase = EraseDisabledHistoriesUseCase(lifecycle, journal, permissions)

    @Test
    fun `each history turned off is erased`() = runTest {
        useCase(lifecycleOn = false, journalOn = false, permissionsOn = false)

        coVerify(exactly = 1) { lifecycle.deleteAll() }
        coVerify(exactly = 1) { journal.deleteAll() }
        coVerify(exactly = 1) { permissions.deleteAll() }
    }

    @Test
    fun `a history that is on is never touched`() = runTest {
        useCase(lifecycleOn = true, journalOn = true, permissionsOn = true)

        coVerify(exactly = 0) { lifecycle.deleteAll() }
        coVerify(exactly = 0) { journal.deleteAll() }
        coVerify(exactly = 0) { permissions.deleteAll() }
    }

    @Test
    fun `only the history turned off goes`() = runTest {
        useCase(lifecycleOn = true, journalOn = false, permissionsOn = true)

        coVerify(exactly = 0) { lifecycle.deleteAll() }
        coVerify(exactly = 1) { journal.deleteAll() }
        coVerify(exactly = 0) { permissions.deleteAll() }
    }
}
