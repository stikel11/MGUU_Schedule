package com.mguuschedule.ui.components

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mguuschedule.model.Lesson
import com.mguuschedule.ui.screens.InteractiveFloorMap
import com.mguuschedule.ui.screens.SearchViewModel
import com.mguuschedule.ui.screens.SearchViewModelFactory
import com.mguuschedule.util.SearchResultItem
import com.mguuschedule.util.formatClassroom
import com.mguuschedule.util.rememberHapticFeedback
import java.time.format.TextStyle
import java.util.Locale

private val localeRu = Locale("ru")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlay(
    onDismiss: () -> Unit,
    allLessons: List<Lesson>, // Сохраняем обратную совместимость для параметров
    onLessonClick: (Lesson) -> Unit,
    onTeacherClick: (String) -> Unit = {},
    viewModel: SearchViewModel = viewModel(
        factory = SearchViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val haptic = rememberHapticFeedback()
    val uiState by viewModel.uiState.collectAsState()
    
    val query = uiState.query
    val selectedFilter = uiState.selectedFilter
    val results = uiState.results

    val filters = listOf("Все", "Предмет", "Преподаватель", "Аудитория")
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val handleBack = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    BackHandler(onBack = handleBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 12.dp)
                .clipToBounds()
        ) {
            // Верхнее поле поиска
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        haptic.lightTick()
                        handleBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    
                    TextField(
                        value = query,
                        onValueChange = { viewModel.onQueryChanged(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Предмет, преподоб., дата, 425, КТ...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        )
                    )
                    
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            haptic.lightTick()
                            viewModel.onQueryChanged("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                }
            }

            // Фильтры
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { tag ->
                    val selected = (selectedFilter == tag)
                    val label = when (tag) {
                        "Преподаватель" -> "Препод"
                        "Аудитория" -> "Ауд."
                        else -> tag
                    }
                    Surface(
                        onClick = {
                            haptic.selection()
                            viewModel.onFilterSelected(tag)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Списочный результат по типам
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (query.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Введите предмет, ФИО, номер аудитории, дату или 'КТ'...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (results.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Ничего не найдено",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(
                        items = results,
                        key = { item ->
                            when (item) {
                                is SearchResultItem.TeacherCard -> "teacher_${item.teacherName}"
                                is SearchResultItem.RoomCard -> "room_${item.room}"
                                is SearchResultItem.DateHeader -> "date_${item.date}"
                                is SearchResultItem.ControlPointHeader -> "cp_header"
                                is SearchResultItem.ControlPointCard -> "cp_${item.subjectName}_${item.pointName}"
                                is SearchResultItem.SectionHeader -> "section_${item.title}"
                                is SearchResultItem.LessonCard -> "lesson_${item.lesson.id}_${item.lesson.date}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is SearchResultItem.TeacherCard -> {
                                SearchTeacherCard(
                                    teacherName = item.teacherName,
                                    rating = item.campusProfile?.rating,
                                    reviewCount = item.campusProfile?.reviewCount,
                                    onClick = { onTeacherClick(item.teacherName) }
                                )
                            }
                            is SearchResultItem.RoomCard -> {
                                SearchRoomCard(
                                    room = item.room,
                                    floorNumber = item.floorNumber,
                                    floorResId = item.floorImageResId
                                )
                            }
                            is SearchResultItem.DateHeader -> {
                                SearchDateHeader(
                                    dateText = item.dateText,
                                    dayOfWeekText = item.dayOfWeekText
                                )
                            }
                            is SearchResultItem.ControlPointHeader -> {
                                SearchSectionTitle(title = item.title)
                            }
                            is SearchResultItem.ControlPointCard -> {
                                SearchControlPointCard(item)
                            }
                            is SearchResultItem.SectionHeader -> {
                                SearchSectionTitle(title = item.title)
                            }
                            is SearchResultItem.LessonCard -> {
                                SearchItemCard(lesson = item.lesson, onClick = { onLessonClick(item.lesson) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTeacherCard(
    teacherName: String,
    rating: Float?,
    reviewCount: Int?,
    onClick: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = {
                haptic.click()
                onClick()
            }),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Преподаватель",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = teacherName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (rating != null && rating > 0f) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f", rating),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (reviewCount != null && reviewCount > 0) {
                            Text(
                                text = " · $reviewCount отзывов",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Профиль",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchRoomCard(
    room: String,
    floorNumber: String,
    floorResId: Int?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Аудитория $room",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (floorNumber.isNotBlank()) {
                Text(
                    text = "$floorNumber этаж",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (floorResId != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    InteractiveFloorMap(
                        imageResId = floorResId,
                        contentDescription = "Схема $floorNumber этажа",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun SearchDateHeader(dateText: String, dayOfWeekText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = dayOfWeekText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SearchSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun SearchControlPointCard(item: SearchResultItem.ControlPointCard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.subjectName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.pointName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.scoreText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun SearchItemCard(lesson: Lesson, onClick: () -> Unit) {
    val haptic = rememberHapticFeedback()
    val dateText = remember(lesson.date) {
        val dayName = lesson.date.dayOfWeek.getDisplayName(TextStyle.SHORT, localeRu)
            .replaceFirstChar { it.uppercase() }
        "$dayName, ${lesson.date.dayOfMonth} ${lesson.date.month.getDisplayName(TextStyle.SHORT, localeRu)}"
    }
    val roomFormatted = remember(lesson.room) {
        formatClassroom(lesson.room)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = {
                haptic.click()
                onClick()
            }),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = lesson.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (lesson.teacher.isNotBlank() && lesson.teacher != "—") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lesson.teacher,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchChip(text = dateText)
                SearchChip(text = lesson.type)
                SearchChip(text = roomFormatted, isPrimary = true)
            }
        }
    }
}

@Composable
fun SearchChip(text: String, isPrimary: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium
        )
    }
}
