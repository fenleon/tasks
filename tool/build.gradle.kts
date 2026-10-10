plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

android {
    compileSdk = rootProject.ext["compileSdk"] as Int

    signingConfigs {
        // Workspace dev signing (same key as the SDK tools/emulator). Inside
        // an SDK checkout (Light's Tool Library builder stages this tool/
        // module into the baked-in SDK repo) the keys live at ../sdk/keys.
        create("lightsdkDev") {
            storeFile = file(
                listOf("../../light-sdk/sdk/keys/lightsdk-dev.jks", "../sdk/keys/lightsdk-dev.jks")
                    .map(::file).first { it.exists() }
            )
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
        }
    }

    defaultConfig {
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int

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
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(rootProject.ext["jvmTarget"] as String))
    }
}

// Tasks never scans: drop the SDK's bundled QR scanner stack — the zxing-cpp
// engine (com.github.markusfisch) needs compileSdk 37, and ML Kit + CameraX
// carry ~20 MB of native libs. Per-dependency excludes don't prune through
// the composite-build project substitution, so drop the groups wholesale.
configurations.configureEach {
    exclude(group = "com.github.markusfisch")
    exclude(group = "com.google.mlkit")
    exclude(group = "androidx.camera")
}

// Inside an SDK checkout the SDK modules are sibling projects; in this
// workspace the included ../light-sdk build substitutes the module artifacts.
val inSdkRepo = file("../sdk").exists()

dependencies {
    implementation(
        if (inSdkRepo) project(":sdk:client")
        else "com.thelightphone:sdk-client"   // LightScreen, LightActivity
    )
    implementation(libs.kotlinx.coroutines)
    // kotlinx-serialization runtime for the in-process tasks.json store
    // (lightJson comes from sdk-shared; the runtime dep mirrors passes).
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}
