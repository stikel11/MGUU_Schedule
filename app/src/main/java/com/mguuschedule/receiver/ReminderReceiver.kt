package com.mguuschedule.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mguuschedule.util.NotificationHelper

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Скоро пара"
        val time = intent.getStringExtra("time") ?: ""
        val room = intent.getStringExtra("room") ?: ""
        val id = intent.getIntExtra("id", 0)

        val message = "Начало в $time, ауд. $room"
        
        NotificationHelper.showNotification(
            context,
            NotificationHelper.CHANNEL_CLASS_REMINDERS,
            title,
            message,
            id
        )
    }
}
