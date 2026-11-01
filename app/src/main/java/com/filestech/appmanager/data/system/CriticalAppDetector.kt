package com.filestech.appmanager.data.system

import com.filestech.appmanager.data.local.datastore.SettingsRepository
import com.filestech.appmanager.di.ApplicationScope
import com.filestech.appmanager.domain.model.CriticalCategory
import com.filestech.appmanager.domain.model.CriticalClassification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure detector — classifies an installed package against curated whitelists
 * of sensitive apps the user should be warned about before any destructive
 * action.
 *
 * v0.2.0 — Safety Guardrails feature.
 * v0.3.1 — extended with a user-customisable whitelist (Settings → Apps protégées).
 *
 * Design:
 *  - Static curated lists per [CriticalCategory], lowercased Set<String>.
 *  - First-match wins (categories are scanned in the order they appear in
 *    [CategoryRanking] — most sensitive first).
 *  - User-customised set is loaded reactively from [SettingsRepository] into a
 *    process-lifetime [AtomicReference] cache so [classify] stays synchronous
 *    (called from VM hot paths that can NOT `await` a DataStore flow). The
 *    cache is updated by an [ApplicationScope]-scoped collector — survives
 *    Activity rotation, no leak.
 *  - Pure Kotlin, zero Android dependency — trivially testable.
 *  - No remote sync, no fetching — F-Droid-friendly.
 *
 * Maintenance:
 *  - Built-in lists are intentionally CONSERVATIVE: only include apps where
 *    data loss or account lockout is the realistic consequence of uninstall.
 *    Avoid inflating lists with apps that have trivial cloud restore — would
 *    train users to dismiss warnings.
 *  - When adding a package, prefer the production package name + verify on
 *    a real install (the Play Store displays the package id under the share
 *    URL).
 */
