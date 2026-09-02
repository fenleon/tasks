plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lightphone.tasks.server"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
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

dependencies {
    // SDK modules come from the included ../light-sdk build (see settings.gradle.kts).
    // sdk:server = LightSdkServer + LightSdkService (the binder). Since the
    // 2026-08-25 single-module merge this library ships INSIDE the tool APK,
    // which hosts the service and binds to itself (lighttool.toml serverPackage).
    implementation(libs.sdk.server) {
        exclude(group = "com.google.mlkit")
        exclude(group = "androidx.camera")
    }
    implementation(libs.kotlinx.coroutines)
    // kotlinx-serialization runtime for the in-process tasks.json store (the
    // data layer lives here so the merged server can read the same JSON);
    // lightJson itself comes from sdk-shared.
    implementation(libs.kotlinx.serialization.json)
    // Unit tests for the JSON store (kotlin-test + JUnit; a plain android
    // library module, so the plugin's test-source restrictions don't apply).
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}