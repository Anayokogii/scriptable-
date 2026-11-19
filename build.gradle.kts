plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.scriptwidgets"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scriptwidgets"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Pure-Java JS engine; run in interpreter mode (optimizationLevel = -1) on Android.
    implementation("org.mozilla:rhino:1.7.15")
}
