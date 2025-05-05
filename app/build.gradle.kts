plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // ML Kit now ships as an AAR, no extra plugin needed
}

android {
    namespace = "com.application.ocr"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.application.ocr"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        viewBinding = true      // required for all the *Binding classes
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.github.yalantis:ucrop:2.2.8")   // <- new

    // CameraX
    implementation("androidx.camera:camera-core:1.4.0-beta01")
    implementation("androidx.camera:camera-camera2:1.4.0-beta01")
    implementation("androidx.camera:camera-lifecycle:1.4.0-beta01")
    implementation("androidx.camera:camera-view:1.4.0-beta01")

    // ML Kit on‑device text recogniser (Latin)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Coil for image loading (or Glide if you prefer)
    implementation("io.coil-kt:coil:2.5.0")

    // Lifecycle + coroutines (optional but handy)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // CardView (for the list items)
    implementation("androidx.cardview:cardview:1.0.0")
}
