package com.mguuschedule.util

fun formatRoomBadge(rawRoom: String, compact: Boolean = true): String {
    val clean = rawRoom.replace(Regex("^(?i)(ауд\\.?\\s*)+"), "").trim()
    
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
