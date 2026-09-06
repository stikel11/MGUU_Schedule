package com.mguuschedule.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.repository.NotificationDao
import com.mguuschedule.repository.NotificationEntity
import com.mguuschedule.util.rememberHapticFeedback
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class NotificationHistoryViewModel(
    private val notificationDao: NotificationDao,
    application: Application
) : AndroidViewModel(application) {

    val notifications: StateFlow<List<NotificationEntity>> = notificationDao.getAllNotificationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = notificationDao.getUnreadCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationDao.markAllAsRead()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            notificationDao.clearAll()
        }
    }
}

class NotificationHistoryViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(application)
        return NotificationHistoryViewModel(db.notificationDao(), application) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: NotificationHistoryViewModel = viewModel(
        factory = NotificationHistoryViewModelFactory(app)
    )
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val haptic = rememberHapticFeedback()

    LaunchedEffect(Unit) {
        viewModel.markAllAsRead()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История уведомлений", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.lightTick()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (notifications.isNotEmpty()) {
                        IconButton(onClick = {
                            haptic.click()
                            viewModel.clearAll()
                        }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить историю")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "История уведомлений пуста",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Здесь будут появляться отслеживаемые изменения и напоминания",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    NotificationItemCard(item = item)
                }
            }
        }
    }
}

@Composable
fun NotificationItemCard(item: NotificationEntity) {
    val dateTimeStr = remember(item.timestamp) {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(item.timestamp), ZoneId.systemDefault())
        val dateFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
        dt.format(dateFormatter)
    }

    val typeLabel = remember(item.type) {
        when (item.type) {
            "CHANGE" -> "Изменение"
            "REMINDER" -> "Напоминание"
            "LIVE_UPDATE" -> "Live Update"
            else -> "Системное"
        }
    }

    val typeBgColor = when (item.type) {
        "CHANGE" -> MaterialTheme.colorScheme.primaryContainer
        "REMINDER" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val typeTextColor = when (item.type) {
        "CHANGE" -> MaterialTheme.colorScheme.onPrimaryContainer
        "REMINDER" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (!item.isRead) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = typeBgColor
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = typeTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (!item.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Text(
                    text = dateTimeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (item.message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
