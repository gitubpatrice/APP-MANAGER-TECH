package com.filestech.appmanager.ui

import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The protected-apps warning (hold 3 s) lives in the ViewModels, before they call a destructive use case;
 * the use cases take a `bypassCriticalCheck` and do not check by themselves. Two rules keep it whole, read
 * from the sources — a net under the behaviour tests of each ViewModel, not a proof of them:
 * - a ViewModel that can uninstall, disable or quarantine injects
 *   [com.filestech.appmanager.data.system.CriticalAppDetector] and calls its `classify`;
 * - `bypassCriticalCheck = true` is written only in a screen, in the confirm callback of the
 *   `CriticalWarningDialog` just above it — never in a ViewModel, never on a plain button.
 */
class ProtectedAppsGuardArchitectureTest {

    private val uiSources = File(SOURCES).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `every ViewModel that can uninstall, disable or quarantine also checks protected apps`() {
        val viewModels = uiSources.filter { it.name.endsWith("ViewModel.kt") }
        assertWithMessage("no ViewModel source found under %s", SOURCES).that(viewModels).isNotEmpty()

        val destructive = viewModels.filter { file ->
            val code = codeOf(file)
            DESTRUCTIVE_USE_CASES.any { useCase -> ": $useCase" in code }
        }
        // Six today: AppDetail, AppList, QuarantinePicker, SecurityAudit, SmartCleaner, Trash.
        assertWithMessage("destructive ViewModels found").that(destructive.size).isAtLeast(EXPECTED_AT_LEAST)

        for (file in destructive) {
            val code = codeOf(file)
            assertWithMessage("%s calls a destructive use case without CriticalAppDetector", file.name)
                .that(code).contains(": CriticalAppDetector")
            assertWithMessage("%s injects CriticalAppDetector but never classifies", file.name)
                .that(code).contains(".classify(")
        }
    }

    @Test
    fun `the guard is bypassed only from the confirm callback of a warning dialog`() {
        var bypasses = 0
        for (file in uiSources) {
            val lines = codeOf(file).lines()
            lines.forEachIndexed { index, line ->
                if (!BYPASS.containsMatchIn(line)) return@forEachIndexed
                bypasses++
                assertWithMessage("%s:%s bypasses the guard outside a screen", file.name, index + 1)
                    .that(file.name).endsWith("Screen.kt")
                val above = lines.subList((index - DIALOG_WINDOW).coerceAtLeast(0), index)
                assertWithMessage("%s:%s bypasses the guard with no CriticalWarningDialog just above", file.name, index + 1)
                    .that(above.any { "CriticalWarningDialog(" in it }).isTrue()
            }
        }
        // Eight today, one per confirm callback: guards that the scan reads the right sources.
        assertWithMessage("bypasses found").that(bypasses).isAtLeast(EXPECTED_BYPASSES_AT_LEAST)
    }

    /** The source without its comments, which quote `bypassCriticalCheck = true` in KDoc. */
    private fun codeOf(file: File): String = file.readLines()
        .filterNot { it.trimStart().let { line -> line.startsWith("//") || line.startsWith("*") || line.startsWith("/*") } }
        .joinToString("\n")

    private companion object {
        /** Unit tests run from the module directory. */
        const val SOURCES = "src/main/java/com/filestech/appmanager/ui"
        const val EXPECTED_AT_LEAST = 6
        const val EXPECTED_BYPASSES_AT_LEAST = 8
        /** Lines between `CriticalWarningDialog(` and the bypass in its onConfirm. */
        const val DIALOG_WINDOW = 30
        val DESTRUCTIVE_USE_CASES = listOf("UninstallAppUseCase", "DisableEnableAppUseCase", "QuarantineAppUseCase")
        val BYPASS = Regex("""bypassCriticalCheck\s*=\s*true""")
    }
}
