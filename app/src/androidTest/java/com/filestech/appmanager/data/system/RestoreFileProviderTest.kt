package com.filestech.appmanager.data.system

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * v0.5.1 — the restore FileProvider as the MERGED manifest declares it. `ApkBackupManager` builds its
 * authority from the package name and serves `cache/restore/`; if the manifest's authority or
 * `res/xml/restore_paths.xml` drifted from that, `getUriForFile` would throw, the catch-all would turn
 * it into "unreadable", and every restore would be refused — invisibly to the unit tests, which run
 * without a manifest. Here, on a device, with the real one.
 */
@RunWith(AndroidJUnit4::class)
class RestoreFileProviderTest {

    @Test
    fun a_verified_copy_in_cache_restore_gets_a_content_uri_from_the_restore_authority() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val copy = File(File(context.cacheDir, "restore"), "check.apk")

        val uri = FileProvider.getUriForFile(context, context.packageName + ".restore", copy)

        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo(context.packageName + ".restore")
        assertThat(uri.path).endsWith("/check.apk")
    }

    @Test(expected = IllegalArgumentException::class)
    fun the_unverified_working_folder_is_not_served() {
        // restore-work/ holds copies BEFORE their fingerprint is checked: the provider must refuse it.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val work = File(File(context.cacheDir, "restore-work"), "check.apk")

        FileProvider.getUriForFile(context, context.packageName + ".restore", work)
    }
}
