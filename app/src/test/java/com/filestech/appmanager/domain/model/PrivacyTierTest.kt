package com.filestech.appmanager.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins the tier thresholds documented on [PrivacyTier]: they set the badge colour of every app, and
 * as enum arguments they are outside detekt's MagicNumber rule, so only this test sees them move.
 */
class PrivacyTierTest {

    @Test
    fun `RED covers 0 to 29`() {
        assertThat(PrivacyTier.of(0)).isEqualTo(PrivacyTier.RED)
        assertThat(PrivacyTier.of(29)).isEqualTo(PrivacyTier.RED)
    }

    @Test
    fun `ORANGE covers 30 to 49`() {
        assertThat(PrivacyTier.of(30)).isEqualTo(PrivacyTier.ORANGE)
        assertThat(PrivacyTier.of(49)).isEqualTo(PrivacyTier.ORANGE)
    }

    @Test
    fun `YELLOW covers 50 to 79`() {
        assertThat(PrivacyTier.of(50)).isEqualTo(PrivacyTier.YELLOW)
        assertThat(PrivacyTier.of(79)).isEqualTo(PrivacyTier.YELLOW)
    }

    @Test
    fun `GREEN covers 80 to 100`() {
        assertThat(PrivacyTier.of(80)).isEqualTo(PrivacyTier.GREEN)
        assertThat(PrivacyTier.of(100)).isEqualTo(PrivacyTier.GREEN)
    }
}
