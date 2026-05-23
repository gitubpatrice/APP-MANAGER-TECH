package com.filestech.appmanager.data.system

import com.filestech.appmanager.domain.model.CriticalCategory
import com.filestech.appmanager.domain.model.CriticalClassification
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure detector — classifies an installed package against curated whitelists
 * of sensitive apps the user should be warned about before any destructive
 * action.
 *
 * v0.2.0 — Safety Guardrails feature.
 *
 * Design:
 *  - Static curated lists per [CriticalCategory], lowercased Set<String>.
 *  - First-match wins (categories are scanned in the order they appear in
 *    [CategoryRanking] — most sensitive first).
 *  - Pure Kotlin, zero Android dependency — trivially testable.
 *  - No remote sync, no fetching — F-Droid-friendly.
 *
 * Maintenance:
 *  - Lists are intentionally CONSERVATIVE: only include apps where data loss
 *    or account lockout is the realistic consequence of uninstall. Avoid
 *    inflating lists with apps that have trivial cloud restore — would
 *    train users to dismiss warnings.
 *  - When adding a package, prefer the production package name + verify on
 *    a real install (the Play Store displays the package id under the share
 *    URL).
 */
@Singleton
class CriticalAppDetector @Inject constructor() {

    /** Returns the [CriticalCategory] for [packageName] or null if not classified. */
    fun classify(packageName: String): CriticalClassification? {
        val key = packageName.lowercase()
        for (category in CategoryRanking) {
            if (key in WHITELISTS.getValue(category)) {
                return CriticalClassification(packageName = packageName, category = category)
            }
        }
        return null
    }

    /** Convenience probe — `true` iff the package is classified in any category. */
    fun isCritical(packageName: String): Boolean = classify(packageName) != null

    companion object {

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

        private val AUTHENTICATION_PKGS = setOf(
            "com.beemdevelopment.aegis",            // Aegis Authenticator (F-Droid)
            "org.shadowice.flocke.andotp",          // andOTP (legacy F-Droid)
            "org.fedorahosted.freeotp",             // FreeOTP
            "com.google.android.apps.authenticator2",
            "com.azure.authenticator",              // Microsoft Authenticator
            "com.authy.authy",
            "com.duosecurity.duomobile",
            "com.lastpass.authenticator",
            "io.raivo.android",
            "com.twilio.authy",
            "io.gitlab.zenofile.zenotp",            // Zen OTP (F-Droid)
            "me.lemoo.openauth",
        )

        private val PASSWORD_MANAGER_PKGS = setOf(
            "com.x8bit.bitwarden",                  // Bitwarden
            "com.kunzisoft.keepass.libre",          // KeePassDX (F-Droid)
            "com.kunzisoft.keepass.free",
            "com.agilebits.onepassword",            // 1Password
            "com.dashlane",
            "proton.android.pass",                  // Proton Pass
            "com.lastpass.lpandroid",
            "com.keepersecurity.passwordmanager",
            "com.enpass.app",
            "com.zoho.vault",
        )

        // FR retail / online banks + payment apps. Focused on the Files Tech
        // FR audience. Curated, NOT exhaustive — better to miss than to over-
        // protect (we want warnings to stay meaningful).
        private val BANKING_FR_PKGS = setOf(
            "net.bnpparibas.mabanque",
            "fr.boursorama.android.clients",
            "fr.creditagricole.androidapp",
            "com.cm_prod.bad",                      // Crédit Mutuel
            "com.cic_prod.bad",                     // CIC
            "net.banquepopulaire.cyberplus",
            "com.caisseepargne.cebanqueavous",
            "fr.lcl.android.customerarea",
            "com.banquepostale.client",
            "fr.laposte.lapostemobile",
            "com.societegenerale.mobile.lappli",
            "fr.hellobank",                         // Hello bank! (BNP)
            "com.fortuneo.android",
            "com.helios.bank",                      // helios
            "com.shine.android",
            "fr.lydia.app",
            "com.lydia.lydia",
            "com.wero.app",
            "com.paypal.android.p2pmobile",
            "com.revolut.revolut",
            "de.number26.android",                  // N26
            "com.wise.android",                     // Wise (formerly TransferWise)
            "fr.orangebank.orangebank",
        )

        private val HEALTH_PKGS = setOf(
            "com.google.android.apps.healthdata",   // Health Connect
            "com.sec.android.app.shealth",          // Samsung Health
            "fr.doctolib.app",                      // Doctolib
            "fr.cnam.monespacesante",               // Mon Espace Santé
            "fr.gouv.tousanticovid",                // TousAntiCovid (legacy, kept)
            "com.fitbit.FitbitMobile",
            "com.huawei.health",
            "com.garmin.android.apps.connectmobile",
        )

        private val TRANSPORT_FR_PKGS = setOf(
            "fr.sncf.connect.app",                  // SNCF Connect
            "com.app.sncf",
            "fr.ratp.ratp",
            "com.bonjour.ratp",
            "fr.iledefrance_mobilites",             // Île-de-France Mobilités
            "com.thalesgroup.smartlife",            // Navigo
            "com.comuto",                           // BlaBlaCar
            "fr.ouisncf",                           // Oui.sncf (legacy)
            "fr.tcl.android",                       // Lyon
            "fr.transgvexpress",                    // various
            "com.uber.android",
            "com.lyft.android",
        )

        private val MESSAGING_E2E_PKGS = setOf(
            "org.thoughtcrime.securesms",           // Signal
            "com.whatsapp",
            "org.telegram.messenger",
            "ch.threema.app",
            "ch.threema.libre",                     // Threema Libre (F-Droid)
            "im.vector.app",                        // Element
            "im.vector.app.x",                      // Element X
            "chat.simplex.app",                     // SimpleX Chat
            "im.molly.app",                         // Molly (Signal fork)
            "org.briarproject.briar.android",       // Briar
        )

        // Placeholder — v0.3.0 will load this from a DataStore-backed
        // user-customisation Settings screen ("Apps protégées" → "Ajouter").
        // For v0.2.0 the set stays empty so the enum + UI plumbing exists
        // end-to-end (avoids "phantom enum branch" anti-pattern flagged by
        // the H-1 audit finding).
        private val USER_PROTECTED_PKGS: Set<String> = emptySet()

        private val WHITELISTS: Map<CriticalCategory, Set<String>> = mapOf(
            CriticalCategory.AUTHENTICATION    to AUTHENTICATION_PKGS,
            CriticalCategory.PASSWORD_MANAGERS to PASSWORD_MANAGER_PKGS,
            CriticalCategory.BANKING_FR        to BANKING_FR_PKGS,
            CriticalCategory.HEALTH            to HEALTH_PKGS,
            CriticalCategory.TRANSPORT_FR      to TRANSPORT_FR_PKGS,
            CriticalCategory.MESSAGING_E2E     to MESSAGING_E2E_PKGS,
            CriticalCategory.USER_PROTECTED    to USER_PROTECTED_PKGS,
        )
    }
}
