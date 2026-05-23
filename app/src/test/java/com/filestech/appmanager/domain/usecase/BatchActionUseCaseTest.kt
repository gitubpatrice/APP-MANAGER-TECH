package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.domain.model.AppAction
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * [BatchActionUseCase] honours the contract:
 * - Empty package list → success with zero counts.
 * - Uninstall / ClearCache / SetEnabled → Validation failure (cannot batch
 *   silently, UI must iterate).
 * - ForceStop → dispatch to [ForceStopAppUseCase] per package, aggregate
 *   succeeded/failed.
 */
class BatchActionUseCaseTest {

    private val forceStop: ForceStopAppUseCase = mockk()
    private val useCase = BatchActionUseCase(forceStop)

    @Test
    fun `empty list returns success with zero counts`() = runTest {
        val outcome = useCase(packages = emptyList(), action = AppAction.ForceStop)
        val result = (outcome as Outcome.Success).value
        assertThat(result.total).isEqualTo(0)
        assertThat(result.successCount).isEqualTo(0)
        assertThat(result.failureCount).isEqualTo(0)
    }

    @Test
    fun `uninstall action is rejected with validation failure`() = runTest {
        val outcome = useCase(
            packages = listOf("com.a"),
            action   = AppAction.Uninstall,
        )
        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat((outcome as Outcome.Failure).error).isInstanceOf(AppError.Validation::class.java)
    }

    @Test
    fun `clear cache action is rejected with validation failure`() = runTest {
        val outcome = useCase(
            packages = listOf("com.a"),
            action   = AppAction.ClearCache,
        )
        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
    }

    @Test
    fun `set enabled action is rejected with validation failure`() = runTest {
        val outcome = useCase(
            packages = listOf("com.a"),
            action   = AppAction.SetEnabled(enabled = true),
        )
        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
    }

    @Test
    fun `force stop dispatches to use case per package and aggregates success`() = runTest {
        coEvery { forceStop("com.a") } returns Outcome.Success(Unit)
        coEvery { forceStop("com.b") } returns Outcome.Success(Unit)

        val outcome = useCase(
            packages = listOf("com.a", "com.b"),
            action   = AppAction.ForceStop,
        )
        val result = (outcome as Outcome.Success).value

        assertThat(result.total).isEqualTo(2)
        assertThat(result.succeeded).containsExactly("com.a", "com.b")
        assertThat(result.failed).isEmpty()
        coVerify { forceStop("com.a"); forceStop("com.b") }
    }

    @Test
    fun `force stop captures failure per package`() = runTest {
        coEvery { forceStop("com.ok") } returns Outcome.Success(Unit)
        coEvery { forceStop("com.fail") } returns
            Outcome.Failure(AppError.Permission("KILL_BG"))

        val outcome = useCase(
            packages = listOf("com.ok", "com.fail"),
            action   = AppAction.ForceStop,
        )
        val result = (outcome as Outcome.Success).value

        assertThat(result.succeeded).containsExactly("com.ok")
        assertThat(result.failed.keys).containsExactly("com.fail")
        assertThat(result.allSucceeded).isFalse()
    }
}
