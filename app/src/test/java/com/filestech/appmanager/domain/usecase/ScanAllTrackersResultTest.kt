package com.filestech.appmanager.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests the summary figures of [ScanAllTrackersUseCase.Result]:
 * - [ScanAllTrackersUseCase.Result.contaminationPercent] truncates, so it never reads 100 while one app is clean.
 * - [ScanAllTrackersUseCase.Result.isBelowOnePercent] flags the case the truncation hides: one app of
 *   230 with trackers read "0 %" next to "1 app of 230 contains trackers".
 */
class ScanAllTrackersResultTest {

    @Test
    fun `one app of 230 with trackers is below one percent, not zero`() {
        val result = result(total = 230, withTrackers = 1)
        assertThat(result.contaminationPercent).isEqualTo(0)
        assertThat(result.isBelowOnePercent).isTrue()
    }

    @Test
    fun `no app with trackers reads zero percent`() {
        val result = result(total = 7, withTrackers = 0)
        assertThat(result.contaminationPercent).isEqualTo(0)
        assertThat(result.isBelowOnePercent).isFalse()
    }

    @Test
    fun `exactly one percent is a plain percentage`() {
        val result = result(total = 100, withTrackers = 1)
        assertThat(result.contaminationPercent).isEqualTo(1)
        assertThat(result.isBelowOnePercent).isFalse()
    }

    @Test
    fun `all but one app with trackers stays below one hundred`() {
        val result = result(total = 230, withTrackers = 229)
        assertThat(result.contaminationPercent).isEqualTo(99)
        assertThat(result.isBelowOnePercent).isFalse()
    }

    @Test
    fun `an empty scan reads zero, not below one percent`() {
        val result = result(total = 0, withTrackers = 0)
        assertThat(result.contaminationPercent).isEqualTo(0)
        assertThat(result.isBelowOnePercent).isFalse()
    }

    private fun result(total: Int, withTrackers: Int) = ScanAllTrackersUseCase.Result(
        reports           = emptyList(),
        byPackage         = emptyMap(),
        apps              = emptyMap(),
        totalAppCount     = total,
        appsWithTrackers  = withTrackers,
        totalDetections   = withTrackers,
        categoryHistogram = emptyList(),
    )
}
