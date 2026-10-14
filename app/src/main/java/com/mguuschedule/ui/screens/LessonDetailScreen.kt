package com.mguuschedule.ui.screens

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.text.style.TextDecoration
import java.time.format.TextStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalClipboardManager
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
import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
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
import com.mguuschedule.ui.components.StatusBarBlurOverlay
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

    fun addTask(title: String, deadlineEpoch: Long? = null) {
        viewModelScope.launch {
            repository.addTask(lessonKey, title, deadlineEpoch)
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
    if (room.contains("спорт", ignoreCase = true)) return null

    val regex = Regex("([1-5])\\d{2}")
    val match = regex.find(room) ?: return null
    val floorDigit = match.groupValues[1].toIntOrNull() ?: return null

    val isVoykovskaya = Regex("[1-5]\\d{2}[\\s\\-]*[кКkK]?[\\s\\-]*[ВвBb](?![а-яА-Яa-zA-Z])").containsMatchIn(room)

    return if (isVoykovskaya) {
        when (floorDigit) {
            1 -> R.drawable.v_floor_1
            2 -> R.drawable.v_floor_2
            3 -> R.drawable.v_floor_3
            4 -> R.drawable.v_floor_4
            else -> null
        }
    } else {
        when (floorDigit) {
            1 -> R.drawable.floor_1
            2 -> R.drawable.floor_2
            3 -> R.drawable.floor_3
            4 -> R.drawable.floor_4
            5 -> R.drawable.floor_5
            else -> null
        }
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

@Composable
fun FullscreenFloorMapDialog(
    imageResId: Int,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        var backProgress by remember { mutableFloatStateOf(0f) }
        var swipeEdge by remember { mutableIntStateOf(0) }
        PredictiveBackHandler { progressFlow ->
            try {
                progressFlow.collect { event ->
                    backProgress = event.progress
                    swipeEdge = event.swipeEdge
                }
                backProgress = 0f
                onDismiss()
            } catch (_: CancellationException) {
                backProgress = 0f
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - (backProgress * 0.18f)
                    scaleX = scale
                    scaleY = scale
                    translationX = (if (swipeEdge == 0) 1f else -1f) * backProgress * 48f
                }
                .background(Color.Black.copy(alpha = 0.95f * (1f - backProgress)))
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

            InteractiveFloorMap(
                imageResId = imageResId,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
            )
        }
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
        var backProgress by remember { mutableFloatStateOf(0f) }
        var swipeEdge by remember { mutableIntStateOf(0) }
        PredictiveBackHandler { progressFlow ->
            try {
                progressFlow.collect { event ->
                    backProgress = event.progress
                    swipeEdge = event.swipeEdge
                }
                backProgress = 0f
                onDismiss()
            } catch (_: CancellationException) {
                backProgress = 0f
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - (backProgress * 0.18f)
                    scaleX = scale
                    scaleY = scale
                    translationX = (if (swipeEdge == 0) 1f else -1f) * backProgress * 48f
                }
                .background(Color.Black.copy(alpha = 0.95f * (1f - backProgress)))
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
    val tintedBg = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, 0.07f)

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
        shape = RoundedCornerShape(18.dp),
        color = tintedBg,
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
                    modifier = Modifier.size(40.dp)
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
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (item.type) {
                            "URL" -> Icons.Rounded.Link
                            "IMAGE" -> Icons.Rounded.Image
                            else -> Icons.Rounded.AttachFile
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                    Icons.Rounded.DeleteOutline,
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
            val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale("ru"))
            "до ${date.dayOfMonth} $monthName"
        }
    }

    val isOverdue = remember(task.deadlineEpoch, task.isCompleted) {
        if (task.isCompleted || task.deadlineEpoch == null) false
        else task.deadlineEpoch < System.currentTimeMillis()
    }

    val tintedBg = if (isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    else androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, 0.07f)

    val formattedTitle = remember(task.title) {
        task.title.replaceFirstChar { it.uppercase() }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = tintedBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    onClick = { onToggle(!task.isCompleted) },
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (task.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = formattedTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (deadlineFormatted != null) {
                        Text(
                            text = deadlineFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Удалить задачу",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
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
    val roomDisplay = remember(lesson.room) {
        com.mguuschedule.util.formatRoomBadge(lesson.room, compact = false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        haptic.click()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Информация о паре",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Lesson Main Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Top Row: Type Badge & Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = typeFormatted,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            Text(
                                text = "${lesson.startTime} – ${lesson.endTime}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Middle Block: Subject Title
                        Text(
                            text = lesson.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 26.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bottom Row: Teacher & Room Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (lesson.teacher.isNotBlank() && lesson.teacher != "—") {
                                Surface(
                                    onClick = { 
                                        haptic.lightTick()
                                        onTeacherClick(lesson.teacher) 
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp)
                                        .offset(x = (-8).dp)
                                ) {
                                    Text(
                                        text = lesson.teacher,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    text = "Не указан",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Place,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = roomDisplay,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

            // Control Points Section (if linked to this lesson)
            if (linkedControlPoints.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.lightTick()
                        showNoteDialog = true
                    },
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Notes,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Заметка к паре",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            onClick = {
                                haptic.lightTick()
                                showNoteDialog = true
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (noteEntity?.text.isNullOrBlank()) Icons.Default.Add else Icons.Default.Edit,
                                    contentDescription = "Редактировать заметку",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    val tintedBg = androidx.compose.ui.graphics.lerp(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primary,
                        0.07f
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        onClick = {
                            haptic.lightTick()
                            showNoteDialog = true
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = tintedBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (noteEntity?.text.isNullOrBlank()) {
                            Text(
                                text = "Нажмите, чтобы добавить заметку к занятию...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        } else {
                            Text(
                                text = noteEntity!!.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tasks Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.TaskAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Задачки к паре",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            onClick = {
                                haptic.lightTick()
                                showTaskDialog = true
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Добавить задачу",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (taskList.isEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Нет задач к этой паре",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
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

            Spacer(modifier = Modifier.height(12.dp))

            // Materials Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Материалы к паре",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                onClick = {
                                    haptic.lightTick()
                                    showUrlDialog = true
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Link,
                                        contentDescription = "Ссылка",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Surface(
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
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.AttachFile,
                                        contentDescription = "Файл",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (materialList.isEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Нет прикрепленных материалов",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
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

            // Floor Plan Section
            val floorImageResId = remember(lesson.room) { getFloorImageResId(lesson.room) }
            val floorNumber = remember(lesson.room) {
                Regex("([1-5])\\d{2}").find(lesson.room)?.groupValues?.get(1) ?: ""
            }
            var showFloorMapDialog by remember { mutableStateOf(false) }

            if (floorImageResId != null) {
                val titleText = if (floorNumber.isNotBlank()) "Схема $floorNumber этажа" else "Схема этажа"

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = {
                            haptic.lightTick()
                            showFloorMapDialog = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .widthIn(max = 220.dp)
                            .wrapContentHeight()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = floorImageResId),
                                contentDescription = titleText,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "Увеличить схему",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (showFloorMapDialog) {
                    FullscreenFloorMapDialog(
                        imageResId = floorImageResId,
                        title = if (roomDisplay.isNotBlank()) "$titleText ($roomDisplay)" else titleText,
                        onDismiss = { showFloorMapDialog = false }
                    )
                }
            }
            
            Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    StatusBarBlurOverlay()

    val bentoTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
    )
    val bentoFieldShape = RoundedCornerShape(18.dp)

    // Note Sheet
    if (showNoteDialog) {
        var textValue by remember { mutableStateOf(noteEntity?.text.orEmpty()) }
        ModalBottomSheet(
            onDismissRequest = { showNoteDialog = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            ) {
                Text(
                    text = "Заметка к паре",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    placeholder = { Text("Введите текст заметки...") },
                    shape = bentoFieldShape,
                    minLines = 4,
                    maxLines = 8,
                    trailingIcon = {
                        if (textValue.isNotEmpty()) {
                            IconButton(onClick = { textValue = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить")
                            }
                        }
                    },
                    colors = bentoTextFieldColors
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        haptic.click()
                        viewModel.saveNote(textValue)
                        showNoteDialog = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "Сохранить",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Task Sheet
    if (showTaskDialog) {
        var titleValue by remember { mutableStateOf("") }
        var selectedDeadlineOption by remember { mutableIntStateOf(0) }
        var customDeadlineDate by remember { mutableStateOf<LocalDate?>(null) }
        var showDatePicker by remember { mutableStateOf(false) }

        val lessonDate = lesson.date
        val tomorrowDate = remember { LocalDate.now().plusDays(1) }

        val selectedDeadlineEpoch = remember(selectedDeadlineOption, customDeadlineDate, lessonDate, tomorrowDate) {
            when (selectedDeadlineOption) {
                1 -> lessonDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                2 -> tomorrowDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                3 -> customDeadlineDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                else -> null
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showTaskDialog = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            ) {
                Text(
                    text = "Новая задача",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = titleValue,
                    onValueChange = { titleValue = it },
                    placeholder = { Text("Заголовок задачи...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = bentoFieldShape,
                    singleLine = true,
                    colors = bentoTextFieldColors
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Срок выполнения",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedDeadlineOption == 0,
                        onClick = { selectedDeadlineOption = 0 },
                        label = { Text("Без срока") },
                        shape = CircleShape
                    )

                    FilterChip(
                        selected = selectedDeadlineOption == 1,
                        onClick = { selectedDeadlineOption = 1 },
                        label = { Text("Ко дню пары (${lessonDate.dayOfMonth} ${lessonDate.month.getDisplayName(TextStyle.SHORT, Locale("ru"))})") },
                        shape = CircleShape
                    )

                    FilterChip(
                        selected = selectedDeadlineOption == 2,
                        onClick = { selectedDeadlineOption = 2 },
                        label = { Text("Завтра") },
                        shape = CircleShape
                    )

                    val customLabel = if (customDeadlineDate != null && selectedDeadlineOption == 3) {
                        "${customDeadlineDate!!.dayOfMonth} ${customDeadlineDate!!.month.getDisplayName(TextStyle.SHORT, Locale("ru"))}"
                    } else {
                        "Выбрать дату..."
                    }

                    FilterChip(
                        selected = selectedDeadlineOption == 3,
                        onClick = {
                            selectedDeadlineOption = 3
                            showDatePicker = true
                        },
                        label = { Text(customLabel) },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = CircleShape
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (titleValue.isNotBlank()) {
                            haptic.click()
                            viewModel.addTask(titleValue, selectedDeadlineEpoch)
                            showTaskDialog = false
                        }
                    },
                    enabled = titleValue.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "Добавить задачу",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDeadlineEpoch ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                customDeadlineDate = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                            }
                            showDatePicker = false
                        }
                    ) { Text("OK", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    // URL Sheet
    if (showUrlDialog) {
        var titleValue by remember { mutableStateOf("") }
        var urlValue by remember { mutableStateOf("") }
        val clipboardManager = LocalClipboardManager.current

        ModalBottomSheet(
            onDismissRequest = { showUrlDialog = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            ) {
                Text(
                    text = "Прикрепить ссылку",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = titleValue,
                    onValueChange = { titleValue = it },
                    placeholder = { Text("Название (например: Презентация)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = bentoFieldShape,
                    singleLine = true,
                    colors = bentoTextFieldColors
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = urlValue,
                    onValueChange = { urlValue = it },
                    placeholder = { Text("Ссылка (https://...)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = bentoFieldShape,
                    singleLine = true,
                    trailingIcon = {
                        if (urlValue.isNotEmpty()) {
                            IconButton(onClick = { urlValue = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить")
                            }
                        }
                    },
                    colors = bentoTextFieldColors
                )

                Spacer(modifier = Modifier.height(10.dp))

                AssistChip(
                    onClick = {
                        val clip = clipboardManager.getText()
                        if (!clip.isNullOrBlank()) {
                            urlValue = clip.text
                        }
                    },
                    label = { Text("Вставить из буфера") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = CircleShape
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (urlValue.isNotBlank()) {
                            haptic.click()
                            val finalTitle = titleValue.ifBlank { "Ссылка" }
                            val finalUrl = if (!urlValue.startsWith("http://") && !urlValue.startsWith("https://")) {
                                "https://$urlValue"
                            } else urlValue

                            viewModel.addMaterial(
                                title = finalTitle,
                                type = "URL",
                                uriOrUrl = finalUrl
                            )
                            showUrlDialog = false
                        }
                    },
                    enabled = urlValue.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "Прикрепить",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
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
