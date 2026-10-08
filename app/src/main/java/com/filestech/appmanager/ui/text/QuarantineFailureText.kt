package com.filestech.appmanager.ui.text

import com.filestech.appmanager.R
import com.filestech.appmanager.domain.usecase.QuarantineAppUseCase
import com.filestech.appmanager.domain.usecase.QuarantineAppUseCase.FailureReason

/** The message the user sees when a quarantine fails; [QuarantineAppUseCase.Result.Failure.detail] is for the logs. */
fun QuarantineAppUseCase.Result.Failure.toUiText(): UiText = when (reason) {
    FailureReason.INVALID_PACKAGE -> uiText(R.string.error_invalid_package)
    FailureReason.INVALID_DURATION ->
        uiText(R.string.quarantine_error_invalid_duration, QuarantineAppUseCase.MIN_DAYS, QuarantineAppUseCase.MAX_DAYS)
    FailureReason.APP_NOT_FOUND -> uiText(R.string.error_package_not_found)
    FailureReason.APP_LIST_LOADING -> uiText(R.string.quarantine_error_app_list_loading)
    FailureReason.NEEDS_BACKUP_FOLDER -> uiText(R.string.quarantine_error_needs_backup_folder)
    FailureReason.APK_UNREADABLE -> uiText(R.string.quarantine_error_apk_unreadable)
    FailureReason.BACKUP_FOLDER_UNAVAILABLE -> uiText(R.string.quarantine_error_backup_folder_unavailable)
    FailureReason.BACKUP_FOLDER_ACCESS_REVOKED -> uiText(R.string.quarantine_error_backup_folder_revoked)
    FailureReason.BACKUP_FAILED -> uiText(R.string.quarantine_error_backup_failed)
    FailureReason.SAVE_FAILED -> uiText(R.string.quarantine_error_save_failed)
}
