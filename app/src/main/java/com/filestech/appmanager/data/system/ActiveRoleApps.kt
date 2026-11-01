package com.filestech.appmanager.data.system

import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apps the user relies on without ever opening them: the keyboard in use, the enabled accessibility
 * services, the active device admins and the live wallpaper. Usage statistics count activities brought to
 * the front, so these read as "never opened" while working all day: the inactive-app lists and the
 * cleaner leave them out.
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
        runCatching { context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins }
            .getOrNull()
            ?.forEach { add(it.packageName) }
        runCatching { WallpaperManager.getInstance(context).wallpaperInfo?.packageName }
            .getOrNull()
            ?.let(::add)
    }

    private fun secureSetting(resolver: android.content.ContentResolver, key: String): String? =
        runCatching { Settings.Secure.getString(resolver, key) }.getOrNull()
}

/** Packages named in a `Settings.Secure` component list: `pkg/cls`, or several joined by `:`. */
internal fun packagesOfComponentList(value: String): List<String> =
    value.split(':').mapNotNull { entry -> entry.substringBefore('/').trim().takeIf { it.isNotEmpty() } }
