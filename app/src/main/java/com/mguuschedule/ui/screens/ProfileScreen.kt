package com.mguuschedule.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mguuschedule.model.EducationLevel
import com.mguuschedule.model.Group
import com.mguuschedule.ui.components.CollapsibleScreenTitle
import com.mguuschedule.ui.components.StatusBarBlurOverlay
import com.mguuschedule.util.rememberHapticFeedback
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun getItemShape(index: Int, total: Int): Shape {
    if (total <= 1) return RoundedCornerShape(24.dp)
    return when (index) {
        0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        total - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    scheduleViewModel: ScheduleViewModel,
    onNavigateToDebug: () -> Unit = {},
    onOpenFeatureTour: () -> Unit = {}
) {
    val groupsUiState = viewModel.groupsUiState
    val zachetkasUiState = viewModel.zachetkasUiState
    val selectedGroup = viewModel.selectedGroup
    val selectedZachetka = viewModel.selectedZachetka
    val haptic = rememberHapticFeedback()

    val remindersEnabled = viewModel.remindersEnabled
    val reminderTime = viewModel.reminderTimeMinutes
    val changesEnabled = viewModel.changesEnabled

    val themeMode = viewModel.themeMode
    val dynamicColorEnabled = viewModel.dynamicColorEnabled
    val shareCardStyle = viewModel.shareCardStyle

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showThemeSheet by remember { mutableStateOf(false) }
    var showShareStyleSheet by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            viewModel.updateRemindersEnabled(false)
            viewModel.updateChangesEnabled(false)
        }
    }

    val isForcedLoading = scheduleViewModel.isForcedLoading
    val storageState by viewModel.storageState.collectAsState()

    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()

    val scrollToTopTrigger by viewModel.scrollToTopTrigger.collectAsState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0 && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
            listState.animateScrollToItem(0)
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zachetkaSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showGroupSheet by remember { mutableStateOf(false) }
    var showZachetkaSheet by remember { mutableStateOf(false) }
    var showReminderTimeSheet by remember { mutableStateOf(false) }
    var showClearScheduleDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        modifier = Modifier
                            .padding(12.dp)
                            .clip(CircleShape),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        action = data.visuals.actionLabel?.let {
                            { TextButton(onClick = { data.performAction() }) { Text(it, fontWeight = FontWeight.Bold) } }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(data.visuals.message, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    bottom = innerPadding.calculateBottomPadding() + 100.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    CollapsibleScreenTitle(title = "Настройки")
                }

                // Секция 1: Профиль студента
                item {
                    SettingsContainer(title = "Профиль студента") {
                        SettingsClickItem(
                            title = "Моя группа",
                            subtitle = selectedGroup?.name ?: "Не выбрана",
                            icon = Icons.Default.School,
                            shape = getItemShape(0, 2),
                            onClick = {
                                haptic.lightTick()
                                showGroupSheet = true
                            }
                        )

                        SettingsClickItem(
                            title = "Номер зачетной книжки",
                            subtitle = if (selectedZachetka.isNotBlank()) selectedZachetka else "Нажмите, чтобы выбрать зачетку",
                            icon = Icons.Default.Badge,
                            shape = getItemShape(1, 2),
                            onClick = {
                                haptic.lightTick()
                                viewModel.loadZachetkasForSelectedGroup()
                                showZachetkaSheet = true
                            }
                        )
                    }
                }

                // Секция 2: Уведомления
                item {
                    val notifTotalCount = if (remindersEnabled) 4 else 3
                    SettingsContainer(title = "Уведомления") {
                        SettingsSwitchItem(
                            title = "Напоминания о парах",
                            subtitle = "Уведомление перед началом занятия",
                            icon = Icons.Default.NotificationsActive,
                            checked = remindersEnabled,
                            shape = getItemShape(0, notifTotalCount),
                            onCheckedChange = {
                                haptic.toggle(it)
                                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.updateRemindersEnabled(it)
                            }
                        )

                        AnimatedVisibility(
                            visible = remindersEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsClickItem(
                                title = "Время до начала",
                                subtitle = "$reminderTime минут",
                                icon = Icons.Default.AccessTime,
                                shape = getItemShape(1, 4),
                                onClick = {
                                    haptic.lightTick()
                                    showReminderTimeSheet = true
                                }
                            )
                        }

                        SettingsSwitchItem(
                            title = "Оповещения об изменениях",
                            subtitle = "Проверка замен и переносов аудиторий",
                            icon = Icons.Default.Update,
                            checked = changesEnabled,
                            shape = getItemShape(if (remindersEnabled) 2 else 1, notifTotalCount),
                            onCheckedChange = {
                                haptic.toggle(it)
                                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.updateChangesEnabled(it)
                            }
                        )

                        SettingsSwitchItem(
                            title = "Live Updates",
                            subtitle = "Интерактивное уведомление с таймером во время пары",
                            icon = Icons.Default.Timer,
                            checked = viewModel.liveUpdatesEnabled,
                            shape = getItemShape(if (remindersEnabled) 3 else 2, notifTotalCount),
                            onCheckedChange = {
                                haptic.toggle(it)
                                viewModel.updateLiveUpdatesEnabled(it)
                            }
                        )
                    }
                }

                // Секция 3: Внешний вид
                item {
                    val appearanceTotal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 3 else 2
                    val themeSubtitle = when (themeMode) {
                        1 -> "Светлая"
                        2 -> "Тёмная"
                        else -> "Системная"
                    }
                    val shareStyleSubtitle = when (shareCardStyle) {
                        1 -> "Тёмный (M3 Dark)"
                        2 -> "Светлый (M3 Light)"
                        3 -> "Чёрно-белый (Минимализм)"
                        else -> "Тематический (Material You)"
                    }

                    SettingsContainer(title = "Внешний вид") {
                        SettingsClickItem(
                            title = "Тема оформления",
                            subtitle = themeSubtitle,
                            icon = Icons.Default.Palette,
                            shape = getItemShape(0, appearanceTotal),
                            onClick = {
                                haptic.lightTick()
                                showThemeSheet = true
                            }
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SettingsSwitchItem(
                                title = "Динамические цвета (Material You)",
                                subtitle = "Использовать цвета из обоев рабочего стола",
                                icon = Icons.Default.ColorLens,
                                checked = dynamicColorEnabled,
                                shape = getItemShape(1, appearanceTotal),
                                onCheckedChange = {
                                    haptic.toggle(it)
                                    viewModel.updateDynamicColorEnabled(it)
                                }
                            )
                        }

                        SettingsClickItem(
                            title = "Стиль постера расписания",
                            subtitle = shareStyleSubtitle,
                            icon = Icons.Default.Share,
                            shape = getItemShape(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 2 else 1, appearanceTotal),
                            onClick = {
                                haptic.lightTick()
                                showShareStyleSheet = true
                            }
                        )
                    }
                }

                // Секция 4: Данные и память
                item {
                    SettingsContainer(title = "Данные и память") {
                        SettingsClickItem(
                            title = "Расписание",
                            subtitle = if (isForcedLoading) "Обновление расписания..." else "${storageState.statusText} • Обновлено: ${storageState.lastUpdated}",
                            icon = Icons.Default.Sync,
                            enabled = !isForcedLoading,
                            shape = getItemShape(0, 2),
                            onClick = {
                                haptic.click()
                                scheduleViewModel.refreshSchedule()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Запущено обновление расписания")
                                }
                            }
                        )

                        SettingsClickItem(
                            title = "Очистить данные расписания",
                            subtitle = "Удалит только сохраненные пары расписания",
                            icon = Icons.Default.DeleteSweep,
                            enabled = !isForcedLoading && storageState.lessonsCount > 0,
                            isDestructive = true,
                            shape = getItemShape(1, 2),
                            onClick = {
                                haptic.error()
                                showClearScheduleDialog = true
                            }
                        )
                    }
                }

                // Секция 5: О приложении
                item {
                    val updateState = viewModel.updateState
                    val context = LocalContext.current
                    var versionTapCount by remember { mutableIntStateOf(0) }
                    var lastVersionTapTime by remember { mutableLongStateOf(0L) }

                    val updateSubtitle = when (updateState) {
                        is UpdateUiState.Idle -> "Нажмите для проверки наличия обновлений"
                        is UpdateUiState.Checking -> "Проверка наличия обновлений..."
                        is UpdateUiState.NoUpdate -> "У вас установлена последняя версия"
                        is UpdateUiState.UpdateAvailable -> "Доступна версия ${updateState.release.tagName}! Нажмите для скачивания."
                        is UpdateUiState.Downloading -> "Загрузка: ${updateState.progress}%"
                        is UpdateUiState.Downloaded -> "Скачано. Нажмите для установки."
                        is UpdateUiState.Error -> "Ошибка: ${updateState.message} • Нажмите для повтора"
                    }

                    val updateIcon = when (updateState) {
                        is UpdateUiState.UpdateAvailable, is UpdateUiState.Downloaded -> Icons.Default.DownloadForOffline
                        is UpdateUiState.Downloading -> Icons.Default.CloudDownload
                        is UpdateUiState.Error -> Icons.Default.ErrorOutline
                        else -> Icons.Default.SystemUpdate
                    }

                    SettingsContainer(title = "О приложении") {
                        // 0: Обновления
                        SettingsClickItem(
                            title = if (updateState is UpdateUiState.UpdateAvailable) "Скачать обновление" else "Проверить обновления",
                            subtitle = updateSubtitle,
                            icon = updateIcon,
                            enabled = updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading,
                            shape = getItemShape(0, 5),
                            onClick = {
                                haptic.click()
                                when (updateState) {
                                    is UpdateUiState.UpdateAvailable -> {
                                        viewModel.downloadAndInstallUpdate(context, updateState.release.apkDownloadUrl)
                                    }
                                    is UpdateUiState.Downloaded -> {
                                        com.mguuschedule.util.UpdateManager.installApk(context, updateState.file)
                                    }
                                    else -> {
                                        viewModel.checkForUpdates()
                                    }
                                }
                            }
                        )

                        // 1: Интерактивное мини-обучение
                        SettingsClickItem(
                            title = "Возможности приложения",
                            subtitle = "Показать интерактивное мини-обучение",
                            icon = Icons.Default.AutoAwesome,
                            shape = getItemShape(1, 5),
                            onClick = {
                                haptic.lightTick()
                                onOpenFeatureTour()
                            }
                        )

                        // 2: Версия
                        SettingsClickItem(
                            title = "МГУУ Расписание",
                            subtitle = "Версия ${com.mguuschedule.BuildConfig.VERSION_NAME} beta",
                            icon = Icons.Default.Info,
                            shape = getItemShape(2, 5),
                            onClick = {
                                val now = System.currentTimeMillis()
                                if (now - lastVersionTapTime > 2000L) {
                                    versionTapCount = 1
                                } else {
                                    versionTapCount++
                                }
                                lastVersionTapTime = now

                                if (versionTapCount >= 5) {
                                    haptic.success()
                                    versionTapCount = 0
                                    if (!viewModel.isDeveloperMode) {
                                        viewModel.enableDeveloperMode()
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Режим разработчика включён")
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Режим разработчика уже включён")
                                        }
                                    }
                                } else {
                                    haptic.lightTick()
                                }
                            }
                        )

                        // 3: GitHub
                        SettingsClickItem(
                            title = "GitHub",
                            subtitle = "Исходный код проекта",
                            icon = Icons.Default.Code,
                            shape = getItemShape(3, 5),
                            onClick = {
                                haptic.click()
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/stikel11/MGUU_Schedule"))
                                    context.startActivity(intent)
                                }.onFailure {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Не удалось открыть браузер")
                                    }
                                }
                            }
                        )

                        // 4: Обратная связь
                        SettingsClickItem(
                            title = "Обратная связь",
                            subtitle = "Сообщить о проблеме или связаться с разработчиком",
                            icon = Icons.Default.Feedback,
                            shape = getItemShape(4, 5),
                            onClick = {
                                haptic.click()
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/stikel11/MGUU_Schedule/issues"))
                                    context.startActivity(intent)
                                }.onFailure {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Не удалось открыть ссылку")
                                    }
                                }
                            }
                        )
                    }
                }

                // Секция 6: Для разработчиков
                if (viewModel.isDeveloperMode) {
                    item {
                        SettingsContainer(title = "Для разработчиков") {
                            SettingsClickItem(
                                title = "Панель отладки",
                                subtitle = "Инструменты тестирования и логи",
                                icon = Icons.Default.BugReport,
                                shape = getItemShape(0, 1),
                                onClick = {
                                    haptic.lightTick()
                                    onNavigateToDebug()
                                }
                            )
                        }
                    }
                }
            }
        }

        StatusBarBlurOverlay()

        if (showThemeSheet) {
            ThemeSelectionSheet(
                currentMode = themeMode,
                onModeSelected = {
                    haptic.selection()
                    viewModel.updateThemeMode(it)
                    showThemeSheet = false
                },
                onDismiss = { showThemeSheet = false }
            )
        }

        if (showShareStyleSheet) {
            ShareStyleSelectionSheet(
                currentStyle = shareCardStyle,
                onStyleSelected = {
                    haptic.selection()
                    viewModel.updateShareCardStyle(it)
                    showShareStyleSheet = false
                },
                onDismiss = { showShareStyleSheet = false }
            )
        }

        if (showClearScheduleDialog) {
            AlertDialog(
                onDismissRequest = { showClearScheduleDialog = false },
                title = { Text("Очистить данные расписания?", fontWeight = FontWeight.Bold) },
                text = { Text("Это удалит локально сохраненные пары расписания. Ваша группа, зачетка, заметки и настройки затронуты не будут.") },
                confirmButton = {
                    TextButton(onClick = {
                        haptic.error()
                        viewModel.clearStorage(scheduleViewModel)
                        showClearScheduleDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Данные расписания очищены")
                        }
                    }) {
                        Text("Очистить", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearScheduleDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        if (showGroupSheet) {
            GroupSelectionSheet(
                uiState = groupsUiState,
                sheetState = sheetState,
                selectedGroup = selectedGroup,
                onGroupSelected = {
                    haptic.success()
                    viewModel.selectGroup(it)
                    scheduleViewModel.loadSchedule(it.id, forceRefresh = true)
                    showGroupSheet = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Группа ${it.name} выбрана")
                    }
                },
                onDismiss = { showGroupSheet = false }
            )
        }

        if (showZachetkaSheet) {
            ZachetkaSelectionSheet(
                uiState = zachetkasUiState,
                sheetState = zachetkaSheetState,
                selectedZachetka = selectedZachetka,
                groupName = selectedGroup?.name,
                onZachetkaSelected = { zachetka ->
                    haptic.success()
                    viewModel.updateSelectedZachetka(zachetka)
                    showZachetkaSheet = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Зачетка $zachetka выбрана")
                    }
                },
                onRetry = { viewModel.loadZachetkasForSelectedGroup() },
                onDismiss = { showZachetkaSheet = false }
            )
        }

        if (showReminderTimeSheet) {
            ReminderTimeBottomSheet(
                currentValue = reminderTime,
                onValueSelected = {
                    haptic.selection()
                    viewModel.updateReminderTime(it)
                    showReminderTimeSheet = false
                },
                onDismiss = { showReminderTimeSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZachetkaSelectionSheet(
    uiState: ZachetkasUiState,
    sheetState: SheetState,
    selectedZachetka: String,
    groupName: String?,
    onZachetkaSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf(selectedZachetka) }
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mguu_prefs", Context.MODE_PRIVATE) }

    // Проверка мгновенного кэша зачеток из SharedPreferences
    val cachedZachetkas = remember(groupName, uiState) {
        if (uiState is ZachetkasUiState.Success) {
            uiState.zachetkas
        } else {
            val groupId = prefs.getString("selected_group_id", null)
            val json = if (!groupId.isNullOrEmpty()) prefs.getString("rating_latest_instant_cache_$groupId", null) else null
            if (!json.isNullOrEmpty()) {
                runCatching {
                    val jsonObject = JSONObject(json)
                    val studentsArray = jsonObject.optJSONArray("students")
                    if (studentsArray != null) {
                        val list = mutableListOf<String>()
                        for (i in 0 until studentsArray.length()) {
                            val st = studentsArray.optJSONObject(i)
                            val z = st?.optString("zachetka")
                            if (!z.isNullOrBlank()) {
                                list.add(z)
                            }
                        }
                        list
                    } else emptyList()
                }.getOrDefault(emptyList())
            } else emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 18.dp)
        ) {
            Text(
                text = "Номер зачетной книжки",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (!groupName.isNullOrBlank()) {
                Text(
                    text = "Группа $groupName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!showManualInput) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    placeholder = { Text("Поиск зачетки...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить")
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Box(modifier = Modifier.heightIn(max = 380.dp, min = 160.dp)) {
                    val currentList = if (uiState is ZachetkasUiState.Success) uiState.zachetkas else cachedZachetkas

                    if (currentList.isNotEmpty()) {
                        val filtered = remember(currentList, searchQuery) {
                            currentList.filter { it.contains(searchQuery, ignoreCase = true) }
                        }

                        if (filtered.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Зачетки не найдены", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(filtered, key = { it }) { zachetka ->
                                    val isSelected = zachetka.equals(selectedZachetka, ignoreCase = true)

                                    Surface(
                                        onClick = {
                                            haptic.click()
                                            onZachetkaSelected(zachetka)
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Badge,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = zachetka,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        when (uiState) {
                            is ZachetkasUiState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Загрузка списка зачеток...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }

                            is ZachetkasUiState.Error -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        uiState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = onRetry) { Text("Повторить") }
                                        OutlinedButton(onClick = { showManualInput = true }) { Text("Ввести вручную") }
                                    }
                                }
                            }

                            else -> {}
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showManualInput = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text("Ввести номер вручную", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    label = { Text("Номер зачетной книжки") },
                    placeholder = { Text("Например: МУГКП-2516о") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showManualInput = false }) { Text("Назад к списку") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualText.isNotBlank()) {
                                haptic.click()
                                onZachetkaSelected(manualText)
                            }
                        }
                    ) { Text("Сохранить") }
                }
            }
        }
    }
}

@Composable
fun SettingsContainer(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
fun SettingsClickItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    shape: Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    contentAlpha: Float = 1f,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val iconBgColor = if (isDestructive) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val iconTint = if (isDestructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    val titleColor = if (isDestructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor.copy(alpha = contentAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint.copy(alpha = contentAlpha),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor.copy(alpha = contentAlpha)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionSheet(
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        0 to "Системная",
        1 to "Светлая",
        2 to "Тёмная"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Тема оформления",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            options.forEach { (mode, label) ->
                val isSelected = mode == currentMode
                Surface(
                    onClick = { onModeSelected(mode) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareStyleSelectionSheet(
    currentStyle: Int,
    onStyleSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        0 to "Тематический (Material You)",
        1 to "Тёмный (M3 Dark)",
        2 to "Светлый (M3 Light)",
        3 to "Чёрно-белый (Минимализм)"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Стиль постера расписания",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            options.forEach { (style, label) ->
                val isSelected = style == currentStyle
                Surface(
                    onClick = { onStyleSelected(style) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSelectionSheet(
    uiState: GroupsUiState,
    sheetState: SheetState,
    selectedGroup: Group?,
    onGroupSelected: (Group) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val haptic = rememberHapticFeedback()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 18.dp)
        ) {
            Text(
                text = "Выберите учебную группу",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            var selectedLevelTab by remember {
                mutableStateOf(selectedGroup?.level ?: EducationLevel.BACHELOR)
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                SegmentedButton(
                    selected = selectedLevelTab == EducationLevel.BACHELOR,
                    onClick = {
                        haptic.selection()
                        selectedLevelTab = EducationLevel.BACHELOR
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Бакалавриат", fontWeight = FontWeight.Bold)
                }
                SegmentedButton(
                    selected = selectedLevelTab == EducationLevel.MASTER,
                    onClick = {
                        haptic.selection()
                        selectedLevelTab = EducationLevel.MASTER
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Магистратура", fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                placeholder = { Text("Поиск группы...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Box(modifier = Modifier.heightIn(max = 500.dp)) {
                when (uiState) {
                    is GroupsUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    is GroupsUiState.Error -> Text("Ошибка загрузки групп", modifier = Modifier.align(Alignment.Center))
                    is GroupsUiState.Success -> {
                        val filteredGroups = remember(uiState.groups, selectedLevelTab, searchQuery) {
                            uiState.groups
                                .filter { it.level == selectedLevelTab }
                                .filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                        val groupedGroups = remember(filteredGroups) {
                            filteredGroups.groupBy { it.course }
                        }
                        val expandedCourses = remember { mutableStateMapOf<String, Boolean>() }

                        if (filteredGroups.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Группы не найдены", style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 32.dp)
                            ) {
                                groupedGroups.forEach { (course, groups) ->
                                    val isExpanded = expandedCourses[course] ?: (searchQuery.isNotEmpty())

                                    item(key = course) {
                                        Surface(
                                            onClick = {
                                                haptic.lightTick()
                                                expandedCourses[course] = !(expandedCourses[course] ?: (searchQuery.isNotEmpty()))
                                            },
                                            color = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = course,
                                                    modifier = Modifier.weight(1f),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    }

                                    if (isExpanded) {
                                        items(groups, key = { it.id }) { group ->
                                            val isSelected = selectedGroup?.id == group.id
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        haptic.success()
                                                        onGroupSelected(group)
                                                    },
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                                                )
                                            ) {
                                                ListItem(
                                                    headlineContent = {
                                                        Text(
                                                            group.name,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                    },
                                                    leadingContent = {
                                                        Icon(
                                                            Icons.Default.Group,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(24.dp),
                                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                                        )
                                                    },
                                                    trailingContent = {
                                                        if (isSelected) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                            )
                                                        }
                                                    },
                                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeBottomSheet(
    currentValue: Int,
    onValueSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(5, 10, 15, 30, 60)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Напомнить о паре за",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            options.forEach { mins ->
                val isSelected = mins == currentValue
                Surface(
                    onClick = { onValueSelected(mins) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "$mins минут",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}