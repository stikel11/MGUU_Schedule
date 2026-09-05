package com.mguuschedule.util

fun formatClassroom(room: String): String {
    val clean = room.trim()
    if (clean.isEmpty()) return "Ауд. -"
    val stripped = clean.replace(Regex("^(?i:(ауд\\.?\\s*)+)"), "").trim()
    return if (stripped.isNotEmpty()) "Ауд. $stripped" else "Ауд. $clean"
}
