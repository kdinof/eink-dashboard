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
