package com.mguuschedule.ui.screens

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mguuschedule.R
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.Lesson
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.mguuschedule.repository.LessonMaterialEntity
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.repository.LessonTaskEntity
import com.mguuschedule.util.SearchEngine
import com.mguuschedule.repository.ScheduleRepository
import com.mguuschedule.ui.components.TopScrimProtection
import com.mguuschedule.util.formatClassroom
import com.mguuschedule.util.rememberHapticFeedback
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LessonDetailViewModel(
    application: Application,
    val lesson: Lesson
) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ScheduleRepository(application, database)
    private val lessonKey = "${lesson.date}_${lesson.number}_${lesson.startTime}"

    val noteFlow = repository.getNoteFlow(lessonKey)
    val tasksFlow = repository.getTasksFlow(lessonKey)
    val materialsFlow = repository.getMaterialsFlow(lessonKey)

    fun saveNote(text: String) {
        viewModelScope.launch {
            repository.saveNote(lessonKey, text)
        }
    }

    fun addMaterial(
        title: String,
        type: String,
        uriOrUrl: String,
        fileName: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null
    ) {
        viewModelScope.launch {
            repository.addMaterial(lessonKey, title, type, uriOrUrl, fileName, mimeType, sizeBytes)
        }
    }

    fun deleteMaterial(materialId: Long) {
        viewModelScope.launch {
            repository.deleteMaterial(materialId)
        }
    }

    fun addTask(title: String, deadlineStr: String? = null) {
        viewModelScope.launch {
            val epoch = runCatching {
                deadlineStr?.let {
                    LocalDate.parse(it.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }.getOrNull()
            repository.addTask(lessonKey, title, epoch)
        }
    }

    fun toggleTask(taskId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleTaskCompleted(taskId, isCompleted)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }
}

class LessonDetailViewModelFactory(
    private val application: Application,
    private val lesson: Lesson
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LessonDetailViewModel(application, lesson) as T
    }
}

/**
 * Определение ресурса схемы этажа по номеру аудитории.
 * Поддерживаются 3-значные аудитории (101-599). Первая цифра определяет этаж (1-5).
 */
fun getFloorImageResId(room: String): Int? {
    if (room.isBlank() || room.trim() == "—") return null
    if (room.contains("онлайн", ignoreCase = true) || room.contains("дистант", ignoreCase = true)) return null

    val regex = Regex("([1-5])\\d{2}")
    val match = regex.find(room) ?: return null
    val floorDigit = match.groupValues[1].toIntOrNull() ?: return null

    return when (floorDigit) {
        1 -> R.drawable.floor_1
        2 -> R.drawable.floor_2
        3 -> R.drawable.floor_3
        4 -> R.drawable.floor_4
        5 -> R.drawable.floor_5
        else -> null
    }
}

/**
 * Интерактивный компонент просмотра схемы этажа с поддержкой Pinch-to-Zoom, Pan и Double-Tap Zoom.
 */
@Composable
fun InteractiveFloorMap(
    imageResId: Int,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale <= 1.05f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            val maxOffsetX = 320f * (scale - 1f)
            val maxOffsetY = 240f * (scale - 1f)
            val newX = (offset.x + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX)
            val newY = (offset.y + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            offset = Offset(newX, newY)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .transformable(state = state),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = imageResId),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

fun isImageMaterial(item: LessonMaterialEntity): Boolean {
    val mime = item.mimeType?.lowercase().orEmpty()
    if (mime.startsWith("image/")) return true

    val name = (item.fileName ?: item.uriOrUrl).lowercase()
    return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
           name.endsWith(".png") || name.endsWith(".webp") ||
           name.endsWith(".gif") || name.endsWith(".bmp")
}

fun formatFileSize(sizeBytes: Long?): String {
    if (sizeBytes == null || sizeBytes <= 0) return ""
    val kb = sizeBytes / 1024.0
    if (kb < 1024) {
        return String.format(Locale.US, "%.1f КБ", kb)
    }
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f МБ", mb)
}

@Composable
fun rememberImageThumbnail(filePath: String, targetSizePx: Int = 200): ImageBitmap? {
    return produceState<ImageBitmap?>(initialValue = null, key1 = filePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(filePath)
                if (!file.exists()) return@runCatching null

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)

                var sampleSize = 1
                val height = options.outHeight
                val width = options.outWidth
                if (height > targetSizePx || width > targetSizePx) {
                    val halfHeight = height / 2
                    val halfWidth = width / 2
                    while (halfHeight / sampleSize >= targetSizePx && halfWidth / sampleSize >= targetSizePx) {
                        sampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                bitmap?.asImageBitmap()
            }.getOrNull()
        }
    }.value
}

