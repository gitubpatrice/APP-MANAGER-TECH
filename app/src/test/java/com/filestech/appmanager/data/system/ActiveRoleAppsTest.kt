package com.filestech.appmanager.data.system

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Tests [packagesOfComponentList], the parsing half of [ActiveRoleApps]. */
class ActiveRoleAppsTest {

    @Test
    fun `the keyboard setting names one component`() {
        assertThat(packagesOfComponentList("com.touchtype.swiftkey/com.touchtype.KeyboardService"))
            .containsExactly("com.touchtype.swiftkey")
    }

    @Test
    fun `the accessibility setting names several, short class names included`() {
        val value = "com.bitwarden/.Accessibility:com.google.android.marvin.talkback/" +
            "com.google.android.marvin.talkback.TalkBackService"
        assertThat(packagesOfComponentList(value))
            .containsExactly("com.bitwarden", "com.google.android.marvin.talkback").inOrder()
    }

    @Test
    fun `an empty setting names nothing`() {
        assertThat(packagesOfComponentList("")).isEmpty()
        assertThat(packagesOfComponentList(":")).isEmpty()
    }
}
