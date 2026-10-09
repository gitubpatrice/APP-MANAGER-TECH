package com.filestech.appmanager.data.system

import com.filestech.appmanager.core.ext.HashUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * v0.5.1 — the one place a quarantine backup becomes a file the installer may read.
 *
 * [copyIfMatches] copies the backup into [workDir], which nothing serves, hashing it in the same pass;
 * only if the SHA-256 is the one recorded when the backup was written does the file move — by rename,
 * atomic on one file system — into [outDir], the folder the restore FileProvider serves, under a
 * name used once. So:
 *  - no unverified byte is ever reachable through the provider, not even a partial copy;
 *  - a second restore never rewrites a file an installer may still be reading (a fresh name each time,
 *    and an open descriptor keeps the old inode);
 *  - a failed or mismatching copy leaves nothing behind.
 *
 * Plain JVM (no Android type), so it is unit-tested as is: `VerifiedCopyTest`.
 */
object VerifiedCopy {

    /**
     * Returns the verified file in [outDir], or null when the copy's SHA-256 is not [expectedSha256]
     * (nothing published). I/O errors propagate, a failed publication included; the working copy is
     * deleted in every case.
     */
    fun copyIfMatches(input: InputStream, expectedSha256: String, workDir: File, outDir: File): File? {
        workDir.mkdirs()
        outDir.mkdirs()
        val name = "${UUID.randomUUID()}.apk"
        val work = File(workDir, name)
        try {
            val actual = FileOutputStream(work).use { output -> HashUtils.copyWithSha256HexLower(input, output) }
            if (actual != expectedSha256) return null
            val published = File(outDir, name)
            if (!work.renameTo(published)) throw IOException("Could not publish the verified copy")
            return published
        } finally {
            work.delete()
        }
    }

    /**
     * Deletes the files of [dir] last modified more than [maxAgeMs] before [nowMs]. Verified copies are
     * kept long enough for any installer to have staged them, and never pile up.
     */
    fun deleteOlderThan(dir: File, maxAgeMs: Long, nowMs: Long) {
        dir.listFiles()?.forEach { if (nowMs - it.lastModified() > maxAgeMs) it.delete() }
    }
}
