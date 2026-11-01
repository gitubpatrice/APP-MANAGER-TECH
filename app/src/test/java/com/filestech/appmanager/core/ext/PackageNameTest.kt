package com.filestech.appmanager.core.ext

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [isValidPackageName]: two segments at least, except the framework's own `android` — the one installed
 * single-segment package, listed among system apps, whose tracker scan and detail page it used to refuse.
 */
class PackageNameTest {

    @Test
    fun `ordinary package names are valid`() {
        assertThat("com.filestech.appmanager".isValidPackageName()).isTrue()
        assertThat("org.fdroid.fdroid".isValidPackageName()).isTrue()
        assertThat("com.example_app.x2".isValidPackageName()).isTrue()
    }

    @Test
    fun `the framework package android is valid`() {
        assertThat("android".isValidPackageName()).isTrue()
    }

    @Test
    fun `other single segments and malformed names are not`() {
        assertThat("androidx".isValidPackageName()).isFalse()
        assertThat("android.".isValidPackageName()).isFalse()
        assertThat("Android".isValidPackageName()).isFalse()
        assertThat("com..example".isValidPackageName()).isFalse()
        assertThat("com.example/../x".isValidPackageName()).isFalse()
        assertThat("1com.example".isValidPackageName()).isFalse()
        assertThat("".isValidPackageName()).isFalse()
        assertThat("a.${"b".repeat(300)}".isValidPackageName()).isFalse()
    }
}
