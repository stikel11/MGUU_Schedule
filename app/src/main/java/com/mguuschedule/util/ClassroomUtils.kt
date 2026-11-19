package com.mguuschedule.util

private val ROOM_PREFIX_REGEX = Regex("^(?i)(ауд\\.?\\s*)+")

fun formatRoomBadge(rawRoom: String, compact: Boolean = true): String {
    val clean = rawRoom.replace(ROOM_PREFIX_REGEX, "").trim()
    
    if (clean.contains("выезд", ignoreCase = true)) {
        return if (compact) "Выезд" else "Выездное занятие"
    }
    
    val lower = clean.lowercase()
    if (lower.contains("дистант") || lower.contains("онлайн") || lower.contains("сдо") || 
        lower.contains("зал") || lower.contains("библиотека") || lower.contains("кафедра")) {
        return clean
    }
    
    return if (clean.isNotEmpty()) "Ауд. $clean" else "Аудитория не указана"
}

fun formatClassroom(room: String): String = formatRoomBadge(room, compact = true)
