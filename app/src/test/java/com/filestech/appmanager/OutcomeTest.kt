package com.filestech.appmanager

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.core.result.isFailure
import com.filestech.appmanager.core.result.isLoading
import com.filestech.appmanager.core.result.isSuccess
import com.filestech.appmanager.core.result.map
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [Outcome] sealed interface and its extension functions.
 *
 * Invariants verified:
 * - Pattern matching is exhaustive (when() compiles without else on sealed interface)
 * - Convenience extensions return correct values
 * - map() transforms Success, leaves Failure and Loading untouched
 * - Loading is a data object (reference equality via ===)
 */
class OutcomeTest {

    // ---------------------------------------------------------------------------
    // State predicates
    // ---------------------------------------------------------------------------

    @Test
    fun `Loading is correctly identified`() {
        val outcome: Outcome<String> = Outcome.Loading
        assertThat(outcome.isLoading).isTrue()
        assertThat(outcome.isSuccess).isFalse()
        assertThat(outcome.isFailure).isFalse()
    }

    @Test
    fun `Success is correctly identified`() {
        val outcome = Outcome.Success("hello")
        assertThat(outcome.isSuccess).isTrue()
        assertThat(outcome.isLoading).isFalse()
        assertThat(outcome.isFailure).isFalse()
    }

    @Test
    fun `Failure is correctly identified`() {
        val outcome = Outcome.Failure(AppError.Unknown(RuntimeException("boom")))
        assertThat(outcome.isFailure).isTrue()
        assertThat(outcome.isLoading).isFalse()
        assertThat(outcome.isSuccess).isFalse()
    }

    // ---------------------------------------------------------------------------
    // getOrNull
    // ---------------------------------------------------------------------------

    @Test
    fun `getOrNull returns value for Success`() {
        val outcome = Outcome.Success(42)
        assertThat(outcome.getOrNull()).isEqualTo(42)
    }

    @Test
    fun `getOrNull returns null for Loading`() {
        val outcome: Outcome<Int> = Outcome.Loading
        assertThat(outcome.getOrNull()).isNull()
    }

    @Test
    fun `getOrNull returns null for Failure`() {
        val outcome: Outcome<Int> = Outcome.Failure(AppError.StorageFull)
        assertThat(outcome.getOrNull()).isNull()
    }

    // ---------------------------------------------------------------------------
    // map
    // ---------------------------------------------------------------------------

    @Test
    fun `map transforms Success value`() {
        val outcome = Outcome.Success(3)
        val mapped = outcome.map { it * 2 }
        assertThat(mapped).isEqualTo(Outcome.Success(6))
    }

    @Test
    fun `map leaves Failure untouched`() {
        val error = AppError.Validation("bad input")
        val outcome: Outcome<Int> = Outcome.Failure(error)
        val mapped = outcome.map { it * 2 }
        assertThat(mapped).isEqualTo(Outcome.Failure(error))
    }

    @Test
    fun `map leaves Loading untouched`() {
        val outcome: Outcome<Int> = Outcome.Loading
        val mapped = outcome.map { it * 2 }
        assertThat(mapped).isEqualTo(Outcome.Loading)
    }

    // ---------------------------------------------------------------------------
    // data object identity
    // ---------------------------------------------------------------------------

    @Test
    fun `Loading is a singleton data object`() {
        val a: Outcome<String> = Outcome.Loading
        val b: Outcome<Int>    = Outcome.Loading
        // data object → same reference
        assertThat(a).isSameInstanceAs(b)
    }

    // ---------------------------------------------------------------------------
    // Exhaustive when (compile-time guarantee — runtime exercise)
    // ---------------------------------------------------------------------------

    @Test
    fun `exhaustive when covers all branches`() {
        val outcomes: List<Outcome<String>> = listOf(
            Outcome.Loading,
            Outcome.Success("ok"),
            Outcome.Failure(AppError.StorageFull),
        )
        val labels = outcomes.map { outcome ->
            when (outcome) {
                is Outcome.Success -> "success"
                is Outcome.Failure -> "failure"
                Outcome.Loading    -> "loading"
            }
        }
        assertThat(labels).containsExactly("loading", "success", "failure").inOrder()
    }
}
