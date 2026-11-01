package com.filestech.appmanager.ui

import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The protected-apps warning (hold 3 s) lives in the ViewModels, before they call a destructive use case:
 * the use cases take a `bypassCriticalCheck` and do not check by themselves. So a new screen that can
 * uninstall, disable or quarantine must inject [com.filestech.appmanager.data.system.CriticalAppDetector]
 * too — this test reads the sources and fails the build of one that does not.
 */
class ProtectedAppsGuardArchitectureTest {

    @Test
    fun `every ViewModel that can uninstall, disable or quarantine also checks protected apps`() {
        val viewModels = File(SOURCES).walkTopDown().filter { it.isFile && it.name.endsWith("ViewModel.kt") }.toList()
        assertWithMessage("no ViewModel source found under %s", SOURCES).that(viewModels).isNotEmpty()

        val destructive = viewModels.filter { file ->
            val text = file.readText()
            DESTRUCTIVE_USE_CASES.any { useCase -> "private val" in text && ": $useCase" in text }
        }
        // Six today: AppDetail, AppList, QuarantinePicker, SecurityAudit, SmartCleaner, Trash.
        assertWithMessage("destructive ViewModels found").that(destructive.size).isAtLeast(EXPECTED_AT_LEAST)

        for (file in destructive) {
            assertWithMessage("%s calls a destructive use case without CriticalAppDetector", file.name)
                .that(file.readText())
                .contains(": CriticalAppDetector")
        }
    }

    private companion object {
        /** Unit tests run from the module directory. */
        const val SOURCES = "src/main/java/com/filestech/appmanager/ui"
        const val EXPECTED_AT_LEAST = 6
        val DESTRUCTIVE_USE_CASES = listOf("UninstallAppUseCase", "DisableEnableAppUseCase", "QuarantineAppUseCase")
    }
}
