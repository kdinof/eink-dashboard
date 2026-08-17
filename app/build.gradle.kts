import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.eink.dashboard"
    // API 34 toolchain; the app still targets/runs on the Meebook M103 (API 30).
    compileSdk = 34

    defaultConfig {
        applicationId = "com.eink.dashboard"
        // Confirmed by T00 device audit: Meebook M103 runs Android 11 / API 30.
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // The endpoint is public configuration, not a credential. CI can inject
        // it through -P or the environment; local builds persist it in the
        // git-ignored local.properties so a routine rebuild cannot silently
        // replace a working APK with one that cannot refresh expired tokens.
        val googleBrokerUrl = providers.gradleProperty("EINK_GOOGLE_BROKER_URL").orNull
            ?: providers.environmentVariable("EINK_GOOGLE_BROKER_URL").orNull
            ?: localProperties.getProperty("EINK_GOOGLE_BROKER_URL")
            ?: ""
        buildConfigField("String", "GOOGLE_BROKER_URL", "\"$googleBrokerUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // T04: export the Room schema JSON so future schema changes ship an explicit
        // migration. Committed under app/schemas/.
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }

        // App code is pure Kotlin/JVM, but some AndroidX libraries (e.g.
        // DataStore) bundle a small native .so for every ABI. The device is
        // arm64-v8a (T00), so we ship only that ABI — dropping x86/x86_64/
        // armeabi-v7a keeps the APK lean and matched to the target.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        // Debug: default debug signing config (local, not a real secret).
        getByName("debug") {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // Release: no real signing secrets committed. A CI/local signing config
        // is injected later (T09). Until then `assembleRelease` produces an
        // unsigned APK; smoke installs use the debug variant.
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM-managed versions)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Room (local cache) — wired for T04/T05, no schema shipped yet
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // DataStore (settings)
    implementation(libs.androidx.datastore.preferences)

    // Network — wired for T04/T05, no live calls in the foundation
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Foreground-only local configuration server + on-device pairing QR.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.zxing.core)
    implementation(libs.slf4j.nop)

    // --- Unit test stack ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)

    // --- Instrumented test stack ---
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
