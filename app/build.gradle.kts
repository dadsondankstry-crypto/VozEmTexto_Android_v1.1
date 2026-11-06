plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vozemtexto.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vozemtexto.app"
        minSdk = 29 // Captura de áudio interno exige Android 10 ou superior.
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("com.alphacephei:vosk-android:0.3.47")
}
