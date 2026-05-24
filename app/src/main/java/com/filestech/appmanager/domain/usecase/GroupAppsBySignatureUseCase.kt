package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.core.result.AppError
import com.filestech.appmanager.core.result.Outcome
import com.filestech.appmanager.core.result.getOrNull
import com.filestech.appmanager.core.result.runCatchingOutcome
import com.filestech.appmanager.di.IoDispatcher
import com.filestech.appmanager.domain.model.SignatureCluster
import com.filestech.appmanager.domain.repository.AppInfoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * v0.3.3 — Groups installed apps by their signing certificate SHA-256.
 *
 * Pure in-memory aggregation : pulls the user-installed app list from
 * [AppInfoRepository.observeApps] (cache → fast) and, for each package,
 * fetches its SHA-256 via [AppInfoRepository.getSignatureSha256]
 * (per-package PM IPC — typically < 5 ms each).
 *
 * Apps that fail signature resolution (sharedUserId proxies, unusual
 * OEM stripping) are quietly dropped from the result rather than crashing
 * the whole call — the UI still shows the clusters we DID find.
 *
 * Sort:
 * - SHARED clusters first (size > 1), ranked by size desc then total bytes
 *   desc — the user is most interested in "two apps share a signer".
 * - Single-app clusters (the common case) come after, sorted by label.
 *
 * Cost : O(N) PM IPC calls per scan. The UI surfaces a loading spinner;
 * `withContext(io)` keeps it off the main thread.
 */
class GroupAppsBySignatureUseCase @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
    private val repository: AppInfoRepository,
) {

    suspend operator fun invoke(): Outcome<List<SignatureCluster>> =
        runCatchingOutcome(mapError = { AppError.Unknown(it) }) {
            withContext(io) {
                val apps = repository.observeApps(includeSystemApps = false)
                    .first()
                    .getOrNull()
                    .orEmpty()
                if (apps.isEmpty()) return@withContext emptyList()

                val bySig = HashMap<String, MutableList<com.filestech.appmanager.domain.model.AppInfo>>()
                for (app in apps) {
                    val sha = repository.getSignatureSha256(app.packageName).getOrNull()
                        ?: continue
                    bySig.getOrPut(sha) { mutableListOf() }.add(app)
                }

                Timber.i("GroupAppsBySignature: %d apps → %d clusters", apps.size, bySig.size)

                bySig.map { (sha, list) ->
                    SignatureCluster(
                        signatureSha256 = sha,
                        apps            = list.sortedBy { it.label.lowercase() },
                    )
                }.sortedWith(
                    compareByDescending<SignatureCluster> { it.isShared }
                        .thenByDescending { it.size }
                        .thenByDescending { it.totalBytes }
                        .thenBy { it.apps.firstOrNull()?.label?.lowercase() ?: "" },
                )
            }
        }
}
