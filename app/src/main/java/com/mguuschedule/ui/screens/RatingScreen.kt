package com.mguuschedule.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.StudentRating
import com.mguuschedule.model.SubjectScore
import com.mguuschedule.ui.components.CollapsibleScreenTitle
import com.mguuschedule.ui.components.ExpressiveLoadingIndicator
import com.mguuschedule.ui.components.PhotosStyleRefreshContainer
import com.mguuschedule.ui.components.StatusBarBlurOverlay
import com.mguuschedule.ui.components.StatusBarScrim
import com.mguuschedule.ui.components.TopScrimProtection
import com.mguuschedule.ui.theme.AppMotionScheme
import com.mguuschedule.util.rememberHapticFeedback
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingScreen(
    viewModel: RatingViewModel = viewModel(
        factory = RatingViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState = viewModel.uiState
    val isRefreshing = viewModel.isRefreshing
    val controlPointsMap by viewModel.subjectControlPoints.collectAsState()
    val loadingPointsSet by viewModel.loadingControlPoints.collectAsState()
    val haptic = rememberHapticFeedback()

    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()

    val scrollToTopTrigger by viewModel.scrollToTopTrigger.collectAsState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0 && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
            listState.animateScrollToItem(0)
        }
    }

    var expandedSubjectUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadRating()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            containerColor = Color.Transparent
        ) { innerPadding ->
            PhotosStyleRefreshContainer(
                isRefreshing = isRefreshing,
                onRefresh = {
                    haptic.gestureThreshold()
                    viewModel.loadRating(forceRefresh = true)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = innerPadding.calculateBottomPadding() + 100.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        CollapsibleScreenTitle(title = "Рейтинг БРС")
                    }

                    when (uiState) {
                        is RatingUiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        ExpressiveLoadingIndicator(
                                            modifier = Modifier.size(36.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Загрузка рейтинга с портала МГУУ...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        is RatingUiState.Error -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CloudOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Ошибка загрузки рейтинга",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = uiState.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Button(
                                            onClick = {
                                                haptic.click()
                                                viewModel.loadRating(forceRefresh = true)
                                            },
                                            shape = CircleShape
                                        ) {
                                            Text("Повторить запрос", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        is RatingUiState.Success -> {
                            val student = uiState.student
                            val subjects = uiState.subjects
                            val studentsList = uiState.studentsList

                            // 1. Academic Period Selectors (Years & Semesters)
                            if (uiState.availableYears.isNotEmpty() || uiState.availableSemesters.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val currentYear = uiState.availableYears.find { it.id == viewModel.selectedYearId } ?: uiState.availableYears.find { it.isSelected }
                                        val currentSem = uiState.availableSemesters.find { it.id == viewModel.selectedSemId } ?: uiState.availableSemesters.find { it.isSelected }

                                        var showYearDropdown by remember { mutableStateOf(false) }
                                        var showSemDropdown by remember { mutableStateOf(false) }

                                        if (uiState.availableYears.isNotEmpty()) {
                                            Box {
                                                FilterChip(
                                                    selected = true,
                                                    onClick = {
                                                        if (uiState.availableYears.size > 1) {
                                                            showYearDropdown = true
                                                        } else if (currentYear != null && currentSem != null) {
                                                            haptic.click()
                                                            viewModel.selectPeriod(currentYear.id, currentSem.id)
                                                        }
                                                    },
                                                    label = { Text(currentYear?.title ?: "Учебный год") },
                                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                                    shape = CircleShape
                                                )

                                                DropdownMenu(
                                                    expanded = showYearDropdown,
                                                    onDismissRequest = { showYearDropdown = false }
                                                ) {
                                                    uiState.availableYears.forEach { year ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    text = year.title,
                                                                    fontWeight = if (year.id == currentYear?.id) FontWeight.Bold else FontWeight.Normal
                                                                )
                                                            },
                                                            onClick = {
                                                                showYearDropdown = false
                                                                val semId = currentSem?.id ?: uiState.availableSemesters.firstOrNull()?.id ?: "0"
                                                                haptic.click()
                                                                viewModel.selectPeriod(year.id, semId)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (uiState.availableSemesters.isNotEmpty()) {
                                            Box {
                                                FilterChip(
                                                    selected = true,
                                                    onClick = {
                                                        if (uiState.availableSemesters.size > 1) {
                                                            showSemDropdown = true
                                                        } else if (currentYear != null && currentSem != null) {
                                                            haptic.click()
                                                            viewModel.selectPeriod(currentYear.id, currentSem.id)
                                                        }
                                                    },
                                                    label = { Text(currentSem?.title ?: "Семестр") },
                                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                                                    shape = CircleShape
                                                )

                                                DropdownMenu(
                                                    expanded = showSemDropdown,
                                                    onDismissRequest = { showSemDropdown = false }
                                                ) {
                                                    uiState.availableSemesters.forEach { sem ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    text = sem.title,
                                                                    fontWeight = if (sem.id == currentSem?.id) FontWeight.Bold else FontWeight.Normal
                                                                )
                                                            },
                                                            onClick = {
                                                                showSemDropdown = false
                                                                val yearId = currentYear?.id ?: uiState.availableYears.firstOrNull()?.id ?: ""
                                                                haptic.click()
                                                                viewModel.selectPeriod(yearId, sem.id)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Hero Overall Score Card
                            if (student != null) {
                                item {
                                    StudentHeroRatingCard(
                                        student = student,
                                        studentsList = studentsList,
                                        onSelectStudent = { selected ->
                                            viewModel.selectStudent(selected)
                                        }
                                    )
                                }
                            }

                            // Section Title
                            if (subjects.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Дисциплины (${subjects.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            if (subjects.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                modifier = Modifier.size(44.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "За выбранный период данные рейтинга отсутствуют",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Выберите другой учебный год или семестр в кнопках выше",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(subjects, key = { it.title + it.detailUrl }) { subject ->
                                    val isExpanded = expandedSubjectUrl == subject.detailUrl
                                    val points = controlPointsMap[subject.detailUrl]
                                    val isLoadingPoints = loadingPointsSet.contains(subject.detailUrl)

                                    SubjectScoreCard(
                                        subject = subject,
                                        isExpanded = isExpanded,
                                        controlPoints = points,
                                        isLoadingPoints = isLoadingPoints,
                                        onClick = {
                                            haptic.lightTick()
                                            if (isExpanded) {
                                                expandedSubjectUrl = null
                                            } else {
                                                expandedSubjectUrl = subject.detailUrl
                                                viewModel.fetchControlPointsForSubject(subject)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Переиспользуемая матовая блюр-полоса в зоне статус-бара
        StatusBarBlurOverlay(hazeState = hazeState)
    }
}

fun formatScore(score: Float?): String {
    if (score == null) return "—"
    return if (score % 1.0f == 0.0f) {
        score.toInt().toString()
    } else {
        score.toString().replace('.', ',')
    }
}

@Composable
fun StudentHeroRatingCard(
    student: StudentRating?,
    studentsList: List<StudentRating>,
    onSelectStudent: (StudentRating) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }

    val totalScoreStr = remember(student?.totalScore) { formatScore(student?.totalScore) }
    val module1Str = remember(student?.module1) { formatScore(student?.module1) }
    val module2Str = remember(student?.module2) { formatScore(student?.module2) }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zachetka Badge Button
                Box {
                    Surface(
                        onClick = { if (studentsList.size > 1) showDropdown = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Badge,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = student?.zachetka ?: "Зачетка не указана",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (studentsList.size > 1) {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        studentsList.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.zachetka, fontWeight = if (s.zachetka == student?.zachetka) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onSelectStudent(s)
                                    showDropdown = false
                                }
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "Университет МГУУ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Общий рейтинг студента",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = totalScoreStr,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "баллов",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricPill(
                    label = "Модуль 1",
                    value = "$module1Str б.",
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    label = "Модуль 2",
                    value = "$module2Str б.",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SubjectScoreCard(
    subject: SubjectScore,
    isExpanded: Boolean,
    controlPoints: List<ControlPoint>?,
    isLoadingPoints: Boolean,
    onClick: () -> Unit
) {
    val module1Str = remember(subject.module1) { formatScore(subject.module1) }
    val module2Str = remember(subject.module2) { formatScore(subject.module2) }
    val totalScoreStr = remember(subject.totalScore) { formatScore(subject.totalScore) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = subject.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (subject.controlType.isNotBlank()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = subject.controlType,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScoreLabelValue("М1", module1Str)
                    ScoreLabelValue("М2", module2Str)
                    ScoreLabelValue("Итого", totalScoreStr, isPrimary = true)
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Свернуть" else "Подробности",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expandable Control Points List
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(AppMotionScheme.defaultSpatialSpec()) + fadeIn(AppMotionScheme.fastEffectsSpec()),
                exit = shrinkVertically(AppMotionScheme.defaultSpatialSpec()) + fadeOut(AppMotionScheme.fastEffectsSpec())
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Контрольные точки",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoadingPoints) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (controlPoints.isNullOrEmpty()) {
                        Text(
                            text = "Детальные контрольные точки отсутствуют",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            controlPoints.forEach { point ->
                                ControlPointRow(point = point)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreLabelValue(label: String, value: String, isPrimary: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isPrimary) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ControlPointRow(point: ControlPoint) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.pointName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (point.date.isNotBlank()) {
                    Text(
                        text = point.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val scoreText = point.score?.toString() ?: "—"
            Surface(
                shape = CircleShape,
                color = if (point.score != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = "$scoreText б.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (point.score != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
