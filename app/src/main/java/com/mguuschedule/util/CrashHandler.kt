package com.mguuschedule.util

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

object CrashHandler {
    private const val PREFS_NAME = "crash_prefs"
    private const val KEY_LAST_CRASH = "last_crash_trace"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            val stackTrace = stringWriter.toString()

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CRASH, "[${LocalDateTime.now()}] Thread: ${thread.name}\n$stackTrace")
                .commit()

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getLastCrash(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH, null)
    }

    fun clearLastCrash(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_LAST_CRASH).apply()
    }
}
