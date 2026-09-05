package com.mguuschedule.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mguuschedule.MainActivity

object ShortcutHelper {
    fun setupShortcuts(context: Context) {
        val nextLessonIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.mguuschedule.ACTION_NEXT_LESSON"
        }
        val nextLessonShortcut = ShortcutInfoCompat.Builder(context, "next_lesson")
            .setShortLabel("Следующая пара")
            .setLongLabel("Открыть следующую пару")
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_today))
            .setIntent(nextLessonIntent)
            .build()

        val tomorrowIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.mguuschedule.ACTION_TOMORROW"
        }
        val tomorrowShortcut = ShortcutInfoCompat.Builder(context, "tomorrow_schedule")
            .setShortLabel("На завтра")
            .setLongLabel("Расписание на завтра")
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_day))
            .setIntent(tomorrowIntent)
            .build()

        ShortcutManagerCompat.addDynamicShortcuts(context, listOf(nextLessonShortcut, tomorrowShortcut))
    }
}
