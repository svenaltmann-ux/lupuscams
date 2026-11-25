plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.example.lupuscams"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.example.lupuscams"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    // Feste Signatur, damit spätere Versionen als Update über die installierte App gehen.
    signingConfigs {
        create("fixed") {
            storeFile = file("lupuscams.keystore")
            storeType = "pkcs12"
            storePassword = "lupuscams"
            keyAlias = "lupuscams"
            keyPassword = "lupuscams"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
}
