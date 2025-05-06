plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Add Firebase and Google Services plugins
    id("com.google.gms.google-services")
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
        viewBinding = true      // required for all the *Binding classes
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
    implementation("com.github.yalantis:ucrop:2.2.8")

    // CameraX
    implementation("androidx.camera:camera-core:1.4.0-beta01")
    implementation("androidx.camera:camera-camera2:1.4.0-beta01")
    implementation("androidx.camera:camera-lifecycle:1.4.0-beta01")
    implementation("androidx.camera:camera-view:1.4.0-beta01")

    // ML Kit on-device text recogniser (Latin)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Coil for image loading
    implementation("io.coil-kt:coil:2.5.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    // Lifecycle + coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation(libs.tess.two)   // Tesseract port

    // CardView
    implementation("androidx.cardview:cardview:1.0.0")
}