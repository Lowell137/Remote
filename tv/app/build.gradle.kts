plugins { id("com.android.application") }

android {
    namespace = "com.lowell.tvremote"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.lowell.tvremote"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("lowell") {
            storeFile = file("../tv.jks")
            storePassword = "tvapk-local"
            keyAlias = "tv"
            keyPassword = "tvapk-local"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("lowell") }
        release { signingConfig = signingConfigs.getByName("lowell"); isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
