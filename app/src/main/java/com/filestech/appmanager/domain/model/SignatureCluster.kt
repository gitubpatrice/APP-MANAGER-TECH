package com.filestech.appmanager.domain.model

/**
 * v0.3.3 — Group of installed apps that share the same signing certificate
 * SHA-256 fingerprint.
 *
 * Built by `GroupAppsBySignatureUseCase`. Useful for transparency
 * ("show me every app signed by the Samsung cert") + forensic curiosity
 * ("two of these apps share the same signer — they're from the same
 * editor"). Not persisted — recomputed on each scan.
 *
 * Pure domain — zero Android dependency, trivially testable.
 */
data class SignatureCluster(
    /** Uppercase colon-separated SHA-256 hex of the signing certificate. */
    val signatureSha256: String,
    /** Apps installed on the device that match this fingerprint. */
    val apps: List<AppInfo>,
) {
    /** Convenience accessor — the number of apps in this cluster. */
    val size: Int get() = apps.size

    /** Single-app clusters are the common case (most apps have unique signers). */
    val isShared: Boolean get() = size > 1

    /** Total disk footprint of the cluster (install + data + cache). */
    val totalBytes: Long get() = apps.sumOf { it.totalSizeBytes }
}
