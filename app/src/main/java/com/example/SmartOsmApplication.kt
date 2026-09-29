package com.example

import android.app.Application
import android.util.Log
import com.example.data.AppContainer
import com.example.data.firestore.FirestoreManager
import com.google.firebase.FirebaseApp

class SmartOsmApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            FirestoreManager.initialize(this)
            
            Log.i("SmartOsmApp", "Firebase and Firestore successfully initialized")
        } catch (e: Exception) {
            Log.w("SmartOsmApp", "Firebase initialization deferred: ${e.message}")
        }
    }
}