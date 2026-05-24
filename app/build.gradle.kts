import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    namespace   = "com.filestech.appmanager"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.filestech.appmanager"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 11
        versionName   = "0.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Strip locales other than en/fr from the resource bundle.
        // resourceConfigurations is deprecated in AGP 8.13+ (replaced by
        // androidResources.localeFilters) but is still supported on AGP 8.7.
        // To realign with the Files Tech portfolio, bump AGP 8.13.2 in Phase VIII.
        @Suppress("DEPRECATION")
        resourceConfigurations += listOf("en", "fr")

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

    kotlinOptions {
        jvmTarget = "17"
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
            isUniversalApk = false
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

// ---------------------------------------------------------------------------
// Room schema export — the dedicated plugin owns room.schemaLocation; do NOT
// also pass it through `ksp { arg(...) }` (would trigger a conflict error).
// Schemas land in <project>/schemas/<db-class-fqcn>/<version>.json and are
// checked into git so MigrationTest can replay every historical schema.
// ---------------------------------------------------------------------------
room {
    schemaDirectory("$projectDir/../schemas")
}

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

    // --- Unit tests ---
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
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
    androidTestImplementation(libs.junit.jupiter.api)
    androidTestImplementation(libs.truth)
}
