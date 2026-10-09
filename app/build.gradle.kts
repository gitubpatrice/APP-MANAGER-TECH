import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // No `org.jetbrains.kotlin.android`: AGP 9 compiles Kotlin itself.
    alias(libs.plugins.kotlin.compose)   // Compose compiler plugin (Kotlin 2.x, NOT composeOptions)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// ---------------------------------------------------------------------------
// Signing — loaded from keystore.properties (never committed to git)
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    // AGP glisse par defaut, dans le bloc de signature de l'APK, la liste CHIFFREE de nos
    // dependances, a destination de la console Play. Aucune application Files Tech n'est
    // publiee sur Play : ce bloc ne sert rien ici, et un blob illisible n'a pas sa place dans
    // un binaire dont tout l'argument est d'etre verifiable. Le scanner F-Droid le refuse
    // (« found extra signing block 'Dependency metadata' »).
    //
    // Mesure sur Agenda Tech : 9085 octets retires. Constate present sur TOUTES les apps du
    // portefeuille le 2026-08-14 — il ne pouvait pas etre vu plus tot, car les controles
    // n'analysaient que des APK NON SIGNES, qui n'ont pas de bloc de signature.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    namespace   = "com.filestech.appmanager"
    // compileSdk 37 as in Agenda Tech: the AndroidX releases built for AGP 9 require it. targetSdk
    // stays 35, so the app's runtime behaviour on the device does not change with this upgrade.
    compileSdk  = 37

    defaultConfig {
        applicationId = "com.filestech.appmanager"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 15
        versionName   = "0.5.1"

        // Etiquette du lanceur, par type de build. Le suffixe `.debug` ci-dessous laisse les deux
        // variantes coexister sur un meme telephone ; sans etiquette distincte, elles y portaient
        // le meme nom et la meme icone, et rien ne permettait de savoir laquelle on testait.
        //
        // Un `manifestPlaceholders` et NON un `resValue` : `app_name` est localise (values/ et
        // values-fr/), or une chaine generee dans `values/` perd contre `values-fr/` sur un
        // appareil en francais — le renommage ne ferait alors rien, en silence. Piege mesure sur
        // SMS Tech le 2026-09-07, ou une session de test entiere est passee sur la mauvaise
        // variante. Meme patron que `agenda_tech`.
        manifestPlaceholders["appLabel"] = "App Manager Tech"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ---------------------------------------------------------------------------
    // Signing configs
    // ---------------------------------------------------------------------------
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias      = keystoreProperties["keyAlias"]      as String
                keyPassword   = keystoreProperties["keyPassword"]   as String
                storeFile     = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Build types
    // ---------------------------------------------------------------------------
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable        = true
            isMinifyEnabled     = false
            manifestPlaceholders["appLabel"] = "App Manager Tech (debug)"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Kotlin / Java compatibility
    // ---------------------------------------------------------------------------
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Keep only the shipped languages in the resource bundle. `localeFilters` replaces
    // `resourceConfigurations`, which AGP 9 no longer offers. The list must match the translated
    // `values-*` folders and res/xml/locales_config.xml: a language missing here is removed from the
    // APK without error. tools/check-translations.py fails the build when they disagree.
    androidResources {
        localeFilters += listOf("en", "fr", "de", "it", "es")
    }

    // ---------------------------------------------------------------------------
    // Build features
    // ---------------------------------------------------------------------------
    buildFeatures {
        compose    = true
        buildConfig = true
    }

    // ---------------------------------------------------------------------------
    // ABI splits
    // ---------------------------------------------------------------------------
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // ---------------------------------------------------------------------------
    // Test options — JUnit 5 via useJUnitPlatform()
    // ---------------------------------------------------------------------------
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()
            }
        }
    }
}

// Kotlin 2.3+: the `compilerOptions` DSL replaces `kotlinOptions`, which is gone.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// ---------------------------------------------------------------------------
// Room schema export — the dedicated plugin owns room.schemaLocation; do NOT
// also pass it through `ksp { arg(...) }` (would trigger a conflict error).
// Schemas land in <project>/schemas/<db-class-fqcn>/<version>.json and are
// checked into git so MigrationTest can replay every historical schema.
// ---------------------------------------------------------------------------
room {
    schemaDirectory("$projectDir/../schemas")
}

// MigrationTestHelper reads these schemas from the androidTest ASSETS. Since Room 2.8 the plugin
// packages them itself (copyRoomSchemasToAndroidTestAssets); under Room 2.6 nothing did, and the
// source set had to list schemas/ by hand. It packages the COMMITTED files, before KSP runs
// (measured 2026-10-08 with 8.json removed: the test APK carried 1-7 only), so a new database
// version's schema must be committed before its migration test can pass.

dependencies {
    // --- Core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // --- Lifecycle ---
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // --- Compose ---
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // --- Navigation ---
    implementation(libs.androidx.navigation.compose)

    // --- Hilt ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // --- Room ---
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // --- DataStore ---
    implementation(libs.datastore.preferences)

    // --- DocumentFile (SAF tree URI wrapping for APK backups) ---
    implementation(libs.androidx.documentfile)

    // --- SplashScreen ---
    implementation(libs.androidx.splashscreen)

    // --- Coroutines ---
    implementation(libs.coroutines.android)

    // --- WorkManager ---
    implementation(libs.work.runtime.ktx)

    // --- Coil ---
    implementation(libs.coil.compose)

    // --- Timber ---
    implementation(libs.timber)

    // --- Debug tooling ---
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // --- Version constraint, not a new dependency ---
    // Room 2.8 reads the exported schemas with kotlinx-serialization 1.8.1 (room-testing, in the
    // instrumented migration tests). Instrumented tests run against the APP's library versions, and
    // the app resolved kotlinx-serialization-core 1.6.3 (pulled by navigation-compose): the seven
    // migration tests died on an AbstractMethodError (measured on a Galaxy S9, 2026-10-08). This
    // raises the version the app already carries, in the safe direction - a newer runtime runs code
    // generated for an older one, not the reverse - and adds nothing the app did not have.
    constraints {
        implementation(libs.kotlinx.serialization.core) {
            because("room-testing 2.8.5 needs kotlinx-serialization 1.8.1 in the instrumented tests")
        }
    }

    // --- Unit tests ---
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.room.testing)
    testImplementation(libs.turbine)

    // --- Instrumented tests ---
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.truth)
}