@Singleton
class CriticalAppDetector @Inject constructor(
    @ApplicationScope appScope: CoroutineScope,
    settings: SettingsRepository,
) {

    /**
     * Cached lowercased user-protected packages. Updated by the init-block
     * collector on every DataStore tick — never null, never out-of-sync for
     * more than a Flow emission window.
     */
    private val userProtectedCache: AtomicReference<Set<String>> = AtomicReference(emptySet())

    /** Completed once the user's list has been read (or its read has failed): see [awaitUserList]. */
    private val userListRead = CompletableDeferred<Unit>()

    init {
        appScope.launch {
            try {
                settings.flow
                    .map { it.safety.userProtectedPackages }
                    .distinctUntilChanged()
                    // Created at process start: an error here would end the process. The built-in lists
                    // keep protecting without the user's.
                    .catch { e -> Timber.w(e, "Protected apps list unreadable") }
                    .collect { fresh ->
                        userProtectedCache.set(fresh.map { it.lowercase() }.toSet())
                        userListRead.complete(Unit)
                    }
            } finally {
                // A settings flow that fails or ends must not leave classify() waiting: the built-in
                // lists still apply.
                userListRead.complete(Unit)
            }
        }
    }

    /** Returns the [CriticalCategory] for [packageName] or null if not classified. */
    fun classify(packageName: String): CriticalClassification? {
        awaitUserList()
        val key = packageName.lowercase()
        for (category in CategoryRanking) {
            val matchesBuiltIn = key in WHITELISTS.getValue(category)
            val matchesUser    = category == CriticalCategory.USER_PROTECTED &&
                key in userProtectedCache.get()
            if (matchesBuiltIn || matchesUser) {
                return CriticalClassification(packageName = packageName, category = category)
            }
        }
        return null
    }

    /**
     * The user's list is read from DataStore when this singleton is created, which MainApplication does
     * at process start, before any screen can be restored. Should a classification still come first (a
     * screen restored after process death, the user tapping Uninstall at once), it waits for that read
     * rather than answering from an empty list: an app the user protected would lose its warning. The
     * wait is bounded; past it, the built-in lists still answer.
     */
    private fun awaitUserList() {
        if (userListRead.isCompleted) return
        runBlocking { withTimeoutOrNull(USER_LIST_WAIT_MS) { userListRead.await() } }
    }

    /** Convenience probe — `true` iff the package is classified in any category. */
    fun isCritical(packageName: String): Boolean = classify(packageName) != null

    /**
     * v0.3.1 — Synchronous snapshot of the user-protected set (lowercased
     * package names). Used by the Settings "Apps protégées" screen to display
     * + edit the list without re-collecting the DataStore flow.
     */
    fun userProtectedSnapshot(): Set<String> = userProtectedCache.get()

    /**
     * v0.3.1 — Live read of the built-in whitelist for [category]. Used by the
     * "Apps protégées" screen to render the read-only sections (one per
     * built-in category). Returns a defensive copy.
     */
    fun builtInPackagesFor(category: CriticalCategory): Set<String> =
        WHITELISTS.getValue(category).toSet()

    companion object {

        /** Longest wait for the user's list in [awaitUserList]: a DataStore read takes milliseconds. */
        private const val USER_LIST_WAIT_MS = 2_000L

        /**
         * Order in which categories are scanned. Authenticators FIRST — losing
         * 2FA seeds is the worst outcome (multi-day account lockout). Then
         * password managers + banking. Health / transport / messaging next.
         *
         * [CriticalCategory.USER_PROTECTED] is LAST — checked after every
         * built-in category, so a built-in match always wins (better message
         * + safer default). v0.2.0 ships with an empty USER_PROTECTED list
         * (Settings-based customisation lands in v0.3.0 — cf. audit H-1).
         */
        private val CategoryRanking: List<CriticalCategory> = listOf(
            CriticalCategory.AUTHENTICATION,
            CriticalCategory.PASSWORD_MANAGERS,
            CriticalCategory.BANKING_FR,
            CriticalCategory.HEALTH,
            CriticalCategory.MESSAGING_E2E,
            CriticalCategory.TRANSPORT_FR,
            CriticalCategory.USER_PROTECTED,
        )

        // Every id below was checked on 2026-11-01 against the Play Store and F-Droid listings (title
        // read), or, for an app distributed elsewhere, against its own source; see each comment.
        private val AUTHENTICATION_PKGS = setOf(
            "com.beemdevelopment.aegis",            // Aegis Authenticator
            "org.shadowice.flocke.andotp",          // andOTP (discontinued 2022, still installed on older phones)
            "org.fedorahosted.freeotp",             // FreeOTP
            "org.liberty.android.freeotpplus",      // FreeOTP+
            "com.google.android.apps.authenticator2",
            "com.azure.authenticator",              // Microsoft Authenticator
            "com.authy.authy",                      // Twilio Authy
            "com.duosecurity.duomobile",
            "com.lastpass.authenticator",
            "com.twofasapp",                        // 2FAS
            "io.ente.auth",                         // Ente Auth
            "proton.android.authenticator",         // Proton Authenticator
            "com.bitwarden.authenticator",          // Bitwarden Authenticator
            "com.stratumauth.app",                  // Stratum
        )

        private val PASSWORD_MANAGER_PKGS = setOf(
            "com.x8bit.bitwarden",                  // Bitwarden
            "com.kunzisoft.keepass.libre",          // KeePassDX (F-Droid)
            "com.kunzisoft.keepass.free",
            "keepass2android.keepass2android",      // Keepass2Android
            "keepass2android.keepass2android_nonet",
            "com.onepassword.android",              // 1Password 8
            "com.agilebits.onepassword",            // 1Password 7 (withdrawn, still installed on older phones)
            "com.dashlane",
            "proton.android.pass",                  // Proton Pass
            "com.lastpass.lpandroid",
            "com.callpod.android_apps.keeper",      // Keeper
            "io.enpass.app",                        // Enpass
            "com.zoho.vault",
            "com.filestech.pass_tech",              // Pass Tech 3.x (files-tech.com, GitHub)
            "com.passtech.pass_tech",               // Pass Tech 2.x (still the release for Android 7)
        )

        // FR retail / online banks + payment apps. Focused on the Files Tech
        // FR audience. Curated, NOT exhaustive — better to miss than to over-
        // protect (we want warnings to stay meaningful).
        private val BANKING_FR_PKGS = setOf(
            "net.bnpparibas.mescomptes",            // Mes Comptes BNP Paribas
            "com.boursorama.android.clients",       // BoursoBank
            "fr.creditagricole.androidapp",
            "com.cm_prod.bad",                      // Crédit Mutuel
            "com.cic_prod.bad",                     // CIC
            "fr.banquepopulaire.cyberplus",         // Banque Populaire
            "com.caisseepargne.android.mobilebanking", // Caisse d'Epargne (renamed Banxo)
            "fr.lcl.android.customerarea",
            "com.fullsix.android.labanquepostale.accountaccess", // La Banque Postale
            "mobi.societegenerale.mobile.lappli",   // L'Appli SG
            "fr.bnpp.digitalbanking",               // Hello bank!
            "com.fortuneo.android",
            "do.helios.app",                        // helios
            "com.shine.app",                        // Shine
            "com.lydia",                            // Sumeria (ex-Lydia account)
            "com.lydia.app",                        // Lydia
            "eu.epicompany.wero.wallet",            // Wero
            "com.paypal.android.p2pmobile",
            "com.revolut.revolut",
            "de.number26.android",                  // N26
            "com.transferwise.android",             // Wise
        )

        private val HEALTH_PKGS = setOf(
            "com.google.android.apps.healthdata",   // Health Connect
            "com.sec.android.app.shealth",          // Samsung Health
            "fr.doctolib.www",                      // Doctolib
            "fr.assurancemaladie.monespacesante",   // Mon espace santé
            "fr.gouv.android.stopcovid",            // TousAntiCovid (discontinued, kept)
            "com.fitbit.FitbitMobile",              // Fitbit (mixed case: WHITELISTS lowercases it)
            "com.huawei.health",                    // Huawei Health (AppGallery only)
            "com.garmin.android.apps.connectmobile",
        )

        private val TRANSPORT_FR_PKGS = setOf(
            "com.vsct.vsc.mobile.horaireetresa.android", // SNCF Connect
            "com.fabernovel.ratp",                  // Bonjour RATP
            "com.applidium.vianavigo",              // Île-de-France Mobilités (Navigo pass)
            "com.comuto",                           // BlaBlaCar
            "com.micropole.android.tcl_mobile",     // TCL (Lyon)
            "com.ubercab",                          // Uber
            "me.lyft.android",                      // Lyft
        )

        private val MESSAGING_E2E_PKGS = setOf(
            "org.thoughtcrime.securesms",           // Signal
            "com.whatsapp",
            "org.telegram.messenger",
            "ch.threema.app",
            "ch.threema.app.libre",                 // Threema Libre (F-Droid)
            "im.vector.app",                        // Element Classic
            "io.element.android.x",                 // Element X
            "chat.simplex.app",                     // SimpleX Chat
            "im.molly.app",                         // Molly (Signal fork, its own F-Droid repository)
            "org.briarproject.briar.android",       // Briar
        )

        /**
         * v0.3.1 — USER_PROTECTED entries are now loaded reactively from
         * [SettingsRepository] into [userProtectedCache]. The built-in slot
         * for this category stays empty in [WHITELISTS] — `classify()`
         * branches to the cache via the `matchesUser` short-circuit.
         */
        private val USER_PROTECTED_BUILTIN: Set<String> = emptySet()

        // Lowercased here, once: classify() lowercases the package it is given, so a mixed-case id
        // (Fitbit's) written as the store spells it would otherwise never match.
        private val WHITELISTS: Map<CriticalCategory, Set<String>> = mapOf(
            CriticalCategory.AUTHENTICATION    to AUTHENTICATION_PKGS,
            CriticalCategory.PASSWORD_MANAGERS to PASSWORD_MANAGER_PKGS,
            CriticalCategory.BANKING_FR        to BANKING_FR_PKGS,
            CriticalCategory.HEALTH            to HEALTH_PKGS,
            CriticalCategory.TRANSPORT_FR      to TRANSPORT_FR_PKGS,
            CriticalCategory.MESSAGING_E2E     to MESSAGING_E2E_PKGS,
            CriticalCategory.USER_PROTECTED    to USER_PROTECTED_BUILTIN,
        ).mapValues { (_, packages) -> packages.map { it.lowercase() }.toSet() }
    }
}
