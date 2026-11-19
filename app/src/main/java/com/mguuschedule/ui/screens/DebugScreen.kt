package com.mguuschedule.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.ui.components.TopScrimProtection
import com.mguuschedule.util.AppLogger
import com.mguuschedule.util.CrashHandler
import com.mguuschedule.util.NotificationHelper
import com.mguuschedule.util.rememberHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

data class ApiTestResult(val title: String, val result: String?, val isError: Boolean, val timeMs: Long?)

private val DEBUG_LOG_FILTERS = listOf("Все", "API / Сеть", "Парсер / Кэш", "Ошибки / Warn")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: ProfileViewModel = viewModel(),
    onBack: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    val logs by AppLogger.logs.collectAsState()
    val syncDiagnostics by AppLogger.syncDiagnostics.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lastCrash = remember { CrashHandler.getLastCrash(context) }
    var crashReport by remember { mutableStateOf(lastCrash) }
    val scope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val selectedGroupId = remember { prefs.getString("selected_group_id", "—") }
    val selectedGroupName = remember { prefs.getString("selected_group_name", "—") }
    val selectedZachetka = remember { prefs.getString("selected_zachetka", "—") }

    var apiTestResults by remember { mutableStateOf<List<ApiTestResult>>(emptyList()) }
    var isTestingApi by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf("Все") }

    val filteredLogs = remember(logs, activeFilter) {
        when (activeFilter) {
            "API / Сеть" -> logs.filter { it.tag.contains("API") }
            "Парсер / Кэш" -> logs.filter { it.tag.contains("PARSER") || it.tag.contains("CACHE") }
            "Ошибки / Warn" -> logs.filter { it.level == "ERROR" || it.level == "WARN" }
            else -> logs
        }
    }

    fun runApiTests() {
        if (isTestingApi) return
        isTestingApi = true
        apiTestResults = emptyList()
        scope.launch {
            try {
                // Test 1: Groups (schedule.mguu.ru)
                val t1Res = withContext(Dispatchers.IO) {
                    val t1Start = System.currentTimeMillis()
                    val groupsRes = Jsoup.connect("${com.mguuschedule.repository.ScheduleRepository.SCHEDULE_API_BASE}/levels/bak/groups/")
                        .ignoreContentType(true)
                        .header("Accept", "application/json")
                        .execute()
                        .body()
                    val t1End = System.currentTimeMillis()
                    val parsedGroups = com.mguuschedule.util.ScheduleParser.parseGroupsForLevel(groupsRes)
                    val groupFound = parsedGroups.any { it.id == selectedGroupId }
                    ApiTestResult("GET schedule.mguu.ru/levels/bak/groups/", "OK • ${parsedGroups.size} групп (бак.) • Найдена=$groupFound", parsedGroups.isEmpty(), t1End - t1Start)
                }
                apiTestResults = apiTestResults + t1Res

                // Test 2: Schedule (schedule.mguu.ru)
                val t2Res = withContext(Dispatchers.IO) {
                    val t2Start = System.currentTimeMillis()
                    val (startStr, endStr) = com.mguuschedule.util.ScheduleParser.buildAcademicYearDateRange()
                    val encodedGroup = URLEncoder.encode(selectedGroupId ?: "", "UTF-8")
                    val schedUrl = "${com.mguuschedule.repository.ScheduleRepository.SCHEDULE_API_BASE}/groups/$encodedGroup/lessons/?start_date=$startStr&end_date=$endStr"
                    val schedRes = Jsoup.connect(schedUrl)
                        .ignoreContentType(true)
                        .header("Accept", "application/json")
                        .execute()
                        .body()
                    val t2End = System.currentTimeMillis()
                    val parsedLessons = com.mguuschedule.util.ScheduleParser.parse(schedRes)
                    ApiTestResult("GET schedule.mguu.ru/groups/{id}/lessons/", "OK • ${parsedLessons.size} пар • период=$startStr–$endStr", false, t2End - t2Start)
                }
                apiTestResults = apiTestResults + t2Res

                // Test 3: Rating Students (schedule.mguu.ru)
                val (t3Res, zachetkaFound, studentDetailPair) = withContext(Dispatchers.IO) {
                    val t3Start = System.currentTimeMillis()
                    val encodedGroup = URLEncoder.encode(selectedGroupId ?: "", "UTF-8")
                    val studRes = Jsoup.connect("${com.mguuschedule.repository.RatingRepository.RATING_API_BASE}/groups/$encodedGroup/")
                        .ignoreContentType(true)
                        .header("Accept", "application/json")
                        .execute()
                        .body()
                    val t3End = System.currentTimeMillis()
                    val studObj = JSONObject(studRes)
                    val studArr = studObj.optJSONArray("students")
                    val yearCode = studObj.optString("year", "000000033")
                    val semCode = studObj.optString("sem", "0")
                    var sid = ""
                    val found = studArr?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val it = arr.optJSONObject(i)
                            if (it?.optString("student_number") == selectedZachetka) {
                                sid = it.optString("student_code", "")
                                return@let true
                            }
                        }
                        false
                    } ?: false
                    val count = studArr?.length() ?: 0
                    Triple(
                        ApiTestResult(
                            "GET schedule.mguu.ru/rating/groups/{id}/",
                            "OK • $count студ. • Зачетка ${if (found) "найдена ($sid)" else "не найдена"}",
                            studArr == null,
                            t3End - t3Start
                        ),
                        found,
                        Triple(sid, yearCode, semCode)
                    )
                }
                apiTestResults = apiTestResults + t3Res

                // Test 4: Student Disciplines (schedule.mguu.ru)
                val (studentIdStr, yearCode, semCode) = studentDetailPair
                if (zachetkaFound && studentIdStr.isNotBlank()) {
                    val t4Res = withContext(Dispatchers.IO) {
                        val t4Start = System.currentTimeMillis()
                        val encStudent = URLEncoder.encode(studentIdStr, "UTF-8")
                        val detRes = Jsoup.connect("${com.mguuschedule.repository.RatingRepository.RATING_API_BASE}/students/$encStudent/?year=$yearCode&sem=$semCode")
                            .ignoreContentType(true)
                            .header("Accept", "application/json")
                            .execute()
                            .body()
                        val t4End = System.currentTimeMillis()
                        val detObj = JSONObject(detRes)
                        val discArr = detObj.optJSONArray("disciplines")
                        val ratingStr = detObj.optString("rating", "—")
                        ApiTestResult(
                            "GET schedule.mguu.ru/rating/students/{id}/",
                            "OK • ${discArr?.length() ?: 0} дисц. • рейтинг=$ratingStr",
                            discArr == null,
                            t4End - t4Start
                        )
                    }
                    apiTestResults = apiTestResults + t4Res
                }
            } catch (e: Exception) {
                AppLogger.e("TESTER", "Ошибка при выполнении тестов", e)
                apiTestResults = apiTestResults + ApiTestResult("Сбой сети/парсинга", e.message, true, null)
            } finally {
                isTestingApi = false
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text("Инструменты разработчика", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.lightTick()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                crashReport?.let { crashTrace ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.BugReport, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("ОТЧЕТ О ПОСЛЕДНЕМ КРАШЕ", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(rememberScrollState()),
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.12f),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = crashTrace,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(crashTrace))
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Копировать")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            CrashHandler.clearLastCrash(context)
                                            crashReport = null
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Сбросить")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Состояние данных и Кэша (Room vs API)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Group ID: $selectedGroupId", style = MaterialTheme.typography.bodySmall)
                            Text("Группа: $selectedGroupName", style = MaterialTheme.typography.bodySmall)
                            Text("Зачётка: $selectedZachetka", style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Расписание: ${syncDiagnostics.lastScheduleSource} (${syncDiagnostics.lastScheduleCount} пар)", style = MaterialTheme.typography.bodySmall)
                            Text("Версия расп.: ${syncDiagnostics.apiScheduleVersion}, Обновлено: ${syncDiagnostics.apiScheduleUpdatedAt}", style = MaterialTheme.typography.bodySmall)
                            Text("Рейтинг: ${syncDiagnostics.lastRatingSource}, Версия: ${syncDiagnostics.apiRatingVersion}", style = MaterialTheme.typography.bodySmall)
                            
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    haptic.click()
                                    scope.launch(Dispatchers.IO) {
                                        AppLogger.w("CACHE", "Локальный кэш Room принудительно очищен пользователем")
                                        val db = AppDatabase.getDatabase(context)
                                        db.scheduleDao().clearSchedule()
                                        db.ratingDao().clearAllRatingCache()
                                        withContext(Dispatchers.Main) {
                                            viewModel.loadGroups()
                                            viewModel.selectGroup(com.mguuschedule.model.Group(selectedGroupId ?: "", selectedGroupName ?: "", "", com.mguuschedule.model.EducationLevel.BACHELOR))
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Сбросить кэш БД и загрузить с API")
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Прямая проверка эндпоинтов schedule.mguu.ru и БРС", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { runApiTests() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isTestingApi
                            ) {
                                if (isTestingApi) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Запустить тест всех эндпоинтов API")
                            }

                            if (apiTestResults.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    apiTestResults.forEach { res ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (res.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Text(
                                                    text = if (res.isError) "ERROR" else "OK",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (res.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Column {
                                                Text(res.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                Text("${res.result ?: ""} ${if (res.timeMs != null) "• ${res.timeMs} мс" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Консоль приложения", style = MaterialTheme.typography.titleMedium)
                }

                item(key = "console_filters", contentType = "filters") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DEBUG_LOG_FILTERS.forEach { label ->
                            val selected = activeFilter == label
                            Surface(
                                onClick = { activeFilter = label },
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                item(key = "console_actions", contentType = "actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = {
                            val allLogs = logs.joinToString("\n") { "[${it.time}] ${it.tag} ${it.level}: ${it.message}" }
                            clipboardManager.setText(AnnotatedString(allLogs))
                        }) {
                            Icon(Icons.Rounded.ContentCopy, "Копировать все")
                        }
                        IconButton(onClick = { AppLogger.clear() }) {
                            Icon(Icons.Rounded.Delete, "Очистить")
                        }
                    }
                }

                item(key = "console_box", contentType = "console") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(
                                items = filteredLogs,
                                key = { idx, e -> "${idx}_${e.time}_${e.tag}_${e.message.hashCode()}" },
                                contentType = { _, _ -> "log" }
                            ) { _, entry ->
                                LogItem(
                                    entry = entry,
                                    onCopy = { text ->
                                        haptic.lightTick()
                                        clipboardManager.setText(AnnotatedString(text))
                                    }
                                )
                            }
                            if (filteredLogs.isEmpty()) {
                                item(key = "empty_logs", contentType = "empty") {
                                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Логи пусты", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        TopScrimProtection()
    }
}

@Composable
fun LogItem(
    entry: AppLogger.LogEntry,
    onCopy: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val containerColor = when {
        entry.level == "ERROR" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        entry.level == "WARN" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val contentColor = when {
        entry.level == "ERROR" -> MaterialTheme.colorScheme.error
        entry.level == "WARN" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val isApiTag = entry.tag == "API_RES" || entry.tag == "API_PARSER" || entry.tag == "API_REQ"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(4.dp))
            .clickable {
                onCopy("[${entry.time}] ${entry.tag} ${entry.level}: ${entry.message}")
                isExpanded = !isExpanded
            }
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.time,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            if (isApiTag) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            } else {
                Text(
                    text = entry.level,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}