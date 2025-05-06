package com.application.ocr

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestore

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.firestore.firestoreSettings =
            FirebaseFirestoreSettings.Builder()
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
    }
}
