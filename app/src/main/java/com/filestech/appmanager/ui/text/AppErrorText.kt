package com.filestech.appmanager.ui.text

import com.filestech.appmanager.R
import com.filestech.appmanager.core.result.AppError

/**
 * The message a screen shows for an [AppError].
 *
 * The technical detail (exception, English validation text) is for the logs, where the repositories
 * already write it; the user gets a sentence in their language. Showing `AppError.toString()`
 * displayed `Validation(message=Invalid package name)` and the like.
 */
fun AppError.toUiText(): UiText = when (this) {
    is AppError.Permission, is AppError.PermissionPermanentlyDenied -> uiText(R.string.error_permission_denied)
    AppError.StorageFull -> uiText(R.string.error_storage_full)
    is AppError.PackageNotFound -> uiText(R.string.error_package_not_found)
    is AppError.Validation,
    is AppError.IoError,
    is AppError.PackageManagerError,
    is AppError.DatabaseError,
    is AppError.Unknown -> uiText(R.string.error_generic)
}
