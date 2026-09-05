package com.mguuschedule.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mguuschedule.model.Lesson
import com.mguuschedule.ui.components.WeekCalendar
import com.mguuschedule.util.HapticManager
import com.mguuschedule.util.formatClassroom
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class LessonStatus { PAST, CURRENT, UPCOMING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onLessonClick: (Lesson) -> Unit = {}
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val uiState = viewModel.uiState
    val isRefreshing = viewModel.isRefreshing
    val isOnline by viewModel.isOnline.collectAsState()
    val weatherData = viewModel.weatherData
    val hasChanges = viewModel.hasChangesInLastRefresh
    val context = LocalContext.current
    val hapticManager = remember { HapticManager.getInstance(context) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var showRefreshStatus by remember { mutableStateOf(false) }
    var refreshStatusText by remember { mutableStateOf("") }
    
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            refreshStatusText = "Синхронизация расписания..."
            showRefreshStatus = true
        } else if (uiState is ScheduleUiState.Success && uiState.lessons.isNotEmpty()) {
            hapticManager.success()
            refreshStatusText = if (hasChanges) "Найдено 1 изменение" else "Расписание актуально"
            delay(1500)
            showRefreshStatus = false
        } else {
            showRefreshStatus = false
        }
    }
    
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    val currentDate = remember { LocalDate.now() }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(60000)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 4.dp)
        ) {
            WeekCalendar(
                selectedDate = selectedDate,
                onDateSelected = { viewModel.onDateSelected(it) },
                weatherData = weatherData
            )

            // Expanding refresh status block
            AnimatedVisibility(
                visible = showRefreshStatus && !isRefreshing && uiState is ScheduleUiState.Success,
                enter = expandVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                Surface(
                    color = if (hasChanges) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasChanges) Icons.Default.AutoAwesome else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = refreshStatusText,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (hasChanges) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !isOnline,
                enter = expandVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                OfflineBanner()
            }

            val pullState = rememberPullToRefreshState()
            
            LaunchedEffect(pullState.distanceFraction) {
                if (pullState.distanceFraction >= 1f && !isRefreshing) {
                    hapticManager.lightTick()
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                state = pullState,
                onRefresh = { 
                    if (isOnline) {
                        viewModel.refreshSchedule() 
                    } else {
                        hapticManager.lightTick()
                        scope.launch {
                            snackbarHostState.showSnackbar("Нет подключения к интернету. Показана сохраненная копия")
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                indicator = {}
            ) {
                val lessons by viewModel.lessonsForSelectedDay.collectAsState()

                Crossfade(
                    targetState = selectedDate,
                    animationSpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "day_crossfade"
                ) { targetDate ->
                    val lessonsForTargetDate = if (targetDate == selectedDate) lessons else emptyList()

                    if (uiState is ScheduleUiState.Loading && !isRefreshing) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                strokeWidth = 4.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    } else if (uiState is ScheduleUiState.Error && lessonsForTargetDate.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Ошибка загрузки", 
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.message, 
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { viewModel.refreshSchedule() },
                                    shape = CircleShape,
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text("Повторить", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        if (lessonsForTargetDate.isEmpty()) {
                            EmptySchedule()
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.Top
                            ) {
                                items(
                                    items = lessonsForTargetDate,
                                    key = { "${it.date}_${it.number}_${it.startTime}" }
                                ) { lesson ->
                                    LessonItemWithBreak(
                                        lesson = lesson,
                                        nextLesson = lessonsForTargetDate.getOrNull(lessonsForTargetDate.indexOf(lesson) + 1),
                                        currentTime = currentTime,
                                        currentDate = currentDate,
                                        onClick = { onLessonClick(lesson) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Офлайн-режим • Расписание из кэша",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptySchedule() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp), 
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🎉", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Пар нет", 
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Можно отдыхать и набираться сил", 
                style = MaterialTheme.typography.bodyLarge, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LessonItemWithBreak(
    lesson: Lesson, 
    nextLesson: Lesson?, 
    currentTime: LocalTime,
    currentDate: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val breakDuration = remember(lesson.endTime, nextLesson?.startTime) {
        if (nextLesson != null) {
            calculateSafeBreakMinutes(lesson.endTime.toString(), nextLesson.startTime.toString())
        } else null
    }
    
    Column(modifier = modifier) {
        LessonRow(
            lesson = lesson, 
            currentTime = currentTime, 
            currentDate = currentDate,
            onClick = onClick
        )
        if (breakDuration != null && breakDuration > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Перерыв $breakDuration мин",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

fun calculateSafeBreakMinutes(endStr: String, startStr: String): Int? = runCatching {
    val cleanEnd = endStr.replace("\u00A0", "").trim()
    val cleanStart = startStr.replace("\u00A0", "").trim()
    val t1 = LocalTime.parse(cleanEnd)
    val t2 = LocalTime.parse(cleanStart)
    val diff = ChronoUnit.MINUTES.between(t1, t2).toInt()
    if (diff in 1..120) diff else null
}.getOrNull()

@Composable
fun LessonRow(
    lesson: Lesson, 
    currentTime: LocalTime,
    currentDate: LocalDate,
    onClick: () -> Unit
) {
    val status = remember(lesson, currentTime, currentDate) {
        when {
            currentDate.isAfter(lesson.date) -> LessonStatus.PAST
            currentDate.isBefore(lesson.date) -> LessonStatus.UPCOMING
            currentTime.isAfter(lesson.endTime) -> LessonStatus.PAST
            currentTime.isBefore(lesson.startTime) -> LessonStatus.UPCOMING
            else -> LessonStatus.CURRENT
        }
    }

    val startTimeStr = remember(lesson.startTime) { lesson.startTime.toString() }
    val endTimeStr = remember(lesson.endTime) { lesson.endTime.toString() }
    val typeName = remember(lesson.type) {
        lesson.type.lowercase().replaceFirstChar { it.uppercase() }
    }
    val roomFormatted = remember(lesson.room) {
        formatClassroom(lesson.room)
    }
    
    val containerColor = if (status == LessonStatus.CURRENT) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    
    val contentColor = if (status == LessonStatus.CURRENT) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Balanced time column (titleMedium for start time, labelMedium for end time)
        Column(
            modifier = Modifier
                .width(56.dp)
                .padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = startTimeStr, 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Bold,
                color = if (status == LessonStatus.CURRENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = endTimeStr, 
                style = MaterialTheme.typography.labelMedium, 
                fontWeight = FontWeight.Medium,
                color = if (status == LessonStatus.CURRENT) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Card Container taking dominant width and visual weight
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = if (status == LessonStatus.CURRENT) 2.dp else 1.dp,
            tonalElevation = if (status == LessonStatus.CURRENT) 2.dp else 1.dp,
            onClick = onClick
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (status == LessonStatus.CURRENT) {
                    val totalMinutes = ChronoUnit.MINUTES.between(lesson.startTime, lesson.endTime)
                    val passedMinutes = ChronoUnit.MINUTES.between(lesson.startTime, currentTime)
                    val progress = (passedMinutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
                    val remaining = totalMinutes - passedMinutes

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Идет сейчас • Осталось $remaining мин",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Badges row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lesson Number Pill Badge
                    Surface(
                        shape = CircleShape,
                        color = if (status == LessonStatus.CURRENT) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${lesson.number} пара",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (status == LessonStatus.CURRENT) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Lesson Type Badge
                    Surface(
                        shape = CircleShape,
                        color = if (status == LessonStatus.CURRENT) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (status == LessonStatus.CURRENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (status == LessonStatus.PAST) {
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "Завершилась",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Subject Name - titleMedium, lineHeight 22.sp
                Text(
                    text = lesson.title, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = contentColor,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Bottom Row: Teacher name & Classroom badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lesson.teacher, 
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    // Room Badge using formatClassroom
                    Surface(
                        shape = CircleShape,
                        color = if (status == LessonStatus.CURRENT) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn, 
                                contentDescription = null, 
                                modifier = Modifier.size(14.dp), 
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = roomFormatted, 
                                style = MaterialTheme.typography.labelMedium, 
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
