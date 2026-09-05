package com.mguuschedule.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime

@Immutable
data class Lesson(
    val id: String,
    val title: String,
    val type: String, // Лекция, Практика и т.д.
    val startTime: LocalTime,
    val endTime: LocalTime,
    val teacher: String,
    val room: String,
    val date: LocalDate,
    val number: Int
)
