package com.filestech.appmanager.domain.model

/**
 * App identified as "zombie" — installed at some point but never (or rarely) used.
 *
 * Two qualifying conditions (computed by `GetZombieAppsUseCase`):
 * - [Reason.NEVER_OPENED]: `lastUsedTime == 0` AND `firstInstallTime` older than 7 days.
 *   The "older than 7 days" guard avoids flagging brand-new installs the user
 *   just hasn't launched yet.
 * - [Reason.UNUSED_SINCE]: `lastUsedTime` older than [unusedDays] days ago.
 *
 * The wrapper keeps the source [AppInfo] for UI rendering and adds the reason
 * + the threshold used so the UI can show "Unused for 42 days" etc.
 */
data class ZombieApp(
    val info: AppInfo,
    val reason: Reason,
    /** Days since `info.lastUsedTime`. Only meaningful when reason == UNUSED_SINCE. */
    val unusedDays: Int,
) {
    enum class Reason { NEVER_OPENED, UNUSED_SINCE }
}
