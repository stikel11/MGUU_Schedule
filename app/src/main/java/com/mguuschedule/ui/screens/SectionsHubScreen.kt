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
import com.mguuschedule.model.toLesson
import com.mguuschedule.repository.LessonAddonSummary
import com.mguuschedule.repository.LessonMaterialEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ROOM_API_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")

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
    onOpenFreeRooms: () -> Unit,
    onOpenMoodle: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val isMoodleConnected = remember {
        com.mguuschedule.repository.MoodleRepository.hasSavedSession(context)
    }

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
            // Единый заголовок экрана (выровнен по вертикали с CollapsibleScreenTitle в Расписании и Рейтинге) + кнопка Настроек
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
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
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SectionHubCard(
                        title = "Учебный план",
                        subtitle = "Дисциплины, даты консультаций, зачётов и экзаменов",
                        badgeText = if (totalSubjectsCount > 0) "$totalSubjectsCount дисц." else "Семестр",
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = com.mguuschedule.ui.theme.ExpressiveShapes.segmentedItem(0, 5),
                        onClick = onOpenCurriculum
                    )

                    SectionHubCard(
                        title = "Материалы к парам",
                        subtitle = "Прикрепленные файлы, презентации и ссылки по предметам",
                        badgeText = "$totalMaterialsCount влож.",
                        icon = Icons.Rounded.FolderCopy,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = com.mguuschedule.ui.theme.ExpressiveShapes.segmentedItem(1, 5),
                        onClick = onOpenMaterials
                    )

                    SectionHubCard(
                        title = "Посещаемость",
                        subtitle = "Учёт посещённых и пропущенных занятий по предметам",
                        badgeText = "$attendancePercent%",
                        icon = Icons.Rounded.HowToReg,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = com.mguuschedule.ui.theme.ExpressiveShapes.segmentedItem(2, 5),
                        onClick = onOpenAttendance
                    )

                    SectionHubCard(
                        title = "Свободные аудитории",
                        subtitle = "Поиск свободных кабинетов на Сухаревской и Войковской",
                        badgeText = "Кампус",
                        icon = Icons.Rounded.MeetingRoom,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = com.mguuschedule.ui.theme.ExpressiveShapes.segmentedItem(3, 5),
                        onClick = onOpenFreeRooms
                    )

                    SectionHubCard(
                        title = "СДО Moodle",
                        subtitle = "Курсы, задания, оценки и веб-портал online.mguu.ru",
                        badgeText = if (isMoodleConnected) "Онлайн" else "Вход",
                        icon = Icons.Rounded.School,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = com.mguuschedule.ui.theme.ExpressiveShapes.segmentedItem(4, 5),
                        onClick = onOpenMoodle
                    )
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
    shape: androidx.compose.ui.graphics.Shape = com.mguuschedule.ui.theme.ExpressiveShapes.Card,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
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
            val cps = ratingSubject?.detailUrl?.takeIf { it.isNotBlank() }?.let { controlPointsMap[it] }
                ?: controlPointsMap.entries.firstOrNull {
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
                    shape = com.mguuschedule.ui.theme.ExpressiveShapes.SearchBar,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            items(
                items = filteredDisciplines,
                key = { it.title },
                contentType = { "discipline" }
            ) { item ->
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
                                        else MaterialTheme.colorScheme.onSurfaceVariant
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
                                        else MaterialTheme.colorScheme.onSurfaceVariant
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
                    shape = com.mguuschedule.ui.theme.ExpressiveShapes.SearchBar,
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
                        color = MaterialTheme.colorScheme.surfaceContainer,
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
                    item(key = "subject_$subjectTitle", contentType = "subject_materials") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
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
    groupName: String,
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
                    onBack = onBack,
                    trailingContent = {
                        if (groupName.isNotBlank()) {
                            Text(
                                text = "Группа $groupName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                )
            }

            // Главная сводная карточка в стиле Material You
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
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
                    shape = com.mguuschedule.ui.theme.ExpressiveShapes.SearchBar,
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
            items(
                items = subjectStats,
                key = { it.first },
                contentType = { "subject_stat" }
            ) { (subjectTitle, counts, percent) ->
                val isLow = percent < 50
                val badgeBg = if (isLow) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
                val badgeFg = if (isLow) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer

                Surface(
                    onClick = { selectedSubjectForSheet = subjectTitle },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
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
                    items(
                        items = subjectLessons,
                        key = { "${it.date}_${it.number}_${it.startTime}" },
                        contentType = { "attendance_lesson" }
                    ) { lesson ->
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

/**
 * 5. ЭКРАН «СВОБОДНЫЕ АУДИТОРИИ»
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeRoomsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val roomsCachePrefs = remember(context) {
        context.getSharedPreferences("free_rooms_cache", Context.MODE_PRIVATE)
    }
    var selectedCampus by remember { mutableStateOf("sretenka") } // "sretenka" или "voikovskaya"
    var searchQuery by remember { mutableStateOf("") }
    var selectedFloor by remember { mutableStateOf("Все этажи") }

    var rooms by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isShowingCache by remember { mutableStateOf(false) }
    var cachedTimeLabel by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = com.mguuschedule.util.rememberHapticFeedback()

    fun parseRoomsArray(rawJson: String): List<org.json.JSONObject>? {
        return runCatching {
            val root = org.json.JSONObject(rawJson)
            if (root.has("ok") && !root.optBoolean("ok")) return@runCatching null
            val array = root.optJSONArray("rooms") ?: root.optJSONArray("freeRooms") ?: root.optJSONArray("items") ?: root.optJSONArray("data")
            if (array != null) {
                val list = ArrayList<org.json.JSONObject>(array.length())
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { list.add(it) }
                }
                list
            } else {
                emptyList()
            }
        }.getOrNull()
    }

    fun loadRooms(forceRefresh: Boolean = false) {
        val campus = selectedCampus
        scope.launch {
            errorMsg = null
            // 1. Мгновенно поднимаем локальный кэш для выбранного кампуса
            val (cachedList, cachedTs) = withContext(Dispatchers.IO) {
                val raw = roomsCachePrefs.getString("rooms_$campus", null)
                val ts = roomsCachePrefs.getLong("rooms_ts_$campus", 0L)
                val parsed = raw?.let { parseRoomsArray(it) }
                parsed to ts
            }
            val hasCache = !cachedList.isNullOrEmpty()
            if (!cachedList.isNullOrEmpty() && !forceRefresh) {
                rooms = cachedList
                isShowingCache = true
                cachedTimeLabel = if (cachedTs > 0L) {
                    Instant.ofEpochMilli(cachedTs)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                } else null
                isLoading = false
            } else {
                isLoading = true
            }

            // 2. Обновляем из сети в фоне (Stale-While-Revalidate)
            try {
                val freshJson = withContext(Dispatchers.IO) {
                    val url = "https://api.lk-upm.ru/api/rooms/free?campus=$campus"
                    org.jsoup.Jsoup.connect(url)
                        .ignoreContentType(true)
                        .timeout(10_000)
                        .execute()
                        .body()
                }
                val parsedFresh = withContext(Dispatchers.Default) { parseRoomsArray(freshJson) }

                if (parsedFresh == null) {
                    if (!hasCache) errorMsg = "Ошибка API"
                } else {
                    rooms = parsedFresh
                    isShowingCache = false
                    cachedTimeLabel = null
                    withContext(Dispatchers.IO) {
                        roomsCachePrefs.edit()
                            .putString("rooms_$campus", freshJson)
                            .putLong("rooms_ts_$campus", System.currentTimeMillis())
                            .apply()
                    }
                }
            } catch (e: Exception) {
                if (!hasCache) {
                    errorMsg = e.message ?: "Нет подключения к сети"
                } else {
                    isShowingCache = true
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedCampus) {
        selectedFloor = "Все этажи"
        loadRooms(forceRefresh = false)
    }

    val availableFloors = remember(rooms) {
        val floors = rooms.mapNotNull { 
            val f = it.optString("floor")
            if (f.isNotBlank() && f != "null") "$f этаж" else null 
        }.distinct().sorted()
        listOf("Все этажи") + floors
    }

    val filteredRooms = remember(rooms, searchQuery, selectedFloor) {
        rooms.filter {
            val roomName = it.optString("room", "")
            val floorStr = it.optString("floor", "")
            val matchesSearch = searchQuery.isBlank() || roomName.contains(searchQuery, ignoreCase = true)
            val matchesFloor = selectedFloor == "Все этажи" || "$floorStr этаж" == selectedFloor
            matchesSearch && matchesFloor
        }
    }

    var selectedRoomForSchedule by remember { mutableStateOf<String?>(null) }

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
                    title = "Свободные аудитории",
                    onBack = onBack,
                    trailingContent = {
                        IconButton(onClick = { loadRooms(forceRefresh = true) }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }

            if (isShowingCache && cachedTimeLabel != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Показан сохранённый кэш от $cachedTimeLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val isSretenka = selectedCampus == "sretenka"
                        Surface(
                            onClick = { 
                                haptic.selection()
                                selectedCampus = "sretenka" 
                            },
                            shape = CircleShape,
                            color = if (isSretenka) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Сухаревская",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSretenka) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSretenka) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        val isVoikovskaya = selectedCampus == "voikovskaya"
                        Surface(
                            onClick = { 
                                haptic.selection()
                                selectedCampus = "voikovskaya" 
                            },
                            shape = CircleShape,
                            color = if (isVoikovskaya) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Войковская",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isVoikovskaya) FontWeight.Bold else FontWeight.Medium,
                                color = if (isVoikovskaya) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Поиск аудитории...",
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
                    shape = com.mguuschedule.ui.theme.ExpressiveShapes.SearchBar,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableFloors.forEach { floorLabel ->
                        val isSelected = selectedFloor == floorLabel
                        Surface(
                            onClick = { selectedFloor = floorLabel },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Text(
                                text = floorLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        com.mguuschedule.ui.components.ExpressiveLoadingIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (errorMsg != null) {
                item {
                    Text("Ошибка: $errorMsg", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            } else if (filteredRooms.isEmpty()) {
                item {
                    Text("Аудитории не найдены", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(
                    items = filteredRooms,
                    key = { it.optString("room") },
                    contentType = { "room" }
                ) { roomObj ->
                    val roomName = roomObj.optString("room", "")
                    val freeUntil = roomObj.optString("freeUntil", "")
                    val lessonsToday = roomObj.optInt("lessonsToday", 0)
                    val floor = roomObj.optString("floor", "")

                    val subtitleText = when {
                        freeUntil.isNotBlank() && freeUntil != "null" -> "Свободна до $freeUntil"
                        lessonsToday == 0 -> "Занятий по расписанию сегодня нет"
                        else -> "Занятий за день: $lessonsToday"
                    }

                    Surface(
                        onClick = { selectedRoomForSchedule = roomName },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.MeetingRoom, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            
                            Spacer(Modifier.width(14.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ауд. $roomName",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Spacer(Modifier.width(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (floor.isNotBlank() && floor != "null") {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            text = "$floor этаж",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
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

    selectedRoomForSchedule?.let { roomName ->
        var roomLessons by remember { mutableStateOf<List<Lesson>?>(null) }
        var roomLoading by remember { mutableStateOf(true) }
        val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")) }

        LaunchedEffect(roomName) {
            val cacheKey = "room_sched_${roomName.trim().lowercase()}"
            val cachedLessons = withContext(Dispatchers.IO) {
                val raw = roomsCachePrefs.getString(cacheKey, null) ?: return@withContext null
                runCatching {
                    com.mguuschedule.util.ScheduleParser.parse(raw)
                        .mapNotNull { it.toLesson() }
                        .sortedWith(compareBy<Lesson> { it.date }.thenBy { it.startTime })
                }.getOrNull()
            }
            if (cachedLessons != null) {
                roomLessons = cachedLessons
                roomLoading = false
            } else {
                roomLoading = true
            }

            try {
                val (freshResponse, loaded) = withContext(Dispatchers.IO) {
                    val today = LocalDate.now()
                    val todayStr = today.format(ROOM_API_DATE_FORMATTER)
                    val weekLaterStr = today.plusDays(7).format(ROOM_API_DATE_FORMATTER)
                    val encodedRoom = java.net.URLEncoder.encode(roomName, "UTF-8")

                    val url = "https://api.lk-upm.ru/api/room?name=$encodedRoom&from=$todayStr&to=$weekLaterStr"
                    val response = org.jsoup.Jsoup.connect(url)
                        .ignoreContentType(true)
                        .timeout(10_000)
                        .execute()
                        .body()

                    val parsed = com.mguuschedule.util.ScheduleParser.parse(response)
                        .mapNotNull { it.toLesson() }
                        .sortedWith(compareBy<Lesson> { it.date }.thenBy { it.startTime })
                    response to parsed
                }
                roomLessons = loaded
                withContext(Dispatchers.IO) {
                    roomsCachePrefs.edit()
                        .putString(cacheKey, freshResponse)
                        .putLong("${cacheKey}_ts", System.currentTimeMillis())
                        .apply()
                }
            } catch (e: Exception) {
                if (cachedLessons == null) {
                    roomLessons = emptyList()
                }
            } finally {
                roomLoading = false
            }
        }

        ModalBottomSheet(
            onDismissRequest = { selectedRoomForSchedule = null },
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
                    text = "Расписание: Ауд. $roomName",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (roomLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        com.mguuschedule.ui.components.ExpressiveLoadingIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (roomLessons.isNullOrEmpty()) {
                    Text("В ближайшую неделю занятий в этой аудитории нет.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(
                            items = roomLessons!!,
                            key = { "${it.date}_${it.number}_${it.startTime}_${it.title}" },
                            contentType = { "room_lesson" }
                        ) { lesson ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${lesson.date.format(dateFormatter)} • ${lesson.startTime}–${lesson.endTime}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                            Text(lesson.type, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = lesson.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = lesson.teacher,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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