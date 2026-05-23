package com.filestech.appmanager.domain.model

/**
 * Categories of "critical" apps — apps a user should be warned about before
 * any destructive action (uninstall, disable, clear data, quarantine).
 *
 * v0.2.0 — Safety Guardrails feature. Detected by
 * [com.filestech.appmanager.data.system.CriticalAppDetector] against
 * hardcoded curated package whitelists per category.
 *
 * The categorisation drives the warning copy: an uninstall warning for a 2FA
 * authenticator app says different things than the same warning for a
 * banking app.
 *
 * Persisted name (`name`) is what `data store` keeps in user-customization
 * settings — never rename existing constants (would break backward compat
 * on user-customised lists).
 */
enum class CriticalCategory {

    /**
     * 2FA / TOTP authenticators (Aegis, andOTP, FreeOTP, Google
     * Authenticator, Microsoft Authenticator, Authy, Raivo).
     *
     * Uninstall risk: lose access to every account whose 2FA shared secret is
     * only stored in this app. May lock the user out for days without a
     * backup of the seeds.
     */
    AUTHENTICATION,

    /**
     * French retail / online banking + payment apps. List is FR-focused for
     * the Files Tech audience — could be extended per locale in a future
     * release.
     *
     * Uninstall risk: lose registered cards, recurring payment configuration,
     * fingerprint binding for in-app validation.
     */
    BANKING_FR,

    /**
     * Password managers (Bitwarden, KeePassDX, 1Password, Dashlane, Proton
     * Pass). Even with cloud backup, local biometric unlock binding is lost.
     */
    PASSWORD_MANAGERS,

    /**
     * Health apps that bind to OS Health Connect or hold doctor / vaccine /
     * carte vitale data (Doctolib, Mon Espace Santé, Samsung Health).
     */
    HEALTH,

    /**
     * French public transport ticketing — losing the app may invalidate
     * stored tickets, Navigo binding, or weekly pass renewals.
     */
    TRANSPORT_FR,

    /**
     * End-to-end-encrypted messaging — uninstall WIPES the local key and
     * all messages with it. Backup must be exported first (not all apps
     * support exports).
     */
    MESSAGING_E2E,

    /**
     * Apps explicitly added to the protected list by the user themselves
     * (Settings → Apps protégées → Ajouter). Catch-all for cases the
     * hardcoded categorisation does not cover (regional banking, niche
     * authenticators, etc.).
     */
    USER_PROTECTED,
}
