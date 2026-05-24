package com.filestech.appmanager.data.repository

import com.filestech.appmanager.data.local.db.entity.AppInfoEntity
import com.filestech.appmanager.domain.model.AppCategory
import com.filestech.appmanager.domain.model.AppInfo

/**
 * Maps between persisted [AppInfoEntity] and domain [AppInfo].
 *
 * Single source of truth for the data ↔ domain conversion. Any new field on
 * either side MUST be added here, otherwise it silently disappears.
 *
 * Category serialisation: enum stored as TEXT via [AppCategory.name];
 * unknown / future strings collapse to [AppCategory.UNDEFINED] (forward-compat
 * after downgrade scenarios).
 */
internal object AppInfoMapper {

    fun toDomain(entity: AppInfoEntity): AppInfo = AppInfo(
        packageName       = entity.packageName,
        label             = entity.label,
        versionName       = entity.versionName,
        versionCode       = entity.versionCode,
        installSizeBytes  = entity.installSizeBytes,
        cacheSizeBytes    = entity.cacheSizeBytes,
        dataSizeBytes     = entity.dataSizeBytes,
        firstInstallTime  = entity.firstInstallTime,
        lastUpdateTime    = entity.lastUpdateTime,
        lastUsedTime      = entity.lastUsedTime,
        isSystemApp       = entity.isSystemApp,
        isUninstallable   = entity.isUninstallable,
        isEnabled         = entity.isEnabled,
        category          = parseCategory(entity.category),
        installerPackage  = entity.installerPackage,
        apkSourceDir      = entity.apkSourceDir,
        isHibernated      = entity.isHibernated,
    )

    fun toEntity(domain: AppInfo, cachedAt: Long): AppInfoEntity = AppInfoEntity(
        packageName       = domain.packageName,
        label             = domain.label,
        versionName       = domain.versionName,
        versionCode       = domain.versionCode,
        installSizeBytes  = domain.installSizeBytes,
        cacheSizeBytes    = domain.cacheSizeBytes,
        dataSizeBytes     = domain.dataSizeBytes,
        firstInstallTime  = domain.firstInstallTime,
        lastUpdateTime    = domain.lastUpdateTime,
        lastUsedTime      = domain.lastUsedTime,
        isSystemApp       = domain.isSystemApp,
        isUninstallable   = domain.isUninstallable,
        isEnabled         = domain.isEnabled,
        category          = domain.category.name,
        cachedAt          = cachedAt,
        installerPackage  = domain.installerPackage,
        apkSourceDir      = domain.apkSourceDir,
        isHibernated      = domain.isHibernated,
    )

    private fun parseCategory(raw: String): AppCategory =
        runCatching { AppCategory.valueOf(raw) }.getOrDefault(AppCategory.UNDEFINED)
}
