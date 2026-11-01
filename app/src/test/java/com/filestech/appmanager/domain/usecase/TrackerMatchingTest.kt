package com.filestech.appmanager.domain.usecase

import com.filestech.appmanager.domain.model.Tracker
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests [trackerCandidateNames] and [matchTrackers], the pure half of [DetectTrackersUseCase].
 * The component and meta-data names are those the SDK AARs declare (manifests read on Google's Maven,
 * play-services-measurement 24.0.0, firebase-crashlytics 21.0.1, play-services-measurement-api 24.0.0).
 */
class TrackerMatchingTest {

    private val firebaseAnalytics = Tracker(
        id = "firebase_analytics", name = "Google Firebase Analytics", category = "Analytics",
        signatures = listOf("com.google.firebase.analytics.", "com.google.android.gms.measurement."),
    )
    private val crashlytics = Tracker(
        id = "crashlytics", name = "Firebase Crashlytics", category = "Crash reporter",
        signatures = listOf("com.google.firebase.crashlytics."),
    )
    private val facebookAnalytics = Tracker(
        id = "fb_appevents", name = "Facebook Analytics", category = "Analytics",
        signatures = listOf("com.facebook.appevents.", "com.facebook.internal.FacebookInitProvider"),
    )
    private val appsflyer = Tracker(
        id = "appsflyer", name = "AppsFlyer", category = "Attribution", signatures = listOf("com.appsflyer."),
    )
    private val index = listOf(firebaseAnalytics, crashlytics, facebookAnalytics, appsflyer)
        .flatMap { t -> t.signatures.map { it to t } }

    private fun detect(vararg components: DeclaredComponent) =
        matchTrackers(trackerCandidateNames(components.toList()), index)

    @Test
    fun `Firebase Analytics is seen through its measurement receiver`() {
        val found = detect(component("com.google.android.gms.measurement.AppMeasurementReceiver"))
        assertThat(found).containsExactly(firebaseAnalytics)
    }

    @Test
    fun `Crashlytics is seen through its Firebase registrar, its only trace in a manifest`() {
        val discovery = component(
            "com.google.firebase.components.ComponentDiscoveryService",
            "com.google.firebase.components:com.google.firebase.crashlytics.FirebaseCrashlyticsKtxRegistrar" to
                "com.google.firebase.components.ComponentRegistrar",
            "com.google.firebase.components:com.google.firebase.analytics.connector.internal.AnalyticsConnectorRegistrar" to
                "com.google.firebase.components.ComponentRegistrar",
        )
        assertThat(detect(discovery)).containsExactly(crashlytics, firebaseAnalytics).inOrder()
    }

    @Test
    fun `an AndroidX Startup initializer is matched by its class`() {
        val startup = component(
            "androidx.startup.InitializationProvider",
            "com.appsflyer.internal.AppsFlyerInitializer" to "androidx.startup",
        )
        assertThat(detect(startup)).containsExactly(appsflyer)
    }

    @Test
    fun `meta-data that is not a class registration is ignored`() {
        // An app id under a tracker's own package prefix: a value, not a registered class.
        val app = component("com.example.MainActivity", "com.appsflyer.AppsFlyerKey" to "abc123")
        assertThat(detect(app)).isEmpty()
    }

    @Test
    fun `a class signature matches that class and its nested classes only`() {
        assertThat(detect(component("com.facebook.internal.FacebookInitProvider"))).containsExactly(facebookAnalytics)
        assertThat(detect(component("com.facebook.internal.FacebookInitProvider\$Inner"))).containsExactly(facebookAnalytics)
        assertThat(detect(component("com.facebook.internal.FacebookInitProviderProxy"))).isEmpty()
    }

    @Test
    fun `a tracker matched by several names is reported once`() {
        val found = detect(
            component("com.google.android.gms.measurement.AppMeasurementReceiver"),
            component("com.google.android.gms.measurement.AppMeasurementService"),
        )
        assertThat(found).containsExactly(firebaseAnalytics)
    }

    @Test
    fun `an app without tracker classes is clean`() {
        assertThat(detect(component("com.filestech.notes_tech.MainActivity"))).isEmpty()
    }

    private fun component(name: String, vararg meta: Pair<String, String?>) =
        DeclaredComponent(name = name, metaData = meta.toMap())
}
