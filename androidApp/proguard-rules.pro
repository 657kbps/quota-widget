# =============================================================================
# App ProGuard / R8 rules
# Based on library consumer rules + AGP 9 (R8 full mode) workarounds.
# Libraries already ship consumer rules; this file only covers app entry points
# and known R8 full-mode gaps (WorkManager Room database constructors).
# =============================================================================

-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exception

# -----------------------------------------------------------------------------
# kotlinx.serialization
# Official: library ships rules/common.pro; no extra keep needed unless models
# use named companion objects. Ours use default companions only.
# https://github.com/Kotlin/kotlinx.serialization#android
# -----------------------------------------------------------------------------
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# -----------------------------------------------------------------------------
# Glance AppWidget
# From glance-appwidget-1.1.1/proguard.txt
# -----------------------------------------------------------------------------
-keepclassmembers class * extends androidx.glance.appwidget.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep public class * extends androidx.glance.appwidget.action.ActionCallback {
    <init>();
}

# -----------------------------------------------------------------------------
# WorkManager
# From work-runtime-2.10.3/proguard.txt, plus AGP 9 / R8 full-mode keep for
# Room-backed WorkDatabase_Impl no-arg ctor (reflection via Class.newInstance).
# https://issuetracker.google.com/issues/243257364
# -----------------------------------------------------------------------------
-keepnames class * extends androidx.work.ListenableWorker
-keepclassmembers public class * extends androidx.work.ListenableWorker {
    public <init>(...);
}
-keep class androidx.work.WorkerParameters
-keepnames class * extends androidx.work.InputMerger
-keepclassmembers class * extends androidx.work.InputMerger {
    public <init>();
}
-keep class androidx.work.impl.WorkDatabase {
    <init>();
}
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}
-keep class androidx.work.** {
    <init>(...);
}

# -----------------------------------------------------------------------------
# Room (pulled in by WorkManager)
# From room-runtime-2.6.1/proguard.txt, with explicit <init>() for R8 full mode.
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-dontwarn androidx.room.paging.**
-dontwarn androidx.lifecycle.LiveData

# -----------------------------------------------------------------------------
# Tink (Android keyset / primitive registries use reflection)
# https://developers.google.com/tink/faq/which_languages_are_supported
# -----------------------------------------------------------------------------
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# -----------------------------------------------------------------------------
# Ktor / OkHttp (optional platform APIs)
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
