package com.filestech.appmanager.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins [PermissionCapability.capabilityOf] against the platform's own list: the permissions whose
 * protection is `dangerous`, read from `dumpsys package permissions` on API 34 (42) and API 36 (43).
 */
class PermissionCapabilityTest {

    @Test
    fun `the table holds the 43 dangerous platform permissions measured on API 36`() {
        assertThat(PermissionCapability.platformPermissionCount).isEqualTo(43)
    }

    @Test
    fun `permissions the first list missed now have a capability`() {
        assertThat(PermissionCapability.capabilityOf("android.permission.GET_ACCOUNTS"))
            .isEqualTo(PermissionCapability.CONTACTS)
        assertThat(PermissionCapability.capabilityOf("android.permission.ACCESS_MEDIA_LOCATION"))
            .isEqualTo(PermissionCapability.MEDIA_LOCATION)
        assertThat(PermissionCapability.capabilityOf("android.permission.UWB_RANGING"))
            .isEqualTo(PermissionCapability.NEARBY)
        assertThat(PermissionCapability.capabilityOf("com.android.voicemail.permission.ADD_VOICEMAIL"))
            .isEqualTo(PermissionCapability.PHONE)
    }

    @Test
    fun `Health Connect permissions are matched by prefix`() {
        assertThat(PermissionCapability.capabilityOf("android.permission.health.READ_MENSTRUATION"))
            .isEqualTo(PermissionCapability.HEALTH)
    }

    @Test
    fun `normal and special permissions are not dangerous`() {
        listOf(
            "android.permission.INTERNET",
            "android.permission.USE_BIOMETRIC",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.healthy",
        ).forEach { assertThat(PermissionCapability.capabilityOf(it)).isNull() }
    }

    @Test
    fun `approximate location scores in the precise location group`() {
        assertThat(PermissionCapability.LOCATION_APPROX.scoreGroup).isEqualTo(PermissionCapability.LOCATION)
        assertThat(PermissionCapability.LOCATION_BACKGROUND.scoreGroup)
            .isEqualTo(PermissionCapability.LOCATION_BACKGROUND)
    }
}
