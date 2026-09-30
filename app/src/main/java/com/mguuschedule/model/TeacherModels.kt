package com.mguuschedule.model

data class CampusTeacher(
    val name: String,
    val university: String,
    val department: String,
    val rating: Float,
    val reviewCount: Int,
    val criteria: List<CampusCriteria>,
    val tags: List<String>,
    val reviews: List<CampusReview>
)

data class CampusCriteria(
    val name: String,
    val value: Float
)

data class CampusReview(
    val author: String,
    val date: String,
    val rating: Float,
    val text: String,
    val tags: List<String>
)
