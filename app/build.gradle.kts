plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.newlang.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.newlang.aikeyboard"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "1.7"
    }
    signingConfigs {
        create("shared") {
            storeFile = file("signing/keyboard.jks")
            storePassword = "android"
            keyAlias = "keyboard"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
