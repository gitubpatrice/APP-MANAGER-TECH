// Root build.gradle.kts — App Manager Tech
// No version numbers here: all versions live in gradle/libs.versions.toml.

// The Kotlin compiler version, as a real cap. With AGP 9 the Kotlin Gradle Plugin (KGP) is a
// dependency of AGP itself, and Gradle keeps the HIGHEST version requested: the `kotlin` entry of the
// catalog only reached the compiler through the Compose plugin (AGP 9.4.1 asks for KGP 2.2.10, Compose
// for 2.4.10 - measured with `buildEnvironment`). A future AGP asking for 2.4.20+ would have raised
// the compiler silently past what CodeQL accepts. `strictly` holds it at the catalog's version
// whatever is requested: Gradle downgrades every higher request to it (measured: a strict 2.4.0
// pulled both AGP's and Compose's requests down to 2.4.0). Raising Kotlin is therefore always a
// deliberate edit of the catalog - and an AGP that needs a newer KGP breaks the build loudly rather
// than changing the compiler behind CodeQL's back.
buildscript {
    dependencies {
        constraints {
            classpath("org.jetbrains.kotlin:kotlin-gradle-plugin") {
                version { strictly(libs.versions.kotlin.get()) }
                because("CodeQL 2.27 refuses Kotlin 2.4.20+; the compiler must be the catalog's version")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.hilt)                apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.room)                apply false
    alias(libs.plugins.detekt)
}

// detekt, blocking: config/detekt/detekt.yml states which rules describe a behaviour (active) and which
// a layout taste this code does not follow (off, with the reason). There is no baseline.
allprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    detekt {
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        // androidTest added explicitly: the plugin's default sources are src/{main,test}, which would
        // leave the Room migration tests outside any static analysis.
        source.setFrom(files("src/main/java", "src/test/java", "src/androidTest/java"))
        autoCorrect = false
        parallel = true
    }

    dependencies {
        add("detektPlugins", rootProject.libs.detekt.formatting)
    }
}
