package com.mguuschedule.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mguuschedule.model.Group
import com.mguuschedule.util.rememberHapticFeedback
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel,
    onNavigateToDebug: () -> Unit = {}
) {
    var showGroupSheet by remember { mutableStateOf(false) }
    var showCachePeriodSheet by remember { mutableStateOf(false) }
    var showReminderTimeSheet by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = rememberHapticFeedback()
    
    val groupsUiState = viewModel.groupsUiState
    val selectedGroup = viewModel.selectedGroup
    
    val remindersEnabled = viewModel.remindersEnabled
    val reminderTime = viewModel.reminderTimeMinutes
    val changesEnabled = viewModel.changesEnabled
    val cacheDaysCount = viewModel.cacheDaysCount
    
    val themeMode = viewModel.themeMode
    val dynamicColorEnabled = viewModel.dynamicColorEnabled
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 88.dp)
            ) { data ->
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Профиль и настройки",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hero Profile Card
                item {
                    ProfileHeaderCard(selectedGroup = selectedGroup)
                }

                // Category 1: Notifications Container
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
                    }
                }

                // Category 2: Appearance Container
                item {
                    SettingsContainer(title = "Внешний вид") {
                        val themeLabel = when (themeMode) {
                            1 -> "Светлая"
                            2 -> "Темная"
                            else -> "Системная"
                        }
                        SettingsClickItem(
                            title = "Тема оформления",
                            subtitle = themeLabel,
                            icon = Icons.Default.Brightness4,
                            onClick = { 
                                haptic.lightTick()
                                showThemeDialog = true 
                            }
                        )

                        SettingsSwitchItem(
                            title = "Динамические цвета",
                            subtitle = "Палитра на основе обоев системы (Material You)",
                            icon = Icons.Default.Palette,
                            checked = dynamicColorEnabled,
                            onCheckedChange = { 
                                haptic.toggle(it)
                                viewModel.updateDynamicColorEnabled(it) 
                            }
                        )
                    }
                }

                // Category 3: Account Container
                item {
                    SettingsContainer(title = "Аккаунт") {
                        SettingsClickItem(
                            title = "Моя группа",
                            subtitle = selectedGroup?.name ?: "Не выбрана",
                            icon = Icons.Default.Group,
                            onClick = { 
                                haptic.lightTick()
                                showGroupSheet = true 
                            }
                        )

                        SettingsClickItem(
                            title = "Выйти из аккаунта",
                            icon = Icons.AutoMirrored.Filled.Logout,
                            textColor = MaterialTheme.colorScheme.error,
                            iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                            iconColor = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = {
                                haptic.click()
                                showLogoutDialog = true
                            }
                        )
                    }
                }

                // Category 4: Storage & Cache Container
                item {
                    SettingsContainer(title = "Хранилище и кэширование") {
                        SettingsClickItem(
                            title = "Период кэширования",
                            subtitle = "$cacheDaysCount дней",
                            icon = Icons.Default.History,
                            onClick = {
                                haptic.lightTick()
                                showCachePeriodSheet = true
                            }
                        )

                        DebugCacheSection(
                            storageState = storageState,
                            isLoading = isForcedLoading,
                            onForceUpdate = { 
                                haptic.click()
                                scheduleViewModel.forceUpdate() 
                            },
                            onClearCache = { 
                                haptic.success()
                                viewModel.clearStorage(scheduleViewModel)
                            }
                        )
                    }
                }

                // Category 5: About App Container
                item {
                    SettingsContainer(title = "О приложении") {
                        AboutSection(
                            onNavigateToDebug = onNavigateToDebug,
                            onShowSnackbar = { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Выход из аккаунта", fontWeight = FontWeight.Bold) },
                text = { Text("Вы уверены, что хотите выйти? Данные о выбранной группе будут сброшены.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            haptic.click()
                            showLogoutDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Выйти", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

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

        if (showGroupSheet) {
            GroupSelectionSheet(
                uiState = groupsUiState,
                sheetState = sheetState,
                selectedGroup = selectedGroup,
                onGroupSelected = {
                    haptic.success()
                    viewModel.selectGroup(it)
                    showGroupSheet = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Группа ${it.name} выбрана")
                    }
                },
                onDismiss = { showGroupSheet = false }
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

// Hero Profile Card
@Composable
fun ProfileHeaderCard(selectedGroup: Group?) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "ИИ",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Иван Иванов", 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = selectedGroup?.let { "Группа: ${it.name}" } ?: "Студент, 3 курс",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Container for settings items (Squarcle)
@Composable
fun SettingsContainer(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            )
            content()
        }
    }
}

// Pixel Settings Item with Switch
@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!checked) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pixel Settings Circle Icon Background
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it, 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

// Pixel Settings Item with Click
@Composable
fun SettingsClickItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    enabled: Boolean = true,
    contentAlpha: Float = 1f,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pixel Settings Circle Icon Background
        Surface(
            shape = CircleShape,
            color = iconContainerColor.copy(alpha = if (enabled) 1f else contentAlpha),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = iconColor.copy(alpha = if (enabled) 1f else contentAlpha),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = if (enabled) 1f else contentAlpha)
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it, 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else contentAlpha)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Icon(
            imageVector = Icons.Default.ChevronRight, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachePeriodBottomSheet(
    currentValue: Int,
    onValueSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        7 to "7 дней",
        14 to "14 дней",
        30 to "30 дней",
        60 to "60 дней",
        180 to "До конца семестра"
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp, start = 12.dp, end = 12.dp)) {
            Text(
                text = "Период кэширования",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            options.forEach { (days, label) ->
                val isSelected = days == currentValue
                Surface(
                    onClick = { onValueSelected(days) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        trailingContent = {
                            RadioButton(selected = isSelected, onClick = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
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
    val options = listOf(
        5 to "За 5 минут",
        10 to "За 10 минут",
        15 to "За 15 минут",
        30 to "За 30 минут",
        60 to "За 1 час"
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp, start = 12.dp, end = 12.dp)) {
            Text(
                text = "Напоминание о парах",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            options.forEach { (mins, label) ->
                val isSelected = mins == currentValue
                Surface(
                    onClick = { onValueSelected(mins) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        trailingContent = {
                            RadioButton(selected = isSelected, onClick = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
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
                ThemeOption("Системная (по умолчанию)", 0, currentMode, onModeSelected)
                ThemeOption("Светлая", 1, currentMode, onModeSelected)
                ThemeOption("Темная", 2, currentMode, onModeSelected)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
fun ThemeOption(label: String, mode: Int, currentMode: Int, onSelect: (Int) -> Unit) {
    Surface(
        onClick = { onSelect(mode) },
        shape = RoundedCornerShape(16.dp),
        color = if (mode == currentMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = mode == currentMode, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal)
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
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                        val filteredGroups = remember(uiState.groups, searchQuery) {
                            uiState.groups.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                        val groupedGroups = remember(filteredGroups) {
                            filteredGroups.groupBy { it.course }
                        }
                        val expandedCourses = remember { mutableStateMapOf<String, Boolean>() }
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            groupedGroups.forEach { (course, groups) ->
                                val isSearching = searchQuery.isNotEmpty()
                                val isExpanded = expandedCourses[course] ?: isSearching
                                
                                item(key = course) {
                                    Surface(
                                        onClick = { 
                                            haptic.lightTick()
                                            expandedCourses[course] = !isExpanded 
                                        },
                                        color = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                                        shape = CircleShape
                                    ) {
                                        ListItem(
                                            headlineContent = { Text(course, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                            trailingContent = { Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                                if (isExpanded) {
                                    items(groups, key = { it.id }) { group ->
                                        val isSelected = group.id == selectedGroup?.id
                                        Surface(
                                            onClick = { onGroupSelected(group) },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            shape = CircleShape
                                        ) {
                                            ListItem(
                                                headlineContent = { 
                                                    Text(
                                                        group.name, 
                                                        modifier = Modifier.padding(start = 12.dp),
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    ) 
                                                },
                                                leadingContent = { 
                                                    Icon(
                                                        Icons.Default.School, 
                                                        null, 
                                                        modifier = Modifier.size(20.dp),
                                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    ) 
                                                },
                                                trailingContent = {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
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

@Composable
fun DebugCacheSection(
    storageState: StorageUiState,
    isLoading: Boolean,
    onForceUpdate: () -> Unit,
    onClearCache: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Состояние хранилища",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val statusColor = if (storageState.lessonsCount > 0) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        }
        
        val sizeText = if (storageState.lessonsCount > 0) {
            "${storageState.sizeBytes / 1024} КБ (${storageState.lessonsCount} пар)"
        } else {
            "0 КБ (0 пар)"
        }

        DebugInfoRow("Статус", storageState.statusText, statusColor)
        DebugInfoRow("Занятия", sizeText)
        DebugInfoRow("Период", "${storageState.periodDays} дней")
        DebugInfoRow("Обновлено", storageState.lastUpdated)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onForceUpdate,
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
                shape = CircleShape,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Обновить", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            
            FilledTonalButton(
                onClick = onClearCache,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Очистить", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DebugInfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun AboutSection(
    onNavigateToDebug: () -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val haptic = rememberHapticFeedback()
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Версия 0.1 beta",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 1500) {
                    tapCount++
                } else {
                    tapCount = 1
                }
                lastTapTime = now

                if (tapCount in 3..4) {
                    onShowSnackbar("Вы в ${5 - tapCount} шагах от режима разработчика")
                } else if (tapCount >= 5) {
                    haptic.success()
                    onNavigateToDebug()
                    tapCount = 0
                }
            }
        )
        Spacer(modifier = Modifier.height(2.dp))
        TextButton(onClick = { /* Link to repository */ }) {
            Text("Ссылка на репозиторий", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
