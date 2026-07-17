// Root build script. Plugins are declared here with `apply false` and applied
// per-module. Keep this file free of module-specific configuration.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
