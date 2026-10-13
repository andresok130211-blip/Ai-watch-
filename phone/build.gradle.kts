plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Общий ключ подписи для телефона и часов: Data Layer требует одинаковый пакет и подпись.
// Если ключа нет (локальная сборка), используется debug-подпись.
val sharedKeystore = rootProject.file("watchai.keystore")
val keystorePassword: String = System.getenv("KEYSTORE_PASSWORD") ?: "watchai-local"

android {
    namespace = "com.watchai.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.watchai.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1"

        // llama-server собран только под 64-битный ARM
        ndk { abiFilters += "arm64-v8a" }
    }

    packaging {
        // llama-server лежит в APK как libllamaserver.so и запускается как процесс,
        // поэтому библиотеки должны распаковываться на диск
        jniLibs { useLegacyPackaging = true }
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
