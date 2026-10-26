package com.mguuschedule.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mguuschedule.MainActivity
import com.mguuschedule.R
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.repository.NotificationEntity
import com.mguuschedule.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "From: ${remoteMessage.from}")

        val dataTitle = remoteMessage.data["title"] ?: remoteMessage.data["gcm.notification.title"]
        val dataBody = remoteMessage.data["body"] ?: remoteMessage.data["message"] ?: remoteMessage.data["gcm.notification.body"]
        val route = remoteMessage.data["route"]

        val notifTitle = remoteMessage.notification?.title
        val notifBody = remoteMessage.notification?.body

        val finalTitle = notifTitle ?: dataTitle ?: "Объявление"
        val finalBody = notifBody ?: dataBody ?: ""

        if (finalBody.isNotBlank()) {
            // Save to Notification History DB
            saveToHistory(
                title = finalTitle,
                body = finalBody,
                type = "GENERAL",
                msgId = remoteMessage.messageId
            )

            // If the app is in foreground, FCM calls this method and we manually show the notification.
            // If the app is in background and payload has "notification", FCM shows it automatically,
            // but we still save it to DB here!
            showNotification(
                title = finalTitle,
                body = finalBody,
                targetRoute = route
            )
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FCM", "Refreshed token: $token")
        // If we had a backend, we'd send the token here.
        // sendRegistrationToServer(token)
    }

    private fun showNotification(title: String, body: String, targetRoute: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (!targetRoute.isNullOrBlank()) {
                // Ensure only allowed routes are passed to prevent arbitrary deep links
                val allowedRoutes = listOf("schedule", "rating", "settings", "sections", "notification_history")
                if (allowedRoutes.contains(targetRoute.split("/")[0])) {
                    putExtra("navigate_to_route", targetRoute)
                }
            } else {
                putExtra("navigate_to_route", "notification_history")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = NotificationHelper.CHANNEL_GENERAL_ANNOUNCEMENTS
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // using system icon as a fallback
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun saveToHistory(title: String, body: String, type: String = "GENERAL", msgId: String?) {
        val prefs = getSharedPreferences("processed_fcm_ids", Context.MODE_PRIVATE)
        if (msgId != null) {
            if (prefs.getBoolean(msgId, false)) return // Уже сохранено
            prefs.edit().putBoolean(msgId, true).apply()
        }

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(this@MyFirebaseMessagingService)
            db.notificationDao().insertNotification(
                NotificationEntity(
                    title = title,
                    message = body,
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    isRead = false
                )
            )
        }
    }
}
