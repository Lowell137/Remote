plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lowell.tvkumanda"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lowell.tvkumanda"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("lowell") {
            storeFile = file("../../tv/tv.jks")
            storePassword = "tvapk-local"
            keyAlias = "tv"
            keyPassword = "tvapk-local"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("lowell") }
        release { signingConfig = signingConfigs.getByName("lowell"); isMinifyEnabled = false }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("io.github.kyant0:backdrop:2.0.1")
}
