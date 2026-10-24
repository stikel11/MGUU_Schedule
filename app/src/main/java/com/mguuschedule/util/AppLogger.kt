package com.mguuschedule.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class SyncDiagnostics(
    val lastScheduleSource: String = "Неизвестно", // "СЕТЬ (api.lk-upm.ru)" или "Кэш Room"
    val lastScheduleSyncTime: String = "—",
    val apiScheduleVersion: String = "—",
    val apiScheduleUpdatedAt: String = "—",
    val apiScheduleStale: Boolean? = null,
    val lastScheduleCount: Int = 0,
    val lastRatingSource: String = "Неизвестно",
    val lastRatingSyncTime: String = "—",
    val apiRatingVersion: String = "—"
)

object AppLogger {
    data class LogEntry(val time: String, val level: String, val tag: String = "APP", val message: String)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _syncDiagnostics = MutableStateFlow(SyncDiagnostics())
    val syncDiagnostics: StateFlow<SyncDiagnostics> = _syncDiagnostics.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun updateDiagnostics(update: (SyncDiagnostics) -> SyncDiagnostics) {
        _syncDiagnostics.update(update)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$msg \n${throwable.stackTraceToString()}" else msg
        add("ERROR", tag, fullMsg)
        Log.e(tag, fullMsg)
    }

    fun d(tag: String, msg: String) {
        add("DEBUG", tag, msg)
        Log.d(tag, msg)
    }

    fun w(tag: String, msg: String) {
        add("WARN", tag, msg)
        Log.w(tag, msg)
    }

    private fun add(level: String, tag: String, msg: String) {
        val time = LocalTime.now().format(timeFormatter)
        _logs.update { (listOf(LogEntry(time, level, tag, msg)) + it).take(300) }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
