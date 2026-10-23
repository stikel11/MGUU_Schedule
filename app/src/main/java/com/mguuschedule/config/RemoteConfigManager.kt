package com.mguuschedule.config

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class EmergencyAnnouncement(
    val enabled: Boolean,
    val title: String,
    val message: String,
    val buttonText: String,
    val url: String,
    val id: String,
    val dismissible: Boolean
)

object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"
    private const val PREFS_NAME = "remote_config_prefs"

    private val _announcement = MutableStateFlow<EmergencyAnnouncement?>(null)
    val announcement: StateFlow<EmergencyAnnouncement?> = _announcement.asStateFlow()

    suspend fun initialize(context: Context) {
        try {
            FirebaseApp.getInstance() // Throws if not initialized

            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1 hour for production
                .build()
            
            remoteConfig.setConfigSettingsAsync(configSettings).await()

            // Safe defaults
            remoteConfig.setDefaultsAsync(
                mapOf(
                    "emergency_announcement_enabled" to false,
                    "emergency_announcement_title" to "Важное объявление",
                    "emergency_announcement_message" to "",
                    "emergency_announcement_button_text" to "Понятно",
                    "emergency_announcement_url" to "",
                    "emergency_announcement_id" to "",
                    "emergency_announcement_dismissible" to true
                )
            ).await()

            // Fetch and activate
            remoteConfig.fetchAndActivate().await()

            val enabled = remoteConfig.getBoolean("emergency_announcement_enabled")
            val id = remoteConfig.getString("emergency_announcement_id")

            if (enabled && id.isNotBlank()) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val isAcknowledged = prefs.getBoolean("acknowledged_announcement_$id", false)

                if (!isAcknowledged) {
                    _announcement.value = EmergencyAnnouncement(
                        enabled = true,
                        title = remoteConfig.getString("emergency_announcement_title"),
                        message = remoteConfig.getString("emergency_announcement_message"),
                        buttonText = remoteConfig.getString("emergency_announcement_button_text"),
                        url = remoteConfig.getString("emergency_announcement_url"),
                        id = id,
                        dismissible = remoteConfig.getBoolean("emergency_announcement_dismissible")
                    )
                }
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Firebase not initialized. Ensure google-services.json is present.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Remote Config", e)
        }
    }

    fun acknowledgeAnnouncement(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("acknowledged_announcement_$id", true).apply()
        _announcement.value = null // Hide it
    }
}
