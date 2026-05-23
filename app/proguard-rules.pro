# ============================================================
# App Manager Tech — ProGuard / R8 rules
# ============================================================

# --- Kotlin ---
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }

# --- Hilt ---
# The hilt-android AAR ships consumer ProGuard rules that already keep all
# generated factories and entry points. We only need to keep @HiltViewModel
# classes themselves; the redundant `<init>(...)` keep was removed in
# Phase VIII (M-5 audit) — gains a handful of bytes in the final DEX.
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# --- Hilt-Work ---
# `hilt-work-compiler` generates `<WorkerClass>_AssistedFactory` per @HiltWorker.
# The hilt-work AAR usually keeps them, but we add an explicit rule for
# defence-in-depth on R8 full-mode (Phase VIII M-5 audit).
-keep @androidx.hilt.work.HiltWorker class * { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- DataStore ---
-keep class androidx.datastore.** { *; }

# --- WorkManager ---
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Timber ---
-dontwarn org.jetbrains.annotations.**

# --- App Manager Tech domain models ---
# Keep data classes used in Room entities and DataStore serialisation.
-keepclassmembers class com.filestech.appmanager.domain.model.** { *; }
-keepclassmembers class com.filestech.appmanager.data.local.datastore.** { *; }

# --- Debugging: keep source file names in stack traces (release) ---
-renamesourcefileattribute SourceFile
