package com.filestech.appmanager.core.result

/**
 * Typed result carrier used across all layers.
 *
 * Rules:
 * - ViewModels receive Outcome<T> from UseCases, never raw exceptions.
 * - UI maps Outcome to UiState; never re-throws.
 * - [Loading] is a data object (Kotlin 2.x convention, participates in equals/hashCode).
 *
 * Usage:
 * ```kotlin
 * when (val r = useCase()) {
 *     is Outcome.Success -> render(r.value)
 *     is Outcome.Failure -> showError(r.error)
 *     Outcome.Loading    -> showSpinner()
 * }
 * ```
 */
sealed interface Outcome<out S> {

    /** Intermediate state while an async operation is in flight. */
    data object Loading : Outcome<Nothing>

    /** Operation completed successfully; [value] holds the result. */
    data class Success<S>(val value: S) : Outcome<S>

    /** Operation failed; [error] describes the failure in a typed, structured way. */
    data class Failure(val error: AppError) : Outcome<Nothing>
}

// ---------------------------------------------------------------------------
// Convenience extensions
// ---------------------------------------------------------------------------

/** Returns the [Outcome.Success.value] or null if not a success. */
fun <S> Outcome<S>.getOrNull(): S? = (this as? Outcome.Success)?.value

/** Returns true only for [Outcome.Success]. */
val <S> Outcome<S>.isSuccess: Boolean get() = this is Outcome.Success

/** Returns true only for [Outcome.Failure]. */
val <S> Outcome<S>.isFailure: Boolean get() = this is Outcome.Failure

/** Returns true only for [Outcome.Loading]. */
val <S> Outcome<S>.isLoading: Boolean get() = this is Outcome.Loading

/**
 * Transforms the success value, leaving Loading/Failure untouched.
 */
inline fun <S, R> Outcome<S>.map(transform: (S) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
    Outcome.Loading    -> Outcome.Loading
}

/**
 * Chains another Outcome-producing operation onto a success.
 * Failure / Loading are propagated unchanged — short-circuits the pipeline.
 */
inline fun <S, R> Outcome<S>.flatMap(transform: (S) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
    Outcome.Loading    -> Outcome.Loading
}

/** Performs [block] for side effects only when [Outcome.Success]; returns the receiver. */
inline fun <S> Outcome<S>.onSuccess(block: (S) -> Unit): Outcome<S> {
    if (this is Outcome.Success) block(value)
    return this
}

/** Performs [block] for side effects only when [Outcome.Failure]; returns the receiver. */
inline fun <S> Outcome<S>.onFailure(block: (AppError) -> Unit): Outcome<S> {
    if (this is Outcome.Failure) block(error)
    return this
}

/** Returns the success value or evaluates [fallback] for Failure / Loading. */
inline fun <S> Outcome<S>.getOrElse(fallback: () -> S): S =
    if (this is Outcome.Success) value else fallback()

/**
 * Bridges a throwing block into the Outcome world.
 *
 * - Catches [Throwable] and maps it through [mapError] to a typed [AppError].
 * - Does NOT catch [kotlinx.coroutines.CancellationException] — coroutine
 *   cancellation must propagate.
 */
inline fun <S> runCatchingOutcome(
    mapError: (Throwable) -> AppError,
    block: () -> S,
): Outcome<S> = try {
    Outcome.Success(block())
} catch (ce: kotlinx.coroutines.CancellationException) {
    throw ce
} catch (t: Throwable) {
    Outcome.Failure(mapError(t))
}
