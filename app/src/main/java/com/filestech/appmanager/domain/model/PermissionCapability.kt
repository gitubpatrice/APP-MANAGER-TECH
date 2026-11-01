package com.filestech.appmanager.domain.model

/**
 * What a dangerous permission lets an app do: one line of the privacy panel, one penalty of the privacy
 * score. The score and the panel both read [capabilityOf], so the two cannot disagree on what counts.
 *
 * Declared in panel order, most sensitive first.
 */
enum class PermissionCapability {
    LOCATION,
    LOCATION_APPROX,
    LOCATION_BACKGROUND,
    CAMERA,
    MICROPHONE,
    CONTACTS,
    CALENDAR,
    SMS,
    CALL_LOG,
    PHONE,
    HEALTH,
    SENSORS,
    ACTIVITY,
    STORAGE,
    MEDIA,
    MEDIA_LOCATION,
    BLUETOOTH,
    NEARBY,
    NOTIFICATIONS,
    ;

    /**
     * The score takes one penalty per group: an app asking for precise location must also declare the
     * approximate one (Android 12+), and the pair is one capability, not two.
     */
    val scoreGroup: PermissionCapability get() = if (this == LOCATION_APPROX) LOCATION else this

    companion object {

        private const val HEALTH_PREFIX = "android.permission.health."

        /**
         * The platform permissions whose protection level is `dangerous`, read from
         * `dumpsys package permissions` (prot=dangerous, sourcePackage=android) on API 34 and API 36 on
         * 2026-11-01: 42 and 43 (`RANGING` is new in 36). Health Connect's `android.permission.health.*`,
         * also dangerous, are defined by another package and matched by prefix in [capabilityOf].
         */
        private val PLATFORM: Map<String, PermissionCapability> = mapOf(
            "android.permission.ACCESS_FINE_LOCATION"            to LOCATION,
            "android.permission.ACCESS_COARSE_LOCATION"          to LOCATION_APPROX,
            "android.permission.ACCESS_BACKGROUND_LOCATION"      to LOCATION_BACKGROUND,
            "android.permission.CAMERA"                          to CAMERA,
            "android.permission.RECORD_AUDIO"                    to MICROPHONE,
            "android.permission.READ_CONTACTS"                   to CONTACTS,
            "android.permission.WRITE_CONTACTS"                  to CONTACTS,
            "android.permission.GET_ACCOUNTS"                    to CONTACTS,
            "android.permission.READ_CALENDAR"                   to CALENDAR,
            "android.permission.WRITE_CALENDAR"                  to CALENDAR,
            "android.permission.READ_SMS"                        to SMS,
            "android.permission.SEND_SMS"                        to SMS,
            "android.permission.RECEIVE_SMS"                     to SMS,
            "android.permission.RECEIVE_MMS"                     to SMS,
            "android.permission.RECEIVE_WAP_PUSH"                to SMS,
            "android.permission.READ_CELL_BROADCASTS"            to SMS,
            "android.permission.READ_CALL_LOG"                   to CALL_LOG,
            "android.permission.WRITE_CALL_LOG"                  to CALL_LOG,
            "android.permission.PROCESS_OUTGOING_CALLS"          to CALL_LOG,
            "android.permission.READ_PHONE_STATE"                to PHONE,
            "android.permission.READ_PHONE_NUMBERS"              to PHONE,
            "android.permission.CALL_PHONE"                      to PHONE,
            "android.permission.ANSWER_PHONE_CALLS"              to PHONE,
            "android.permission.USE_SIP"                         to PHONE,
            "android.permission.ACCEPT_HANDOVER"                 to PHONE,
            "com.android.voicemail.permission.ADD_VOICEMAIL"     to PHONE,
            "android.permission.BODY_SENSORS"                    to SENSORS,
            "android.permission.BODY_SENSORS_BACKGROUND"         to SENSORS,
            "android.permission.ACTIVITY_RECOGNITION"            to ACTIVITY,
            "android.permission.READ_EXTERNAL_STORAGE"           to STORAGE,
            "android.permission.WRITE_EXTERNAL_STORAGE"          to STORAGE,
            "android.permission.READ_MEDIA_IMAGES"               to MEDIA,
            "android.permission.READ_MEDIA_VIDEO"                to MEDIA,
            "android.permission.READ_MEDIA_AUDIO"                to MEDIA,
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" to MEDIA,
            "android.permission.ACCESS_MEDIA_LOCATION"           to MEDIA_LOCATION,
            "android.permission.BLUETOOTH_CONNECT"               to BLUETOOTH,
            "android.permission.BLUETOOTH_SCAN"                  to BLUETOOTH,
            "android.permission.BLUETOOTH_ADVERTISE"             to BLUETOOTH,
            "android.permission.NEARBY_WIFI_DEVICES"             to NEARBY,
            "android.permission.UWB_RANGING"                     to NEARBY,
            "android.permission.RANGING"                         to NEARBY,
            "android.permission.POST_NOTIFICATIONS"              to NOTIFICATIONS,
        )

        /** The capability [permission] grants, or null if it is not a dangerous permission. */
        fun capabilityOf(permission: String): PermissionCapability? =
            PLATFORM[permission] ?: HEALTH.takeIf { permission.startsWith(HEALTH_PREFIX) }

        /** Number of platform permissions in the table (tests pin it against the measured list). */
        internal val platformPermissionCount: Int get() = PLATFORM.size
    }
}
