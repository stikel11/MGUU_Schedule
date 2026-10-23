package com.mguuschedule.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.Lesson
import com.mguuschedule.model.SubjectScore
import com.mguuschedule.repository.LessonAddonSummary
import com.mguuschedule.repository.LessonMaterialEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 1. ГЛАВНЫЙ ХАБ «РАЗДЕЛЫ»
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionsScreen(
    allLessons: List<Lesson>,
    addonSummaries: Map<String, LessonAddonSummary>,
    onOpenCurriculum: () -> Unit,
    onOpenMaterials: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var showMoodleSheet by remember { mutableStateOf(false) }

    val totalMaterialsCount = remember(addonSummaries) {
        addonSummaries.values.sumOf { it.materials.size }
    }
    val totalSubjectsCount = remember(allLessons) {
        allLessons.map { it.title.trim() }.filter { it.isNotEmpty() }.distinct().size
    }

    val attendancePrefs = remember {
        context.getSharedPreferences("attendance_tracker_prefs", Context.MODE_PRIVATE)
    }
    val pastLessons = remember(allLessons) {
        val today = LocalDate.now()
        allLessons.filter { !it.date.isAfter(today) }.ifEmpty { allLessons }
    }
    val attendancePercent = remember(pastLessons, attendancePrefs) {
        if (pastLessons.isEmpty()) 100
        else {
            val attended = pastLessons.count { lesson ->
                val key = "${lesson.date}_${lesson.number}_${lesson.startTime}"
                attendancePrefs.getBoolean(key, true)
            }
            ((attended.toFloat() / pastLessons.size) * 100f).roundToInt()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Единый заголовок экрана без подзаголовков (как в Расписании) + кнопка Настроек
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Разделы",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        onClick = onOpenSettings,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 0.dp,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Настройки",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            item {
                SectionHubCard(
                    title = "Учебный план",
                    subtitle = "Дисциплины, даты консультаций, зачётов и экзаменов",
                    badgeText = if (totalSubjectsCount > 0) "$totalSubjectsCount дисц." else "Семестр",
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenCurriculum
                )
            }

            item {
                SectionHubCard(
                    title = "Материалы к парам",
                    subtitle = "Прикрепленные файлы, презентации и ссылки по предметам",
                    badgeText = "$totalMaterialsCount влож.",
                    icon = Icons.Rounded.FolderCopy,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onOpenMaterials
                )
            }

            item {
                SectionHubCard(
                    title = "Посещаемость",
                    subtitle = "Учёт посещённых и пропущенных занятий по предметам",
                    badgeText = "$attendancePercent%",
                    icon = Icons.Rounded.HowToReg,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onOpenAttendance
                )
            }

            item {
                SectionHubCard(
                    title = "СДО Moodle",
                    subtitle = "Быстрый доступ к учебному порталу и курсам университета",
                    badgeText = "Скоро",
                    icon = Icons.Rounded.Language,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { showMoodleSheet = true }
                )
            }
        }
    }

    if (showMoodleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoodleSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "СДО Moodle",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "В ближайших обновлениях здесь появится встроенный клиент СДО МГУУ с сохранением сессии и быстрым прикреплением файлов к парам.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { showMoodleSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text("Понятно", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SectionHubCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = iconContainerColor,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Единая шапка вложенных экранов (как в «Информации о паре» — без подзаголовков!)
 */
@Composable
private fun UnifiedDetailTopBar(
    title: String,
    onBack: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailingContent?.invoke()
    }
}

/**
 * 2. ЭКРАН «УЧЕБНЫЙ ПЛАН»
 */
private data class CurriculumDisciplineItem(
    val title: String,
    val teachers: String,
    val controlType: String,
    val consultationDate: String?,
    val examOrCreditDate: String?,
    val totalLessonsCount: Int
)

@Composable
fun CurriculumScreen(
    allLessons: List<Lesson>,
    subjectsFromRating: List<SubjectScore>,
    controlPointsMap: Map<String, List<ControlPoint>>,
    onBack: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")) }
    var searchQuery by remember { mutableStateOf("") }

    val disciplines = remember(allLessons, subjectsFromRating, controlPointsMap) {
        val groupedLessons = allLessons.groupBy { it.title.trim() }
        val allTitles = (groupedLessons.keys + subjectsFromRating.map { it.title.trim() })
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        allTitles.map { title ->
            val subjectLessons = groupedLessons[title].orEmpty().sortedBy { it.date }
            val ratingSubject = subjectsFromRating.firstOrNull {
                it.title.trim().equals(title, ignoreCase = true)
            }
            val cps = controlPointsMap.entries.firstOrNull {
                it.key.trim().equals(title, ignoreCase = true)
            }?.value.orEmpty()

            val teachers = subjectLessons.map { it.teacher.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")

            val consultLesson = subjectLessons.lastOrNull {
                it.type.contains("консульт", ignoreCase = true)
            }
            val consultCp = cps.firstOrNull {
                it.pointName.contains("консульт", ignoreCase = true) && it.date.isNotBlank()
            }
            val consultationDate = consultLesson?.date?.format(dateFormatter) ?: consultCp?.date

            val examLesson = subjectLessons.lastOrNull {
                it.type.contains("экзамен", ignoreCase = true) ||
                    it.type.contains("зачет", ignoreCase = true) ||
                    it.type.contains("зачёт", ignoreCase = true) ||
                    it.type.contains("аттестац", ignoreCase = true)
            }
            val examCp = cps.lastOrNull {
                (it.pointName.contains("экзамен", ignoreCase = true) ||
                    it.pointName.contains("зачет", ignoreCase = true) ||
                    it.pointName.contains("зачёт", ignoreCase = true) ||
                    it.pointName.contains("итог", ignoreCase = true)) && it.date.isNotBlank()
            } ?: cps.lastOrNull { it.date.isNotBlank() }

            val examDate = examLesson?.date?.format(dateFormatter) ?: examCp?.date

            val controlType = when {
                !ratingSubject?.controlType.isNullOrBlank() -> ratingSubject.controlType
                examLesson != null -> examLesson.type
                else -> "Зачёт"
            }

            CurriculumDisciplineItem(
                title = title,
                teachers = teachers.ifBlank { "Преподаватель кафедры" },
                controlType = controlType,
                consultationDate = consultationDate,
                examOrCreditDate = examDate,
                totalLessonsCount = subjectLessons.size
            )
        }
    }

    val filteredDisciplines = remember(disciplines, searchQuery) {
        if (searchQuery.isBlank()) disciplines
        else disciplines.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.teachers.contains(searchQuery, ignoreCase = true) ||
                it.controlType.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                UnifiedDetailTopBar(
                    title = "Учебный план",
                    onBack = onBack
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Поиск дисциплины...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            items(filteredDisciplines, key = { it.title }) { item ->
                val isExam = item.controlType.contains("экз", ignoreCase = true)

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = item.controlType,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }

                            if (item.totalLessonsCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Text(
                                        text = "${item.totalLessonsCount} зан.",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.teachers,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Консультация
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Forum,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Консультация",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.consultationDate ?: "Не назначена",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (item.consultationDate != null)
                                            MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // Дата экзамена / зачёта
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.EventAvailable,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (isExam) "Дата экзамена" else "Дата зачёта",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.examOrCreditDate ?: "Уточняется",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (item.examOrCreditDate != null)
                                            MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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

/**
 * 3. ЭКРАН «МАТЕРИАЛЫ К ПАРАМ»
 */
private data class SubjectMaterialItem(
    val subjectTitle: String,
    val lessonDate: LocalDate?,
    val material: LessonMaterialEntity
)

@Composable
fun MaterialsHubScreen(
    allLessons: List<Lesson>,
    addonSummaries: Map<String, LessonAddonSummary>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var sortNewestFirst by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val addedAtFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))
    }

    val lessonByKey = remember(allLessons) {
        allLessons.associateBy { "${it.date}_${it.number}_${it.startTime}" }
    }

    val groupedBySubject = remember(addonSummaries, lessonByKey, sortNewestFirst, searchQuery) {
        val allItems = addonSummaries.flatMap { (lessonKey, summary) ->
            val matchedLesson = lessonByKey[lessonKey]
            val subjectName = matchedLesson?.title ?: "Общие материалы"
            summary.materials.map { mat ->
                SubjectMaterialItem(
                    subjectTitle = subjectName,
                    lessonDate = matchedLesson?.date,
                    material = mat
                )
            }
        }.filter {
            searchQuery.isBlank() ||
                it.subjectTitle.contains(searchQuery, ignoreCase = true) ||
                it.material.title.contains(searchQuery, ignoreCase = true) ||
                (it.material.fileName?.contains(searchQuery, ignoreCase = true) == true)
        }

        val sortedItems = if (sortNewestFirst) {
            allItems.sortedByDescending { it.material.createdAt }
        } else {
            allItems.sortedBy { it.material.createdAt }
        }

        sortedItems.groupBy { it.subjectTitle }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                UnifiedDetailTopBar(
                    title = "Материалы",
                    onBack = onBack,
                    trailingContent = {
                        Surface(
                            onClick = { sortNewestFirst = !sortNewestFirst },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapVert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (sortNewestFirst) "Сначала новые" else "Сначала старые",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Поиск по файлам или предметам...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            if (groupedBySubject.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Нет прикрепленных материалов",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Прикрепляйте файлы и ссылки внутри карточки любой пары — они автоматически появятся здесь.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                groupedBySubject.forEach { (subjectTitle, itemsForSubject) ->
                    item(key = "subject_$subjectTitle") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shadowElevation = 0.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = subjectTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${itemsForSubject.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                itemsForSubject.forEach { entry ->
                                    val mat = entry.material
                                    val formattedAddedDate = remember(mat.createdAt) {
                                        Instant.ofEpochMilli(mat.createdAt)
                                            .atZone(ZoneId.systemDefault())
                                            .format(addedAtFormatter)
                                    }
                                    val isLink = mat.type.equals("URL", ignoreCase = true) ||
                                        mat.type.equals("LINK", ignoreCase = true)

                                    Surface(
                                        onClick = {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mat.uriOrUrl)).apply {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = if (isLink) Icons.Rounded.Link else Icons.AutoMirrored.Rounded.InsertDriveFile,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = mat.fileName ?: mat.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Прикреплено: $formattedAddedDate",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    }
}

/**
 * 4. ЭКРАН «ПОСЕЩАЕМОСТЬ» (В единой цветовой гамме Material You без чужеродных цветов)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    allLessons: List<Lesson>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("attendance_tracker_prefs", Context.MODE_PRIVATE)
    }

    var attendanceVersion by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableIntStateOf(0) }
    var selectedSubjectForSheet by remember { mutableStateOf<String?>(null) }

    val today = remember { LocalDate.now() }
    val trackableLessons = remember(allLessons, selectedFilter, today) {
        val base = allLessons.filter { !it.date.isAfter(today) }.ifEmpty { allLessons }
        when (selectedFilter) {
            2 -> base.filter { it.type.contains("лекц", ignoreCase = true) }
            3 -> base.filter { it.type.contains("практ", ignoreCase = true) || it.type.contains("семинар", ignoreCase = true) }
            else -> base
        }
    }

    fun isLessonAttended(lesson: Lesson): Boolean {
        val key = "${lesson.date}_${lesson.number}_${lesson.startTime}"
        return prefs.getBoolean(key, true)
    }

    fun toggleLessonAttendance(lesson: Lesson) {
        val key = "${lesson.date}_${lesson.number}_${lesson.startTime}"
        val next = !prefs.getBoolean(key, true)
        prefs.edit().putBoolean(key, next).apply()
        attendanceVersion++
    }

    val totalCount = remember(trackableLessons, attendanceVersion) { trackableLessons.size }
    val attendedCount = remember(trackableLessons, attendanceVersion) {
        trackableLessons.count { isLessonAttended(it) }
    }
    val missedCount = totalCount - attendedCount
    val overallPercent = if (totalCount > 0) ((attendedCount.toFloat() / totalCount) * 100f).roundToInt() else 100

    val subjectStats = remember(trackableLessons, attendanceVersion, searchQuery, selectedFilter) {
        trackableLessons
            .groupBy { it.title.trim() }
            .map { (subject, lessons) ->
                val subjTotal = lessons.size
                val subjAttended = lessons.count { isLessonAttended(it) }
                val subjPercent = if (subjTotal > 0) ((subjAttended.toFloat() / subjTotal) * 100f).roundToInt() else 100
                Triple(subject, Pair(subjAttended, subjTotal), subjPercent)
            }
            .filter { (subject, counts, _) ->
                val matchesSearch = searchQuery.isBlank() || subject.contains(searchQuery, ignoreCase = true)
                val matchesMissed = if (selectedFilter == 1) counts.first < counts.second else true
                matchesSearch && matchesMissed
            }
            .sortedBy { it.first }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                UnifiedDetailTopBar(
                    title = "Посещаемость",
                    onBack = onBack
                )
            }

            // Главная сводная карточка в стиле Material You
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Общая посещаемость",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$overallPercent%",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.HowToReg,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (overallPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Посещено
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$attendedCount",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Посещено",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            // Пропущено
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$missedCount",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Пропущено",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            // Всего пар
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$totalCount",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Всего пар",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Поле поиска без рамок
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Поиск по предмету...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            // Чипы фильтрации в стиле капсул приложения
            item {
                val filters = listOf("Все", "С пропусками", "Лекции", "Практики")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEachIndexed { index, label ->
                        val selected = selectedFilter == index
                        Surface(
                            onClick = { selectedFilter = index },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // Карточки дисциплин
            items(subjectStats, key = { it.first }) { (subjectTitle, counts, percent) ->
                val isLow = percent < 50
                val badgeBg = if (isLow) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
                val badgeFg = if (isLow) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer

                Surface(
                    onClick = { selectedSubjectForSheet = subjectTitle },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = subjectTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${counts.first} из ${counts.second} пар",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = badgeBg
                                ) {
                                    Text(
                                        text = "$percent%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeFg,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Шторка отметки конкретных пар предмета
    selectedSubjectForSheet?.let { subjectTitle ->
        val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM (EE)", Locale.forLanguageTag("ru")) }
        val subjectLessons = remember(trackableLessons, subjectTitle, attendanceVersion) {
            trackableLessons
                .filter { it.title.trim() == subjectTitle }
                .sortedWith(compareByDescending<Lesson> { it.date }.thenByDescending { it.startTime })
        }

        ModalBottomSheet(
            onDismissRequest = { selectedSubjectForSheet = null },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = subjectTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(subjectLessons, key = { "${it.date}_${it.number}_${it.startTime}" }) { lesson ->
                        val attended = isLessonAttended(lesson)
                        val pillBg = if (attended) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                        val pillFg = if (attended) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer

                        Surface(
                            onClick = { toggleLessonAttendance(lesson) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "${lesson.date.format(dateFormatter)} • ${lesson.startTime}–${lesson.endTime}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = lesson.type,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = pillBg
                                ) {
                                    Text(
                                        text = if (attended) "Посещено" else "Пропущено",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = pillFg,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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