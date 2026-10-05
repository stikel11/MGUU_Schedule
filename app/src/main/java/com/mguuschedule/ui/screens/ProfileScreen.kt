package com.mguuschedule.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mguuschedule.model.EducationLevel
import com.mguuschedule.model.Group
import com.mguuschedule.ui.components.CollapsibleScreenTitle
import com.mguuschedule.ui.components.StatusBarBlurOverlay
import com.mguuschedule.ui.components.StatusBarScrim
import com.mguuschedule.ui.components.TopScrimProtection
import com.mguuschedule.util.rememberHapticFeedback
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    scheduleViewModel: ScheduleViewModel,
    onNavigateToDebug: () -> Unit
) {
    val groupsUiState = viewModel.groupsUiState
    val zachetkasUiState = viewModel.zachetkasUiState
    val selectedGroup = viewModel.selectedGroup
    val selectedZachetka = viewModel.selectedZachetka
    val haptic = rememberHapticFeedback()

    val remindersEnabled = viewModel.remindersEnabled
    val reminderTime = viewModel.reminderTimeMinutes
    val changesEnabled = viewModel.changesEnabled
    val cacheDaysCount = viewModel.cacheDaysCount

    val themeMode = viewModel.themeMode
    val dynamicColorEnabled = viewModel.dynamicColorEnabled
    val shareCardStyle = viewModel.shareCardStyle

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showShareStyleDialog by remember { mutableStateOf(false) }

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
    var showCachePeriodSheet by remember { mutableStateOf(false) }
    var showReminderTimeSheet by remember { mutableStateOf(false) }

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

                // Section 1: Учебная группа
                item {
                    SettingsContainer(title = "Учебная группа") {
                        SettingsClickItem(
                            title = "Моя группа",
                            subtitle = selectedGroup?.name ?: "Не выбрана",
                            icon = Icons.Default.School,
                            onClick = {
                                haptic.lightTick()
                                showGroupSheet = true
                            }
                        )

                        SettingsClickItem(
                            title = "Номер зачетной книжки",
                            subtitle = if (selectedZachetka.isNotBlank()) selectedZachetka else "Нажмите, чтобы выбрать зачетку",
                            icon = Icons.Default.Badge,
                            onClick = {
                                haptic.lightTick()
                                viewModel.loadZachetkasForSelectedGroup()
                                showZachetkaSheet = true
                            }
                        )
                    }
                }

                // Section 2: Уведомления
                item {
                    SettingsContainer(title = "Уведомления") {
                        SettingsSwitchItem(
                            title = "Напоминания о парах",
                            subtitle = "Уведомление перед началом занятия",
                            icon = Icons.Default.NotificationsActive,
                            checked = remindersEnabled,
                            onCheckedChange = {
                                haptic.toggle(it)
                                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.updateRemindersEnabled(it)
                            }
                        )

                        val alpha = if (remindersEnabled) 1f else 0.38f
                        SettingsClickItem(
                            title = "Время до начала",
                            subtitle = "$reminderTime минут",
                            icon = Icons.Default.AccessTime,
                            enabled = remindersEnabled,
                            contentAlpha = alpha,
                            onClick = {
                                haptic.lightTick()
                                showReminderTimeSheet = true
                            }
                        )

                        SettingsSwitchItem(
                            title = "Оповещения об изменениях",
                            subtitle = "Проверка замен и переносов аудиторий",
                            icon = Icons.Default.Update,
                            checked = changesEnabled,
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
                            onCheckedChange = {
                                haptic.toggle(it)
                                viewModel.updateLiveUpdatesEnabled(it)
                            }
                        )
                    }
                }

                // Section 3: Внешний вид
                item {
                    SettingsContainer(title = "Внешний вид") {
                        val themeSubtitle = when (themeMode) {
                            1 -> "Светлая"
                            2 -> "Тёмная"
                            else -> "Системная"
                        }
                        SettingsClickItem(
                            title = "Тема оформления",
                            subtitle = themeSubtitle,
                            icon = Icons.Default.Palette,
                            onClick = {
                                haptic.lightTick()
                                showThemeDialog = true
                            }
                        )

                        val shareStyleSubtitle = when (shareCardStyle) {
                            1 -> "Тёмный (M3 Dark)"
                            2 -> "Светлый (M3 Light)"
                            3 -> "Чёрно-белый (Минимализм)"
                            else -> "Тематический (Material You)"
                        }
                        SettingsClickItem(
                            title = "Стиль постера расписания",
                            subtitle = shareStyleSubtitle,
                            icon = Icons.Default.Share,
                            onClick = {
                                haptic.lightTick()
                                showShareStyleDialog = true
                            }
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SettingsSwitchItem(
                                title = "Динамические цвета (Material You)",
                                subtitle = "Использовать цвета из обоев рабочего стола",
                                icon = Icons.Default.ColorLens,
                                checked = dynamicColorEnabled,
                                onCheckedChange = {
                                    haptic.toggle(it)
                                    viewModel.updateDynamicColorEnabled(it)
                                }
                            )
                        }
                    }
                }

                // Section 4: Данные и хранилище
                item {
                    SettingsContainer(title = "Данные и хранилище") {
                        SettingsClickItem(
                            title = "Период автокэширования",
                            subtitle = "$cacheDaysCount дней",
                            icon = Icons.Default.DateRange,
                            onClick = {
                                haptic.lightTick()
                                showCachePeriodSheet = true
                            }
                        )

                        SettingsClickItem(
                            title = "Очистить локальный кэш",
                            subtitle = "${storageState.statusText} • ${storageState.lastUpdated}",
                            icon = Icons.Default.DeleteSweep,
                            enabled = !isForcedLoading && storageState.lessonsCount > 0,
                            titleColor = MaterialTheme.colorScheme.error,
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = {
                                haptic.error()
                                viewModel.clearStorage(scheduleViewModel)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Кэш успешно очищен")
                                }
                            }
                        )
                    }
                }

                // Section 5: О приложении & Отладка
                item {
                    SettingsContainer(title = "О приложении") {
                        SettingsClickItem(
                            title = "МГУУ Расписание",
                            subtitle = "Версия ${com.mguuschedule.BuildConfig.VERSION_NAME} beta • Material 3 Expressive",
                            icon = Icons.Default.Info,
                            onClick = {}
                        )

                        SettingsClickItem(
                            title = "Панель отладки",
                            subtitle = "Инструменты тестирования и логи",
                            icon = Icons.Default.BugReport,
                            onClick = {
                                haptic.lightTick()
                                onNavigateToDebug()
                            }
                        )
                    }
                }
            }
        }

        // Переиспользуемая матовая блюр-полоса в зоне статус-бара
        StatusBarBlurOverlay(hazeState = hazeState)

        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentMode = themeMode,
                onModeSelected = {
                    haptic.selection()
                    viewModel.updateThemeMode(it)
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false }
            )
        }

        if (showShareStyleDialog) {
            ShareStyleSelectionDialog(
                currentStyle = shareCardStyle,
                onStyleSelected = {
                    haptic.selection()
                    viewModel.updateShareCardStyle(it)
                    showShareStyleDialog = false
                },
                onDismiss = { showShareStyleDialog = false }
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

        if (showCachePeriodSheet) {
            CachePeriodBottomSheet(
                currentValue = cacheDaysCount,
                onValueSelected = {
                    haptic.selection()
                    viewModel.updateCacheDaysCount(it)
                    showCachePeriodSheet = false
                },
                onDismiss = { showCachePeriodSheet = false }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                TextField(
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
                    shape = CircleShape,
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )

                Box(modifier = Modifier.heightIn(max = 380.dp, min = 160.dp)) {
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

                        is ZachetkasUiState.Success -> {
                            val filtered = remember(uiState.zachetkas, searchQuery) {
                                uiState.zachetkas.filter { it.contains(searchQuery, ignoreCase = true) }
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
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
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
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
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
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingsClickItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean = true,
    contentAlpha: Float = 1f,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint.copy(alpha = contentAlpha),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
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

@Composable
fun ThemeSelectionDialog(
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Тема оформления", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ThemeOptionRow("Системная", currentMode == 0) { onModeSelected(0) }
                ThemeOptionRow("Светлая", currentMode == 1) { onModeSelected(1) }
                ThemeOptionRow("Тёмная", currentMode == 2) { onModeSelected(2) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun ShareStyleSelectionDialog(
    currentStyle: Int,
    onStyleSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Стиль постера расписания", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ThemeOptionRow("Тематический (Material You)", currentStyle == 0) { onStyleSelected(0) }
                ThemeOptionRow("Тёмный (M3 Dark)", currentStyle == 1) { onStyleSelected(1) }
                ThemeOptionRow("Светлый (M3 Light)", currentStyle == 2) { onStyleSelected(2) }
                ThemeOptionRow("Чёрно-белый (Минимализм)", currentStyle == 3) { onStyleSelected(3) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun ThemeOptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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

            TextField(
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
                shape = CircleShape,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
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
fun CachePeriodBottomSheet(
    currentValue: Int,
    onValueSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(7, 14, 30, 60, 90)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Период кэширования",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            options.forEach { days ->
                val isSelected = days == currentValue
                Surface(
                    onClick = { onValueSelected(days) },
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
                            text = "$days дней",
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
