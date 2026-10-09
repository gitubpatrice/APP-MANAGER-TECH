package com.filestech.appmanager.data.system

import com.filestech.appmanager.core.ext.HashUtils
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * [VerifiedCopy] — what a restore hands the installer (v0.5.1): the backup is copied into a folder
 * nothing serves, and published into the served folder only if its SHA-256 is the one recorded when it
 * was saved. No unverified byte may ever sit in the served folder.
 */
class VerifiedCopyTest {

    @TempDir
    lateinit var root: File

    private val work get() = File(root, "restore-work")
    private val out get() = File(root, "restore")
    private val apk = ByteArray(150 * 1024) { i -> (i * 7 + 3).toByte() }
    private val apkSha256 = HashUtils.sha256ToHexLower(apk)

    @Test
    fun `an intact backup is published, byte for byte, under a fresh name`() {
        val first = VerifiedCopy.copyIfMatches(ByteArrayInputStream(apk), apkSha256, work, out)
        val second = VerifiedCopy.copyIfMatches(ByteArrayInputStream(apk), apkSha256, work, out)

        assertThat(first).isNotNull()
        assertThat(first!!.parentFile).isEqualTo(out)
        assertThat(first.readBytes()).isEqualTo(apk)
        // A second restore never rewrites the file an installer may still be reading.
        assertThat(second!!.name).isNotEqualTo(first.name)
        assertThat(first.readBytes()).isEqualTo(apk)
        assertThat(work.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a changed backup is refused and nothing reaches the served folder`() {
        val tampered = apk.copyOf().also { it[42] = (it[42] + 1).toByte() }

        val result = VerifiedCopy.copyIfMatches(ByteArrayInputStream(tampered), apkSha256, work, out)

        assertThat(result).isNull()
        assertThat(out.listFiles().orEmpty()).isEmpty()
        assertThat(work.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a read that fails midway leaves no partial file anywhere`() {
        val failing = object : InputStream() {
            private var served = 0
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (served > 70_000) throw IOException("provider gone")
                served += len
                return len
            }
        }

        assertThrows<IOException> { VerifiedCopy.copyIfMatches(failing, apkSha256, work, out) }
        assertThat(out.listFiles().orEmpty()).isEmpty()
        assertThat(work.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `only copies older than the keep time are dropped`() {
        out.mkdirs()
        val now = 10_000_000L
        val old = File(out, "old.apk").apply { writeBytes(apk); setLastModified(now - 7_200_000L) }
        val recent = File(out, "recent.apk").apply { writeBytes(apk); setLastModified(now - 60_000L) }

        VerifiedCopy.deleteOlderThan(out, maxAgeMs = 3_600_000L, nowMs = now)

        assertThat(old.exists()).isFalse()
        assertThat(recent.exists()).isTrue()
    }
}
