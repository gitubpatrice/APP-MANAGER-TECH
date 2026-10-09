package com.filestech.appmanager.core.ext

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * [HashUtils.copyWithSha256HexLower] — the fingerprint a quarantine backup is saved with, and checked
 * against before it is restored (v0.5.1). It must describe exactly the bytes copied, whatever their
 * size relative to the stream buffer, in the format the Room column stores.
 */
class HashUtilsTest {

    private fun copy(bytes: ByteArray): Pair<String, ByteArray> {
        val out = ByteArrayOutputStream()
        val hash = HashUtils.copyWithSha256HexLower(ByteArrayInputStream(bytes), out)
        return hash to out.toByteArray()
    }

    @Test
    fun `known vectors - empty input and abc`() {
        // FIPS 180-2 test vectors.
        assertThat(copy(ByteArray(0)).first)
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        assertThat(copy("abc".toByteArray()).first)
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun `copies every byte and hashes exactly them, across several buffers`() {
        // 200 KB and an odd tail: more than three 64 KB reads, the last one partial.
        val input = ByteArray(200 * 1024 + 7) { i -> (i * 31 + 7).toByte() }

        val (hash, written) = copy(input)

        assertThat(written).isEqualTo(input)
        val expected = MessageDigest.getInstance("SHA-256").digest(input)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        assertThat(hash).isEqualTo(expected)
    }

    @Test
    fun `a single changed byte changes the fingerprint`() {
        val original = ByteArray(4096) { it.toByte() }
        val tampered = original.copyOf().also { it[2048] = (it[2048] + 1).toByte() }

        assertThat(copy(tampered).first).isNotEqualTo(copy(original).first)
    }

    @Test
    fun `same format as the persisted lifecycle hash`() {
        val input = "quarantine backup".toByteArray()

        assertThat(copy(input).first).isEqualTo(HashUtils.sha256ToHexLower(input))
    }
}
