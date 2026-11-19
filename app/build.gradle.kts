plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "il.co.liraz.callrecorder"
    compileSdk = 35

    defaultConfig {
        applicationId = "il.co.liraz.callrecorder"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
