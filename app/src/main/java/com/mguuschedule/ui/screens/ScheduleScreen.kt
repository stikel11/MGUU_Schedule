package com.mguuschedule.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.Lesson
import com.mguuschedule.ui.components.CollapsibleScreenTitle
import com.mguuschedule.ui.components.ExpressiveLoadingIndicator
import com.mguuschedule.ui.components.PhotosStyleRefreshContainer
import com.mguuschedule.ui.components.StatusBarBlurOverlay
import com.mguuschedule.ui.components.StatusBarScrim
import com.mguuschedule.ui.components.TopScrimProtection
import com.mguuschedule.ui.components.WeekCalendar
import com.mguuschedule.ui.theme.AppMotionScheme
import com.mguuschedule.util.ScheduleImageRenderer
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.mguuschedule.util.formatClassroom
import com.mguuschedule.util.rememberHapticFeedback
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
    unreadNotificationCount: Int = 0,
    onNotificationHistoryClick: () -> Unit = {},
    onLessonClick: (Lesson) -> Unit = {}
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val uiState = viewModel.uiState
    val isRefreshing = viewModel.isRefreshing
    val isOnline by viewModel.isOnline.collectAsState()
    val activeAddonLessonKeys by viewModel.activeAddonLessonKeys.collectAsState()
    val lessonControlPointsMap by viewModel.lessonControlPointsFlow.collectAsState()
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val currentDate = remember { LocalDate.now() }
    
    val currentTimeState = produceState(LocalTime.now()) {
        while (true) {
            delay(60000)
            value = LocalTime.now()
        }
    }

            val listState = rememberLazyListState()
    val lessons by viewModel.lessonsForSelectedDay.collectAsState()
    val hazeState = rememberHazeState()

    val scrollToTopTrigger by viewModel.scrollToTopTrigger.collectAsState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0 && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
            listState.animateScrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            PhotosStyleRefreshContainer(
                isRefreshing = isRefreshing,
                onRefresh = {
                    haptic.gestureThreshold()
                    viewModel.refreshSchedule()
                },
                statusText = viewModel.refreshStatusText,
                isError = viewModel.refreshIsError,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = innerPadding.calculateBottomPadding() + 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item 0: Uniform CollapsibleScreenTitle ("Расписание")
                    item {
                        CollapsibleScreenTitle(
                            title = "Расписание",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Item 1: WeekCalendar
                    item {
                        WeekCalendar(
                            selectedDate = selectedDate,
                            onDateSelected = { viewModel.onDateSelected(it) },
                            unreadNotificationCount = unreadNotificationCount,
                            onNotificationHistoryClick = onNotificationHistoryClick,
                            onShareDayClick = {
                                val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                val shareStyle = appPrefs.getInt("share_card_style", 0)
                                ScheduleImageRenderer.shareDaySchedule(
                                    context = context,
                                    date = selectedDate,
                                    lessons = viewModel.lessonsForSelectedDay.value,
                                    isDarkTheme = isDarkTheme,
                                    shareStyle = shareStyle
                                )
                            }
                        )
                    }

                    // Item 2: Offline banner if offline
                    if (!isOnline) {
                        item {
                            OfflineBanner()
                        }
                    }

                    // Items 3..N: Schedule lessons or loading/error/empty states wrapped in AnimatedContent
                    item {
                        AnimatedContent(
                            targetState = selectedDate,
                            transitionSpec = {
                                val isForward = targetState.isAfter(initialState)
                                val slideDir = if (isForward) 1 else -1
                                (slideInHorizontally(
                                    animationSpec = AppMotionScheme.defaultSpatialSpec(),
                                    initialOffsetX = { fullWidth -> slideDir * (fullWidth / 5) }
                                ) + fadeIn(animationSpec = AppMotionScheme.fastEffectsSpec()))
                                    .togetherWith(
                                        slideOutHorizontally(
                                            animationSpec = AppMotionScheme.fastSpatialSpec(),
                                            targetOffsetX = { fullWidth -> -slideDir * (fullWidth / 5) }
                                        ) + fadeOut(animationSpec = AppMotionScheme.fastEffectsSpec())
                                    )
                                    .using(SizeTransform(clip = false))
                            },
                            label = "day_spatial_transition"
                        ) { targetDate ->
                            val lessonsForTargetDate = if (targetDate == selectedDate) lessons else emptyList()
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (uiState is ScheduleUiState.Loading && !isRefreshing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 60.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ExpressiveLoadingIndicator(
                                            modifier = Modifier.size(36.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else if (uiState is ScheduleUiState.Error && lessonsForTargetDate.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
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
                                                onClick = {
                                                    haptic.click()
                                                    viewModel.refreshSchedule()
                                                },
                                                shape = CircleShape,
                                                modifier = Modifier.padding(top = 16.dp)
                                            ) {
                                                Text("Повторить", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else if (lessonsForTargetDate.isEmpty()) {
                                    EmptySchedule()
                                } else {
                                    val addonsMap by viewModel.lessonAddonSummaries.collectAsState()
                                    lessonsForTargetDate.forEachIndexed { index, lesson ->
                                        val lessonKey = "${lesson.date}_${lesson.number}_${lesson.startTime}"
                                        val linkedCps = lessonControlPointsMap[lessonKey] ?: emptyList()
                                        LessonItemWithBreak(
                                            lesson = lesson,
                                            nextLesson = lessonsForTargetDate.getOrNull(index + 1),
                                            currentTimeProvider = { currentTimeState.value },
                                            currentDate = currentDate,
                                            hasAddon = activeAddonLessonKeys.contains(lessonKey),
                                            addonSummary = addonsMap[lessonKey],
                                            controlPoints = linkedCps,
                                            onClick = { onLessonClick(lesson) },
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Переиспользуемая полоса градиента в зоне статус-бара
        StatusBarBlurOverlay()
    }
}

@Composable
fun OfflineBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
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
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 60.dp), 
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
    currentTimeProvider: () -> LocalTime,
    currentDate: LocalDate,
    hasAddon: Boolean = false,
    addonSummary: com.mguuschedule.repository.LessonAddonSummary? = null,
    controlPoints: List<ControlPoint> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val breakDuration = remember(lesson.endTime, nextLesson?.startTime) {
        if (nextLesson != null) {
            calculateSafeBreakMinutes(lesson.endTime, nextLesson.startTime)
        } else null
    }
    
    Column(modifier = modifier) {
        LessonRow(
            lesson = lesson, 
            currentTimeProvider = currentTimeProvider, 
            currentDate = currentDate,
            hasAddon = hasAddon,
            addonSummary = addonSummary,
            controlPoints = controlPoints,
            onClick = onClick
        )
        if (breakDuration != null && breakDuration > 0) {
            // Compact timeline break badge between lessons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Перерыв $breakDuration мин",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

fun calculateSafeBreakMinutes(endTime: LocalTime, startTime: LocalTime): Int? = runCatching {
    val diff = ChronoUnit.MINUTES.between(endTime, startTime).toInt()
    if (diff in 1..120) diff else null
}.getOrNull()

@Composable
fun LessonRow(
    lesson: Lesson,
    currentTimeProvider: () -> LocalTime,
    currentDate: LocalDate,
    hasAddon: Boolean = false,
    addonSummary: com.mguuschedule.repository.LessonAddonSummary? = null,
    controlPoints: List<ControlPoint> = emptyList(),
    onClick: () -> Unit
) {
    val currentTime = currentTimeProvider()
    val status = when {
        currentDate.isAfter(lesson.date) -> LessonStatus.PAST
        currentDate.isBefore(lesson.date) -> LessonStatus.UPCOMING
        currentTime.isAfter(lesson.endTime) -> LessonStatus.PAST
        currentTime.isBefore(lesson.startTime) -> LessonStatus.UPCOMING
        else -> LessonStatus.CURRENT
    }

    val startTimeStr = remember(lesson.startTime) { lesson.startTime.toString() }
    val endTimeStr = remember(lesson.endTime) { lesson.endTime.toString() }
    val typeName = remember(lesson.type) { lesson.type.lowercase().replaceFirstChar { it.uppercase() } }
    val roomFormatted = remember(lesson.room) { formatClassroom(lesson.room) }
    
    val containerColor = if (status == LessonStatus.CURRENT) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    
    val contentColor = if (status == LessonStatus.CURRENT) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val hasFirstBlock = addonSummary?.hasTasks == true || addonSummary?.hasNote == true
    val hasSecondBlock = addonSummary?.hasMaterials == true
    var revealStep by remember(lesson.id, lesson.date) { mutableIntStateOf(0) }

    LaunchedEffect(lesson.id, lesson.date, hasFirstBlock, hasSecondBlock) {
        if (hasFirstBlock || hasSecondBlock) {
            revealStep = 0
            delay(110L)
            revealStep = 1
            if (hasSecondBlock) {
                delay(150L)
                revealStep = 2
            }
        } else {
            revealStep = 0
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Compact 64dp Left Time Column
        Column(
            modifier = Modifier
                .width(64.dp)
                .padding(top = 16.dp),
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
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Medium,
                color = if (status == LessonStatus.CURRENT) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Dominant Lesson Card
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(dampingRatio = 0.82f, stiffness = 260f)),
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shadowElevation = if (status == LessonStatus.CURRENT) 3.dp else 1.dp,
            tonalElevation = if (status == LessonStatus.CURRENT) 3.dp else 1.dp,
            onClick = onClick
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
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
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Top Badges Row: Type, Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val hasControlPoint = controlPoints.isNotEmpty()
                        val typeText = if (hasControlPoint) "$typeName • КТ" else typeName

                        Surface(
                            shape = CircleShape,
                            color = if (status == LessonStatus.CURRENT) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = typeText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (status == LessonStatus.CURRENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (status == LessonStatus.PAST) {
                        Spacer(modifier = Modifier.width(8.dp))
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
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Subject Title
                Text(
                    text = lesson.title, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 17.sp,
                    color = contentColor,
                    lineHeight = 23.sp
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Bottom Row: Teacher Name & Room Badge
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
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Place, 
                                contentDescription = null, 
                                modifier = Modifier.size(14.dp), 
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = roomFormatted, 
                                style = MaterialTheme.typography.labelMedium, 
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                // --- BENTO ADDONS FOOTER: BLOCK 1 (Tasks & Notes) ---
                AnimatedVisibility(
                    visible = revealStep >= 1 && (hasFirstBlock || hasSecondBlock),
                    enter = expandVertically(spring(0.82f, 260f)) + fadeIn(),
                    exit = shrinkVertically(spring(0.85f, 260f)) + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (addonSummary?.hasTasks == true) {
                            val tasks = addonSummary.tasks
                            val firstTask = tasks.firstOrNull { !it.isCompleted } ?: tasks.first()

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TaskAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Задача: ${firstTask.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (tasks.size > 1) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "+${tasks.size - 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (addonSummary?.hasNote == true) {
                            if (addonSummary.hasTasks) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Notes,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = addonSummary.noteText ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // --- BENTO ADDONS FOOTER: BLOCK 2 (Materials) ---
                AnimatedVisibility(
                    visible = revealStep >= 2 && hasSecondBlock,
                    enter = expandVertically(spring(0.82f, 260f)) + fadeIn(),
                    exit = shrinkVertically(spring(0.85f, 260f)) + fadeOut()
                ) {
                    val materials = addonSummary?.materials ?: emptyList()
                    val displayed = materials.take(2)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        displayed.forEachIndexed { idx, mat ->
                            val isSecondWithMore = idx == 1 && materials.size > 2
                            val icon = when (mat.type) {
                                "URL", "LINK" -> Icons.Rounded.Link
                                "IMAGE" -> Icons.Rounded.Image
                                else -> Icons.Rounded.AttachFile
                            }
                            val displayName = mat.fileName ?: mat.title

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = if (isSecondWithMore) "$displayName (+${materials.size - 2})" else displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
