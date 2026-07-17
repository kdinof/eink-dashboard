# ProGuard/R8 rules for release builds.
# The foundation ships no obfuscation-sensitive code yet; module-specific keep
# rules (Retrofit models, Room entities, kotlinx.serialization) are added by the
# owning task (T03/T04/T05) when real integrations land.

# kotlinx.serialization — keep @Serializable metadata (safe default, no models yet).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# --- T04 Todoist ---------------------------------------------------------------
# kotlinx.serialization DTOs: keep the classes and their generated serializers so
# R8 (T09) can't strip fields the Todoist v1 JSON maps onto.
-keepclassmembers @kotlinx.serialization.Serializable class com.eink.dashboard.modules.todoist.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.eink.dashboard.modules.todoist.data.**$$serializer { *; }

# Retrofit service interfaces rely on generic signatures / annotations at runtime.
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation interface com.eink.dashboard.modules.todoist.data.TodoistService

# Room entities are accessed reflectively by generated code — keep their fields.
-keep class com.eink.dashboard.modules.todoist.data.room.** { *; }

# --- Local Ktor/Netty settings server -----------------------------------------
# These are optional Netty integrations (native TLS, alternate loggers, Jetty NPN
# and BlockHound). The local server uses plain HTTP, JDK SSL is not selected, and
# slf4j-nop is the only logging backend, so none are present in the Android APK.
-dontwarn io.netty.internal.tcnative.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn reactor.blockhound.integration.BlockHoundIntegration

# Preserve serializers for the versioned web API DTOs.
-keepclassmembers @kotlinx.serialization.Serializable class com.eink.dashboard.remote.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.eink.dashboard.remote.**$$serializer { *; }

-keepclassmembers @kotlinx.serialization.Serializable class com.eink.dashboard.modules.calendar.google.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.eink.dashboard.modules.calendar.google.**$$serializer { *; }
