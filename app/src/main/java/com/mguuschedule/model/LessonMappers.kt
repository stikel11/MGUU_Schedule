package com.mguuschedule.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER_H_MM = DateTimeFormatter.ofPattern("H:mm")

private fun parseTimeSafely(timeStr: String?): LocalTime? {
    if (timeStr.isNullOrBlank()) return null
    val clean = timeStr.trim().replace("\u00A0", "")
    if (clean.isEmpty() || clean == "—" || clean == "-") return null

    return runCatching {
        val padded = if (clean.length == 4 && clean[1] == ':') "0$clean" else clean
        LocalTime.parse(padded)
    }.getOrElse {
        runCatching {
            LocalTime.parse(clean, TIME_FORMATTER_H_MM)
        }.getOrElse {
            runCatching {
                val parts = clean.split(":")
                if (parts.size >= 2) {
                    val h = parts[0].trim().toInt()
                    val m = parts[1].trim().toInt()
                    LocalTime.of(h, m)
                } else null
            }.getOrNull()
        }
    }
}

private fun parseDateSafely(dateStr: String?): LocalDate? {
    if (dateStr.isNullOrBlank()) return null
    val clean = dateStr.trim()
    return runCatching {
        LocalDate.parse(clean)
    }.getOrElse {
        runCatching {
            val parts = clean.split(".", "-")
            if (parts.size == 3) {
                if (parts[0].length == 4) {
                    LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                } else {
                    LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                }
            } else null
        }.getOrNull()
    }
}

fun LessonEntity.toLesson(): Lesson? {
    return runCatching {
        val parsedStart = parseTimeSafely(this.startTime) ?: return null
        val parsedEnd = parseTimeSafely(this.endTime) ?: parsedStart.plusMinutes(90)
        val parsedDate = parseDateSafely(this.date) ?: return null

        Lesson(
            id = this.id.toString(),
            title = if (this.subject.isBlank()) "Занятие" else this.subject,
            type = this.type,
            startTime = parsedStart,
            endTime = parsedEnd,
            teacher = this.teacher,
            room = this.classroom,
            date = parsedDate,
            number = this.lessonNumber
        )
    }.getOrNull()
}

fun Lesson.toEntity(): LessonEntity {
    return LessonEntity(
        date = this.date.toString(),
        lessonNumber = this.number,
        startTime = this.startTime.toString(),
        endTime = this.endTime.toString(),
        subject = this.title,
        teacher = this.teacher,
        classroom = this.room,
        type = this.type
    )
}
