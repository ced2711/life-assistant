# Release shrinking for the Windows app. Obfuscation stays off so stack traces remain readable.

# JNA (Windows DPAPI) looks classes, fields and methods up by name. Keep JNA itself and the
# structures and library interfaces it maps, but let the unused Windows API mappings go.
-keep class com.sun.jna.* { *; }
-keep class com.sun.jna.ptr.** { *; }
-keep class com.sun.jna.internal.** { *; }
-keep class com.sun.jna.win32.** { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep interface * extends com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class * implements com.sun.jna.NativeMapped { *; }
-keep class com.sun.jna.platform.win32.Crypt32Util { *; }
-dontwarn com.sun.jna.**

# kotlinx.serialization: keep generated serializers of the app's @Serializable classes.
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class com.ced2711.lifetracker.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.ced2711.lifetracker.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.ced2711.lifetracker.** { *; }

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

# Only libraries get short names; the app's own classes keep theirs, so its stack traces stay
# readable and nothing of ours depends on a renamed class.
-keepnames class com.ced2711.lifetracker.** { *; }
-printmapping build/compose/proguard-mapping.txt
