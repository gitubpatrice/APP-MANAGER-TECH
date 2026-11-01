package com.filestech.appmanager.data.system

import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.autofill.AutofillManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apps the user relies on without ever opening them: the keyboard in use, the enabled accessibility
 * services, the autofill service (a password manager that only fills forms), the default SMS and phone
 * apps (they receive in the background), the active device admins and the live wallpaper. Usage
 * statistics count activities brought to the front, so these read as "never opened" while working all
 * day: the inactive-app lists and the cleaner leave them out.
 *
 * Every source is public and needs no permission; one that cannot be read adds nothing.
 */
@Singleton
class ActiveRoleApps @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun packages(): Set<String> = buildSet {
        val resolver = context.contentResolver
        secureSetting(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.let { addAll(packagesOfComponentList(it)) }
        secureSetting(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.let { addAll(packagesOfComponentList(it)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            read { context.getSystemService(AutofillManager::class.java)?.autofillServiceComponentName?.packageName }
                ?.let(::add)
        }
        read { Telephony.Sms.getDefaultSmsPackage(context) }?.let(::add)
        read { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }?.let(::add)
        read { context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins }
            ?.forEach { add(it.packageName) }
        read { WallpaperManager.getInstance(context).wallpaperInfo?.packageName }?.let(::add)
    }

    private fun secureSetting(resolver: android.content.ContentResolver, key: String): String? =
        read { Settings.Secure.getString(resolver, key) }

    /** A source that throws (an OEM restriction, a service missing on a tablet) adds nothing. */
    private inline fun <T> read(block: () -> T?): T? = runCatching(block).getOrNull()
}

/** Packages named in a `Settings.Secure` component list: `pkg/cls`, or several joined by `:`. */
internal fun packagesOfComponentList(value: String): List<String> =
    value.split(':').mapNotNull { entry -> entry.substringBefore('/').trim().takeIf { it.isNotEmpty() } }
