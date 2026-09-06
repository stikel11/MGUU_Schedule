package com.mguuschedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mguuschedule.ui.components.TopScrimProtection
import com.mguuschedule.util.AppLogger
import com.mguuschedule.util.NotificationHelper
import com.mguuschedule.util.CrashHandler
import com.mguuschedule.util.rememberHapticFeedback
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.app.NotificationManagerCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: ProfileViewModel = viewModel(),
    onBack: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    val logs by AppLogger.logs.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lastCrash = remember { CrashHandler.getLastCrash(context) }
    var crashReport by remember { mutableStateOf(lastCrash) }

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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(Icons.Default.BugReport, null)
                                Spacer(Modifier.width(8.dp))
                                Text("ОТЧЕТ О ПОСЛЕДНЕМ КРАШЕ", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                                color = Color.Black.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
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
                Text("Тестирование уведомлений", style = MaterialTheme.typography.titleMedium)
            }
            
            item {
                OutlinedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Системные пуши", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { NotificationHelper.triggerInstantTestPush(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Принудительный тест пуша")
                        }
                    }
                }
            }

            item {
                OutlinedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Live Updates (Таймеры)", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { NotificationHelper.triggerLiveUpdateNotification(context) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Запустить Live Update (пилюля)")
                            }
                        }
                        TextButton(
                            onClick = { NotificationManagerCompat.from(context).cancel(9992) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Остановить Live Update", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                OutlinedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Другие уведомления", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.testScheduleChange() }, modifier = Modifier.weight(1f)) {
                                Text("Замена")
                            }
                            OutlinedButton(onClick = { viewModel.testClassReminder() }, modifier = Modifier.weight(1f)) {
                                Text("Напоминание")
                            }
                        }
                    }
                }
            }

            item {
                SettingsSwitchItem(
                    title = "Живые обновления",
                    subtitle = "Статусная пилюля в строке состояния",
                    icon = Icons.Default.Timer,
                    checked = viewModel.liveUpdatesEnabled,
                    onCheckedChange = { viewModel.updateLiveUpdatesEnabled(it) }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Консоль приложения", style = MaterialTheme.typography.titleMedium)
                    Row {
                        IconButton(onClick = { 
                            val allLogs = logs.joinToString("\n") { "[${it.time}] ${it.level}: ${it.message}" }
                            clipboardManager.setText(AnnotatedString(allLogs))
                        }) {
                            Icon(Icons.Default.ContentCopy, "Копировать все")
                        }
                        IconButton(onClick = { AppLogger.clear() }) {
                            Icon(Icons.Default.Delete, "Очистить")
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { entry ->
                            LogItem(entry)
                        }
                        if (logs.isEmpty()) {
                            item {
                                Box(Modifier.fillParentMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Text("Логи пусты", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Логи и отладка", style = MaterialTheme.typography.titleMedium)
            }

            item {
                ListItem(
                    headlineContent = { Text("Сбросить базу данных (SQL)") },
                    leadingContent = { Icon(Icons.Default.Storage, null) },
                    modifier = Modifier.clickable { /* logic later */ }
                )
            }
        }
    }

    TopScrimProtection()
}
}

@Composable
fun LogItem(entry: AppLogger.LogEntry) {
    val containerColor = if (entry.level == "ERROR") {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }
    
    val contentColor = if (entry.level == "ERROR") {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Row {
            Text(
                text = entry.time,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = entry.level,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

