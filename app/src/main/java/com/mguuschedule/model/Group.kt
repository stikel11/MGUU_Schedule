package com.mguuschedule.model

import androidx.compose.runtime.Immutable

@Immutable
data class Group(
    val id: String,
    val name: String,
    val course: String = ""
)
