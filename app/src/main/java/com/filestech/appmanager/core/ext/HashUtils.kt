package com.filestech.appmanager.core.ext

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * v0.4.0 — Centralised SHA-256 helpers. Replaces two private
 * implementations that lived in
 * [com.filestech.appmanager.data.repository.AppInfoRepositoryImpl] and
 * [com.filestech.appmanager.domain.usecase.RecordLifecycleEventUseCase]
 * with one source of truth.
 *
 * Two outputs intentionally diverge :
 *  - [sha256ToColonHexUpper] : `AA:BB:CC:…` — used to render the APK
 *    signing certificate fingerprint to the user (matches the
 *    apksigner / keytool / F-Droid wire format).
 *  - [sha256ToHexLower] : `aabbcc…` (continuous lowercase) — used to
 *    persist a content hash to Room as a compact column value.
 *
 * Pick the right format at the call site ; do NOT change a format once
 * a column was written with one of them (breaks compare-on-equality).
 */
object HashUtils {

    /**
     * SHA-256 of [bytes], returned as a colon-separated uppercase-hex
     * string (e.g. `"AA:BB:CC:DD:…"`).
     *
     * Used for the APK signing certificate fingerprint surface — the
     * format matches what `apksigner verify --print-certs` and
     * `keytool -printcert` print, so the user can paste it into their
     * own tooling without translation.
     *
     * Throws [IllegalStateException] only if the JVM lacks the SHA-256
     * algorithm (impossible on Android — every JDK ships it).
     */
    fun sha256ToColonHexUpper(bytes: ByteArray): String = try {
        val md = MessageDigest.getInstance(ALGORITHM)
        val digest = md.digest(bytes)
        digest.joinToString(separator = ":") { byte -> "%02X".format(byte) }
    } catch (e: NoSuchAlgorithmException) {
        throw IllegalStateException("SHA-256 algorithm unavailable", e)
    }

    /**
     * SHA-256 of [bytes], returned as a continuous lowercase hex string
     * (e.g. `"aabbccdd…"`). 64 chars long. Suitable for persistence in
     * Room TEXT columns and for equality comparison.
     */
    fun sha256ToHexLower(bytes: ByteArray): String = try {
        val md = MessageDigest.getInstance(ALGORITHM)
        val digest = md.digest(bytes)
        digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    } catch (e: NoSuchAlgorithmException) {
        throw IllegalStateException("SHA-256 algorithm unavailable", e)
    }

    /**
     * Streams [file] through a SHA-256 digest in 64 KB chunks and
     * returns the continuous lowercase hex result. Designed for the
     * APK forensic-hash use case (APKs are typically 1 – 100 MB and
     * occasionally larger — we never materialise the full content in
     * memory).
     *
     * Returns `null` when :
     *  - the file does not exist, is not a regular file, or is
     *    unreadable,
     *  - an [IOException] is thrown during the stream read,
     *  - a [SecurityException] denies access mid-read.
     *
     * Caller is responsible for any size cap / path validation BEFORE
     * invoking this helper (this function only owns the hashing
     * mechanic, not the security policy around it).
     */
    fun sha256FileToHexLower(file: File): String? {
        if (!file.isFile || !file.canRead()) return null
        return try {
            val digest = MessageDigest.getInstance(ALGORITHM)
            FileInputStream(file).use { input ->
                val buf = ByteArray(STREAM_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("SHA-256 algorithm unavailable", e)
        }
    }

    private const val ALGORITHM = "SHA-256"
    /** 64 KB streaming buffer — same as the prior private constant. */
    private const val STREAM_BUFFER_BYTES = 64 * 1024
}