@Composable
fun FullscreenImagePreviewDialog(
    filePath: String,
    title: String,
    onDismiss: () -> Unit
) {
    val bitmap = rememberImageThumbnail(filePath, targetSizePx = 2048)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(onBack = onDismiss)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White
                    )
                }
            }

            if (bitmap != null) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 4f)
                    if (scale <= 1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        val newX = offset.x + offsetChange.x
                        val newY = offset.y + offsetChange.y
                        offset = Offset(newX, newY)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp)
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .transformable(state = state),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
            } else {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun MaterialItemRow(
    item: LessonMaterialEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isImage = remember(item) { isImageMaterial(item) }
    val thumbnailBitmap = if (isImage) rememberImageThumbnail(item.uriOrUrl, targetSizePx = 160) else null

    val metaText = remember(item) {
        if (item.type == "URL") {
            item.uriOrUrl
        } else {
            val ext = (item.fileName ?: item.uriOrUrl).substringAfterLast('.', "").uppercase()
            val size = formatFileSize(item.sizeBytes)
            listOf(ext, size).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isImage && thumbnailBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Image(
                        bitmap = thumbnailBitmap,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isImage) "IMG" else item.type,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить материал",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun TaskItemRow(
    task: LessonTaskEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val deadlineFormatted = remember(task.deadlineEpoch) {
        task.deadlineEpoch?.let {
            val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date)
        }
    }

    val isOverdue = remember(task.deadlineEpoch, task.isCompleted) {
        if (task.isCompleted || task.deadlineEpoch == null) false
        else task.deadlineEpoch < System.currentTimeMillis()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = onToggle
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (deadlineFormatted != null) {
                        Text(
                            text = "Дедлайн: $deadlineFormatted",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Удалить задачу",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailScreen(
    lesson: Lesson?,
    onBack: () -> Unit,
    onTeacherClick: (String) -> Unit = {}
) {
    if (lesson == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Информация о паре не найдена")
        }
        return
    }

    val context = LocalContext.current
    val viewModel: LessonDetailViewModel = viewModel(
        factory = LessonDetailViewModelFactory(context.applicationContext as Application, lesson)
    )

    val noteEntity by viewModel.noteFlow.collectAsState(initial = null)
    val taskList by viewModel.tasksFlow.collectAsState(initial = emptyList())
    val materialList by viewModel.materialsFlow.collectAsState(initial = emptyList())
    val haptic = rememberHapticFeedback()

    var showUrlDialog by remember { mutableStateOf(false) }
    var previewImageItem by remember { mutableStateOf<LessonMaterialEntity?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    cursor.moveToFirst()
                    val fileName = if (nameIndex != -1) cursor.getString(nameIndex) else "file"
                    val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                    val destFile = File(context.filesDir, "mat_${System.currentTimeMillis()}_$fileName")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val typeStr = when {
                        mimeType.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true) -> "PDF"
                        mimeType.contains("presentation", ignoreCase = true) || fileName.endsWith(".pptx", ignoreCase = true) -> "PPTX"
                        else -> "FILE"
                    }

                    viewModel.addMaterial(
                        title = fileName,
                        type = typeStr,
                        uriOrUrl = destFile.absolutePath,
                        fileName = fileName,
                        mimeType = mimeType,
                        sizeBytes = size
                    )
                }
            }
        }
    }

    val database = remember { AppDatabase.getDatabase(context) }
    val ratingEntity by database.ratingDao().getLatestRatingCacheFlow().collectAsState(initial = null)

    val linkedControlPoints = remember(lesson, ratingEntity) {
        val entity = ratingEntity
        if (entity == null) emptyList()
        else {
            val map = SearchEngine.computeLessonControlPointsMap(listOf(lesson), entity)
            val lessonKey = "${lesson.date}_${lesson.number}_${lesson.startTime}"
            map[lessonKey] ?: emptyList()
        }
    }

    var showNoteDialog by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }

    val typeFormatted = remember(lesson.type) {
        lesson.type.lowercase().replaceFirstChar { it.uppercase() }
    }
    val roomFormatted = remember(lesson.room) {
        formatClassroom(lesson.room)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text("Информация о паре", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.click()
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
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
            // Lesson Main Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = typeFormatted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 26.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

                    Spacer(modifier = Modifier.height(12.dp))

                    // Time & Room Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Время проведения",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${lesson.startTime} — ${lesson.endTime}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Аудитория",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = roomFormatted.replace("Ауд. ", "").replace("Ауд.", ""),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Teacher Row
                    Column {
                        Text(
                            text = "Преподаватель",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        if (lesson.teacher.isNotBlank() && lesson.teacher != "—") {
                            Surface(
                                onClick = { 
                                    haptic.lightTick()
                                    onTeacherClick(lesson.teacher) 
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                modifier = Modifier.offset(x = (-8).dp)
                            ) {
                                Text(
                                    text = lesson.teacher,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Не указан",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control Points Section (if linked to this lesson)
            if (linkedControlPoints.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.TaskAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (linkedControlPoints.size == 1) "Контрольная точка" else "Контрольные точки",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        linkedControlPoints.forEachIndexed { index, point ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = point.pointName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (point.date.isNotBlank() && point.date != "—") {
                                        Text(
                                            text = point.date,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (point.score != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = if (point.score != null) "${point.score} баллов" else "Результат отсутствует",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (point.score != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Notes Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Заметка к паре",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.lightTick()
                                showNoteDialog = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (noteEntity?.text.isNullOrBlank()) Icons.Default.Add else Icons.Default.Edit,
                                contentDescription = "Редактировать заметку",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (noteEntity?.text.isNullOrBlank()) {
                        Text(
                            text = "Добавить комментарий...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    } else {
                        Text(
                            text = noteEntity!!.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tasks Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Задачки к паре",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.lightTick()
                                showTaskDialog = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Добавить задачу",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (taskList.isEmpty()) {
                        Text(
                            text = "Нет задач к этой паре",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            taskList.forEach { task ->
                                TaskItemRow(
                                    task = task,
                                    onToggle = { isChecked ->
                                        haptic.click()
                                        viewModel.toggleTask(task.id, isChecked)
                                    },
                                    onDelete = {
                                        haptic.click()
                                        viewModel.deleteTask(task.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Materials Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Материалы к паре",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    haptic.lightTick()
                                    showUrlDialog = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = "Ссылка",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptic.lightTick()
                                    filePickerLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = "Файл",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (materialList.isEmpty()) {
                        Text(
                            text = "Нет прикрепленных материалов",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            materialList.forEach { item ->
                                MaterialItemRow(
                                    item = item,
                                    onClick = {
                                        haptic.click()
                                        if (isImageMaterial(item)) {
                                            previewImageItem = item
                                        } else if (item.type == "URL") {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.uriOrUrl))
                                                context.startActivity(intent)
                                            }
                                        } else {
                                            runCatching {
                                                val file = File(item.uriOrUrl)
                                                if (file.exists()) {
                                                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(contentUri, item.mimeType ?: "*/*")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Открыть материал"))
                                                }
                                            }
                                        }
                                    },
                                    onDelete = {
                                        haptic.click()
                                        viewModel.deleteMaterial(item.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Floor Plan Section (In existing block on LessonDetailScreen)
            val floorImageResId = remember(lesson.room) { getFloorImageResId(lesson.room) }
            val floorNumber = remember(lesson.room) {
                Regex("\\b([1-5])\\d{2}\\b").find(lesson.room)?.groupValues?.get(1) ?: ""
            }

            if (floorImageResId != null) {
                Text(
                    text = if (floorNumber.isNotBlank()) "Схема $floorNumber этажа" else "Схема этажа",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    InteractiveFloorMap(
                        imageResId = floorImageResId,
                        contentDescription = "Схема $floorNumber этажа для $roomFormatted",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Note Dialog
    if (showNoteDialog) {
        var textValue by remember { mutableStateOf(noteEntity?.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Заметка к паре", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("Введите текст заметки...") },
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.click()
                    viewModel.saveNote(textValue)
                    showNoteDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Task Dialog
    if (showTaskDialog) {
        var titleValue by remember { mutableStateOf("") }
        var deadlineValue by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            title = { Text("Новая задача", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleValue,
                        onValueChange = { titleValue = it },
                        label = { Text("Заголовок задачи") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = deadlineValue,
                        onValueChange = { deadlineValue = it },
                        label = { Text("Дедлайн (например: 2025-09-30)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (titleValue.isNotBlank()) {
                            haptic.click()
                            viewModel.addTask(titleValue, deadlineValue.ifBlank { null })
                            showTaskDialog = false
                        }
                    }
                ) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showTaskDialog = false }) { Text("Отмена") }
            }
        )
    }

    // URL Dialog
    if (showUrlDialog) {
        var titleValue by remember { mutableStateOf("") }
        var urlValue by remember { mutableStateOf("https://") }

        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Прикрепить ссылку", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleValue,
                        onValueChange = { titleValue = it },
                        label = { Text("Название") },
                        placeholder = { Text("Например: Презентация к лекции") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = urlValue,
                        onValueChange = { urlValue = it },
                        label = { Text("Ссылка (URL)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (urlValue.isNotBlank()) {
                        haptic.click()
                        val title = if (titleValue.isNotBlank()) titleValue else "Ссылка"
                        viewModel.addMaterial(
                            title = title,
                            type = "URL",
                            uriOrUrl = urlValue
                        )
                        showUrlDialog = false
                    }
                }) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Отмена") }
            }
        )
    }

    previewImageItem?.let { previewItem ->
        FullscreenImagePreviewDialog(
            filePath = previewItem.uriOrUrl,
            title = previewItem.title,
            onDismiss = { previewImageItem = null }
        )
    }
}

fun isImageMaterial(item: LessonMaterialEntity): Boolean {
    val mime = item.mimeType?.lowercase().orEmpty()
    if (mime.startsWith("image/")) return true

    val name = (item.fileName ?: item.uriOrUrl).lowercase()
    return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
           name.endsWith(".png") || name.endsWith(".webp") ||
           name.endsWith(".gif") || name.endsWith(".bmp")
}

fun formatFileSize(sizeBytes: Long?): String {
    if (sizeBytes == null || sizeBytes <= 0) return ""
    val kb = sizeBytes / 1024.0
    if (kb < 1024) {
        return String.format(Locale.US, "%.1f КБ", kb)
    }
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f МБ", mb)
}

@Composable
fun rememberImageThumbnail(filePath: String, targetSizePx: Int = 200): ImageBitmap? {
    return produceState<ImageBitmap?>(initialValue = null, key1 = filePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(filePath)
                if (!file.exists()) return@runCatching null

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)

                var sampleSize = 1
                val height = options.outHeight
                val width = options.outWidth
                if (height > targetSizePx || width > targetSizePx) {
                    val halfHeight = height / 2
                    val halfWidth = width / 2
                    while (halfHeight / sampleSize >= targetSizePx && halfWidth / sampleSize >= targetSizePx) {
                        sampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                bitmap?.asImageBitmap()
            }.getOrNull()
        }
    }.value
}

@Composable
fun FullscreenImagePreviewDialog(
    filePath: String,
    title: String,
    onDismiss: () -> Unit
) {
    val bitmap = rememberImageThumbnail(filePath, targetSizePx = 2048)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(onBack = onDismiss)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White
                    )
                }
            }

            if (bitmap != null) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 4f)
                    if (scale <= 1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        val newX = offset.x + offsetChange.x
                        val newY = offset.y + offsetChange.y
                        offset = Offset(newX, newY)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp)
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .transformable(state = state),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
            } else {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun MaterialItemRow(
    item: LessonMaterialEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isImage = remember(item) { isImageMaterial(item) }
    val thumbnailBitmap = if (isImage) rememberImageThumbnail(item.uriOrUrl, targetSizePx = 160) else null

    val metaText = remember(item) {
        if (item.type == "URL") {
            item.uriOrUrl
        } else {
            val ext = (item.fileName ?: item.uriOrUrl).substringAfterLast('.', "").uppercase()
            val size = formatFileSize(item.sizeBytes)
            listOf(ext, size).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isImage && thumbnailBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Image(
                        bitmap = thumbnailBitmap,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isImage) "IMG" else item.type,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить материал",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun TaskItemRow(
    task: LessonTaskEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val deadlineFormatted = remember(task.deadlineEpoch) {
        task.deadlineEpoch?.let {
            val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date)
        }
    }

    val isOverdue = remember(task.deadlineEpoch, task.isCompleted) {
        if (task.isCompleted || task.deadlineEpoch == null) false
        else task.deadlineEpoch < System.currentTimeMillis()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = onToggle
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (deadlineFormatted != null) {
                        Text(
                            text = "Дедлайн: $deadlineFormatted",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Удалить задачу",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
}
