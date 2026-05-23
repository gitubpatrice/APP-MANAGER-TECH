package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.repository.IgnoreListRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Validates the three Ignore-list use cases share a consistent validation
 * contract and propagate to the repository correctly.
 */
class IgnoreAppUseCaseTest {

    private val repo: IgnoreListRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        // VII M-4: IgnoreAppUseCase now reads the current set via observe().first()
        // to enforce the cap. Default mock returns an empty set so cap checks pass.
        every { repo.observe() } returns flowOf(emptySet())
    }

    @Test
    fun `IgnoreApp rejects invalid package name`() = runTest {
        val useCase = IgnoreAppUseCase(repo)
        val outcome = useCase("not-a-package")
        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat((outcome as Outcome.Failure).error).isInstanceOf(AppError.Validation::class.java)
    }

    @Test
    fun `IgnoreApp delegates to repository on valid input`() = runTest {
        val useCase = IgnoreAppUseCase(repo)
        useCase("com.valid.pkg")
        coVerify { repo.add("com.valid.pkg") }
    }

    @Test
    fun `UnignoreApp rejects invalid package name`() = runTest {
        val useCase = UnignoreAppUseCase(repo)
        val outcome = useCase("")
        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
    }

    @Test
    fun `UnignoreApp delegates to repository on valid input`() = runTest {
        val useCase = UnignoreAppUseCase(repo)
        useCase("com.valid.pkg")
        coVerify { repo.remove("com.valid.pkg") }
    }

    @Test
    fun `IsAppIgnored returns repository value`() = runTest {
        coEvery { repo.isIgnored("com.x") } returns true
        coEvery { repo.isIgnored("com.y") } returns false
        val useCase = IsAppIgnoredUseCase(repo)
        assertThat(useCase("com.x")).isTrue()
        assertThat(useCase("com.y")).isFalse()
    }
}
