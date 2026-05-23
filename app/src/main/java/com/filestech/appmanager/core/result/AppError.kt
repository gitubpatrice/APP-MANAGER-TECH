package com.filestech.appmanager.core.result

/**
 * Typed error hierarchy for App Manager Tech.
 *
 * Design decisions:
 * - Sealed interface (not sealed class) — no shared state, leaner bytecode.
 * - data class / data object throughout — structural equality, useful in tests.
 * - [cause] is nullable Throwable, never re-thrown at UI layer.
 *
 * Extension point: add a new leaf class under the relevant category when a
 * new feature introduces a new failure mode. Never add catch-all catch blocks
 * that swallow into [Unknown]; always prefer the most specific subtype.
 */
sealed interface AppError {

    // --- Input / validation failures ---

    /** A user-supplied value failed validation. [message] is human-readable (English). */
    data class Validation(val message: String) : AppError

    // --- Permission failures ---

    /** The app is missing runtime permission [name]. */
    data class Permission(val name: String) : AppError

    /** The user has permanently denied a permission (checked via shouldShowRequestPermissionRationale). */
    data class PermissionPermanentlyDenied(val name: String) : AppError

    // --- Storage / IO failures ---

    /** A file or database IO operation failed. */
    data class IoError(val cause: Throwable) : AppError

    /** The device storage is full or nearly full. */
    data object StorageFull : AppError

    // --- Package / app management failures ---

    /** The requested package was not found on the device. */
    data class PackageNotFound(val packageName: String) : AppError

    /** A package manager operation failed (install, uninstall, etc.). */
    data class PackageManagerError(val message: String, val cause: Throwable? = null) : AppError

    // --- Data layer failures ---

    /** A Room database operation failed. */
    data class DatabaseError(val cause: Throwable) : AppError

    // --- Catch-all ---

    /** An unexpected error with no specific type. Prefer specific subtypes above. */
    data class Unknown(val cause: Throwable) : AppError
}
