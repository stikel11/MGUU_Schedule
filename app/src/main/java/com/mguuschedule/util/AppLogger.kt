package com.mguuschedule.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object AppLogger {
    data class LogEntry(val time: String, val level: String, val message: String)
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$msg \n${throwable.stackTraceToString()}" else msg
        add("ERROR", "[$tag] $fullMsg")
        android.util.Log.e(tag, fullMsg)
    }

    fun d(tag: String, msg: String) {
        add("DEBUG", "[$tag] $msg")
        android.util.Log.d(tag, msg)
    }

    private fun add(level: String, msg: String) {
        val time = LocalTime.now().format(timeFormatter)
        _logs.update { (listOf(LogEntry(time, level, msg)) + it).take(100) }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
