plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Тот же ключ подписи и тот же applicationId, что у телефонного приложения.
val sharedKeystore = rootProject.file("watchai.keystore")
val keystorePassword: String = System.getenv("KEYSTORE_PASSWORD") ?: "watchai-local"

android {
    namespace = "com.watchai.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.watchai.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("shared") {
            if (sharedKeystore.exists()) {
                storeFile = sharedKeystore
                storePassword = keystorePassword
                keyAlias = "watchai"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (sharedKeystore.exists()) {
                signingConfigs.getByName("shared")
            } else {
                signingConfigs.getByName("debug")
            }
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
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
