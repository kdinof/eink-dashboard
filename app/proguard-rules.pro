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
