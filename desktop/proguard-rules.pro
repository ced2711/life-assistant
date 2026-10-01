# Release shrinking for the Windows app. Obfuscation stays off so stack traces remain readable.

# JNA (Windows DPAPI) looks classes, fields and methods up by name.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# kotlinx.serialization: keep generated serializers of the app's @Serializable classes.
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class com.ced2711.lifetracker.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.ced2711.lifetracker.**$$serializer { *; }

# OkHttp and Okio reference optional TLS providers that are not bundled.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room's common annotations are compiled into shared entities but unused at runtime.
-dontwarn androidx.room.**
-dontwarn okhttp3.internal.graal.**
-dontwarn org.graalvm.**
-dontwarn com.oracle.svm.**

# ProGuard's return-type specialization broke Okio (VerifyError in Okio.buffer), which made every
# cloud request fail with "Could not reach GitHub". Keep the network libraries as they are, and
# keep that optimization off everywhere.
-keep class okio.** { *; }
-keep class okhttp3.** { *; }
-optimizations !method/specialization/returntype,!method/specialization/parametertype
