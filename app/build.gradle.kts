plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.dts88.openplayer"
    compileSdk = 36

    val customStore = System.getenv("LAB_KEYSTORE")
    val useCustomKey = !customStore.isNullOrBlank() && file(customStore).exists()
    signingConfigs {
        if (useCustomKey) {
            create("release") {
                storeFile = file(customStore!!)
                storePassword = System.getenv("LAB_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LAB_KEY_ALIAS")
                keyPassword = System.getenv("LAB_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.dts88.openplayer"
        minSdk = 28
        targetSdk = 36
        versionCode = 1010
        versionName = "1.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        debug {
            ndk { abiFilters += listOf("x86_64") }
        }
        release {
            signingConfig = if (useCustomKey) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.recyclerview)
    implementation(libs.ass.media)
}
