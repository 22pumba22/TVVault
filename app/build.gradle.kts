plugins {
    id("com.android.application")
}

android {
    namespace = "com.alexcantini.tvvault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alexcantini.tvvault"
        minSdk = 28
        targetSdk = 35
        versionCode = 20
        versionName = "0.17.2"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
