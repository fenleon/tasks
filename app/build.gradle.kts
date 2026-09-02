plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

android {
    compileSdk = 36

    signingConfigs {
        // Workspace dev signing (same key as the SDK tools/emulator).
        create("lightsdkDev") {
            storeFile = file("../../light-sdk/sdk/keys/lightsdk-dev.jks")
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
        }
    }

    defaultConfig {
        minSdk = 34
        targetSdk = 36

        // Consumed by the plugin's generated manifest (SDK_VERSION metadata).
        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
        getByName("release") {
            // R8 dead-code elimination + resource shrinking (the audiobooks
            // methodology — see APK-SHRINKING.md); the SDK's consumer rules
            // keep the generated registry + entry points.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// The QR scanner (zxing-cpp, added to sdk:ui for code scanning) requires
// compileSdk 37; tasks never scans codes. Per-dependency excludes don't prune
// it through the composite-build project substitution, so drop the group for
// the whole configuration.
configurations.configureEach {
    exclude(group = "com.github.markusfisch")
}

dependencies {
    // SDK modules come from the included ../light-sdk build (see settings.gradle.kts).
    // sdk:client pulls sdk:ui, which bundles an ML Kit QR scanner + CameraX for the
    // SDK's authenticator example. Tasks never touches it — exclude the groups
    // so their ~20 MB of native libs don't ship (R8 removes the scanner code).
    implementation(libs.sdk.client) {
        exclude(group = "com.google.mlkit")
        exclude(group = "androidx.camera")
    }
    implementation(libs.kotlinx.coroutines)
    // kotlinx-serialization runtime for the in-process tasks.json store
    // (lightJson comes from sdk-shared; the runtime dep mirrors passes).
    implementation(libs.kotlinx.serialization.json)
    // The merged :server library (single-module build): its manifest
    // contributes the SDK server wiring (ServerBootstrapProvider), its
    // LightSdkService comes from sdk:server, and the tool binds to itself
    // (lighttool.toml serverPackage = own id).
    implementation(project(":server"))
    testImplementation(libs.kotlin.test)
}