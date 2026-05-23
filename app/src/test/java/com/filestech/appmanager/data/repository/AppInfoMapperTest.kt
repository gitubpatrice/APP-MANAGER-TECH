package com.filestech.appmanager.data.repository

import com.filestech.appmanager.data.local.db.entity.AppInfoEntity
import com.filestech.appmanager.domain.model.AppCategory
import com.filestech.appmanager.domain.model.AppInfo
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Validates the entity ↔ domain bijection in [AppInfoMapper].
 *
 * Critical invariants:
 * - Every field on either side must round-trip without loss.
 * - Unknown category strings collapse to UNDEFINED (forward-compat after
 *   enum reorder/rename — no crash on stale cached rows).
 * - cachedAt is a write-only mapper input (not in domain AppInfo).
 */
class AppInfoMapperTest {

    @Test
    fun `entity to domain copies every field`() {
        val entity = sampleEntity()
        val domain = AppInfoMapper.toDomain(entity)

        assertThat(domain.packageName).isEqualTo(entity.packageName)
        assertThat(domain.label).isEqualTo(entity.label)
        assertThat(domain.versionName).isEqualTo(entity.versionName)
        assertThat(domain.versionCode).isEqualTo(entity.versionCode)
        assertThat(domain.installSizeBytes).isEqualTo(entity.installSizeBytes)
        assertThat(domain.cacheSizeBytes).isEqualTo(entity.cacheSizeBytes)
        assertThat(domain.dataSizeBytes).isEqualTo(entity.dataSizeBytes)
        assertThat(domain.firstInstallTime).isEqualTo(entity.firstInstallTime)
        assertThat(domain.lastUpdateTime).isEqualTo(entity.lastUpdateTime)
        assertThat(domain.lastUsedTime).isEqualTo(entity.lastUsedTime)
        assertThat(domain.isSystemApp).isEqualTo(entity.isSystemApp)
        assertThat(domain.isUninstallable).isEqualTo(entity.isUninstallable)
        assertThat(domain.isEnabled).isEqualTo(entity.isEnabled)
        assertThat(domain.installerPackage).isEqualTo(entity.installerPackage)
        assertThat(domain.apkSourceDir).isEqualTo(entity.apkSourceDir)
        assertThat(domain.category).isEqualTo(AppCategory.GAMES)
    }

    @Test
    fun `domain to entity round trip preserves data`() {
        val original = sampleDomain()
        val now = 1_234_567_890L
        val entity = AppInfoMapper.toEntity(original, cachedAt = now)
        val backToDomain = AppInfoMapper.toDomain(entity)

        assertThat(backToDomain).isEqualTo(original)
        assertThat(entity.cachedAt).isEqualTo(now)
    }

    @Test
    fun `unknown category collapses to UNDEFINED`() {
        val entity = sampleEntity().copy(category = "DEFINITELY_NOT_A_VALID_ENUM")
        val domain = AppInfoMapper.toDomain(entity)
        assertThat(domain.category).isEqualTo(AppCategory.UNDEFINED)
    }

    @Test
    fun `total size getter is sum of install plus data plus cache`() {
        val entity = sampleEntity().copy(
            installSizeBytes = 1_000_000L,
            dataSizeBytes    = 2_000_000L,
            cacheSizeBytes   = 3_000_000L,
        )
        val domain = AppInfoMapper.toDomain(entity)
        assertThat(domain.totalSizeBytes).isEqualTo(6_000_000L)
    }

    @Test
    fun `total size getter handles zero correctly`() {
        val domain = sampleDomain().copy(
            installSizeBytes = 0L,
            dataSizeBytes    = 0L,
            cacheSizeBytes   = 0L,
        )
        assertThat(domain.totalSizeBytes).isEqualTo(0L)
    }

    // ---------------------------------------------------------------------------

    private fun sampleEntity() = AppInfoEntity(
        packageName       = "com.example.app",
        label             = "Example App",
        versionName       = "1.2.3",
        versionCode       = 42L,
        installSizeBytes  = 10_000_000L,
        cacheSizeBytes    = 500_000L,
        dataSizeBytes     = 2_000_000L,
        firstInstallTime  = 1_000L,
        lastUpdateTime    = 2_000L,
        lastUsedTime      = 3_000L,
        isSystemApp       = false,
        isUninstallable   = true,
        isEnabled         = true,
        category          = "GAMES",
        cachedAt          = 4_000L,
        installerPackage  = "com.android.vending",
        apkSourceDir      = "/data/app/com.example.app/base.apk",
    )

    private fun sampleDomain() = AppInfo(
        packageName       = "com.example.app",
        label             = "Example App",
        versionName       = "1.2.3",
        versionCode       = 42L,
        installSizeBytes  = 10_000_000L,
        cacheSizeBytes    = 500_000L,
        dataSizeBytes     = 2_000_000L,
        firstInstallTime  = 1_000L,
        lastUpdateTime    = 2_000L,
        lastUsedTime      = 3_000L,
        isSystemApp       = false,
        isUninstallable   = true,
        isEnabled         = true,
        category          = AppCategory.GAMES,
        installerPackage  = "com.android.vending",
        apkSourceDir      = "/data/app/com.example.app/base.apk",
    )
}
