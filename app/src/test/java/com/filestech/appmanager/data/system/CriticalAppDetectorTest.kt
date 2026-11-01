package com.filestech.appmanager.data.system

import com.filestech.appmanager.data.local.datastore.AppSettings
import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.domain.model.CriticalCategory
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Tests [CriticalAppDetector]: the built-in lists (ids checked against the stores on 2026-11-01), their
 * case, and the wait for the user's own list.
 */
class CriticalAppDetectorTest {

    // A real dispatcher: classify() blocks its caller until the collector, on another thread, has read.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterEach
    fun tearDown() = scope.cancel()

    private fun detector(userProtected: Set<String> = emptySet(), readDelayMs: Long = 0L): CriticalAppDetector {
        val settings = mockk<SettingsRepository>()
        val value = AppSettings(safety = AppSettings.Safety(userProtectedPackages = userProtected))
        every { settings.flow } returns if (readDelayMs == 0L) flowOf(value) else flow { delay(readDelayMs); emit(value) }
        return CriticalAppDetector(scope, settings)
    }

    @Test
    fun `Fitbit, a mixed-case id, is protected`() {
        assertThat(detector().classify("com.fitbit.FitbitMobile")?.category).isEqualTo(CriticalCategory.HEALTH)
    }

    @Test
    fun `the store ids are protected, the wrong ids they replaced are not`() {
        val d = detector()
        val expected = mapOf(
            "net.bnpparibas.mescomptes" to CriticalCategory.BANKING_FR,
            "com.boursorama.android.clients" to CriticalCategory.BANKING_FR,
            "mobi.societegenerale.mobile.lappli" to CriticalCategory.BANKING_FR,
            "com.callpod.android_apps.keeper" to CriticalCategory.PASSWORD_MANAGERS,
            "com.filestech.pass_tech" to CriticalCategory.PASSWORD_MANAGERS,
            "com.twofasapp" to CriticalCategory.AUTHENTICATION,
            "fr.doctolib.www" to CriticalCategory.HEALTH,
            "com.vsct.vsc.mobile.horaireetresa.android" to CriticalCategory.TRANSPORT_FR,
            "com.ubercab" to CriticalCategory.TRANSPORT_FR,
            "io.element.android.x" to CriticalCategory.MESSAGING_E2E,
        )
        expected.forEach { (pkg, category) -> assertThat(d.classify(pkg)?.category).isEqualTo(category) }
        listOf("net.bnpparibas.mabanque", "com.uber.android", "fr.sncf.connect.app", "com.twilio.authy")
            .forEach { assertThat(d.classify(it)).isNull() }
    }

    @Test
    fun `La Poste's postal app is not a bank`() {
        assertThat(detector().classify("fr.laposte.lapostemobile")).isNull()
    }

    @Test
    fun `every built-in id is lowercase and sits in one category only`() {
        val d = detector()
        val all = CriticalCategory.entries.flatMap { d.builtInPackagesFor(it) }
        all.forEach { assertThat(it).isEqualTo(it.lowercase()) }
        assertThat(all).containsNoDuplicates()
    }

    @Test
    fun `a classification asked before the user's list is read waits for it`() {
        // The DataStore read takes 300 ms; the question comes at once, as after a process restore.
        val d = detector(userProtected = setOf("org.example.notes"), readDelayMs = 300L)
        assertThat(d.classify("org.example.notes")?.category).isEqualTo(CriticalCategory.USER_PROTECTED)
    }
}
