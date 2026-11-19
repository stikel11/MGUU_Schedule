package com.mguuschedule.model

/**
 * Авторизованная сессия Moodle Web Services (online.mguu.ru).
 */
data class MoodleSession(
    val token: String,
    val privateToken: String = "",
    val userId: Long,
    val fullName: String,
    val username: String,
    val siteName: String = "СДО МГУУ",
    val avatarUrl: String = "",
    val baseUrl: String = "https://online.mguu.ru"
)

/**
 * Учебный курс студента в СДО Moodle.
 */
data class MoodleCourse(
    val id: Long,
    val fullName: String,
    val shortName: String,
    val summary: String = "",
    val progress: Float? = null,
    val completed: Boolean = false,
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val isFavourite: Boolean = false
)

/**
 * Раздел (тема / неделя) внутри курса Moodle.
 */
data class MoodleCourseSection(
    val id: Long,
    val name: String,
    val summary: String = "",
    val modules: List<MoodleModule> = emptyList()
)

/**
 * Элемент курса Moodle (задание, тест, файл, ссылка, форум, папка и т. д.).
 */
data class MoodleModule(
    val id: Long,
    val name: String,
    val modName: String,
    val modPlural: String = "",
    val url: String = "",
    val description: String = "",
    val contents: List<MoodleFileContent> = emptyList(),
    val completed: Boolean? = null
)

/**
 * Файл или вложение внутри элемента курса Moodle.
 */
data class MoodleFileContent(
    val fileName: String,
    val fileUrl: String,
    val fileSize: Long = 0L,
    val mimeType: String = "",
    val timeModified: Long = 0L
)

/**
 * Предстоящее или просроченное задание / событие календаря в СДО Moodle.
 */
data class MoodleDeadline(
    val id: Long,
    val name: String,
    val courseName: String,
    val courseId: Long,
    val dueDateEpochSec: Long,
    val url: String = "",
    val moduleName: String = "assign",
    val description: String = "",
    val overdue: Boolean = false
)

/**
 * Оценка по элементу курса в журнале Moodle.
 */
data class MoodleGradeItem(
    val id: Long,
    val itemName: String,
    val gradeFormatted: String,
    val percentageFormatted: String = "",
    val rangeFormatted: String = "",
    val feedback: String = ""
)
