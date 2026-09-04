plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.meshcall"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.meshcall"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.image.cropper)
    implementation(libs.mapsforge.core)
    implementation(libs.mapsforge.map)
    implementation(libs.mapsforge.map.reader)
    implementation(libs.mapsforge.themes)
    implementation(libs.mapsforge.map.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}