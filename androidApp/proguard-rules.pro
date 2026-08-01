# Keep serialization models used for DeepSeek API JSON.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exception
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Glance ActionCallback and WorkManager entry points.
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context,androidx.work.WorkerParameters); }

# Tink keyset / primitives accessed reflectively.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Ktor / OkHttp optional APIs.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
