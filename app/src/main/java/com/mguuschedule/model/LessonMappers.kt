package com.mguuschedule.model

import java.time.LocalDate
import java.time.LocalTime

fun LessonEntity.toLesson(): Lesson {
    return Lesson(
        id = this.id.toString(),
        title = this.subject,
        type = this.type,
        startTime = LocalTime.parse(this.startTime),
        endTime = if (this.endTime.isNotEmpty()) LocalTime.parse(this.endTime) else LocalTime.parse(this.startTime).plusMinutes(90),
        teacher = this.teacher,
        room = this.classroom,
        date = LocalDate.parse(this.date),
        number = this.lessonNumber
    )
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
