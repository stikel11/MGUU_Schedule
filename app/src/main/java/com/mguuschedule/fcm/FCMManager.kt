package com.mguuschedule.fcm

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FCMManager {
    private const val TAG = "FCMManager"
    const val TOPIC_GENERAL = "general_announcements"

    /**
     * Initializes FCM subscriptions and token retrieval.
     * Fails gracefully if google-services.json is missing or Firebase is not initialized.
     */
    suspend fun initialize() {
        try {
            // Check if Firebase is initialized
            FirebaseApp.getInstance()
            
            // Subscribe to general announcements topic
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_GENERAL).await()
            Log.d(TAG, "Successfully subscribed to topic: $TOPIC_GENERAL")

            // Retrieve the current FCM token
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "Current FCM Token: $token")
            sendRegistrationToServer(token)
            
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Firebase not initialized. Ensure google-services.json is present and plugin is applied.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FCM or subscribe to topics", e)
        }
    }

    private fun sendRegistrationToServer(token: String) {
        // TODO: Implement sending the token to your explicitly configured backend server.
        // E.g., api.post("/registerToken", TokenRequest(token))
    }
}
