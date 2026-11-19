package com.mguuschedule.ui.screens

import android.annotation.SuppressLint
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mguuschedule.model.Lesson
import com.mguuschedule.model.MoodleCourse
import com.mguuschedule.model.MoodleCourseSection
import com.mguuschedule.model.MoodleDeadline
import com.mguuschedule.model.MoodleFileContent
import com.mguuschedule.model.MoodleGradeItem
import com.mguuschedule.model.MoodleModule
import com.mguuschedule.repository.MoodleRepository
import com.mguuschedule.ui.components.PredictiveBackContainer
import com.mguuschedule.ui.theme.AppMotionScheme
import com.mguuschedule.util.rememberHapticFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val MOODLE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
private val MOODLE_LESSON_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM (EEE)", Locale.forLanguageTag("ru"))

private data class PendingLessonAttachment(
    val title: String,
    val url: String,
    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val courseHint: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodleScreen(
    allLessons: List<Lesson>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: MoodleViewModel = viewModel(factory = MoodleViewModelFactory(app))
    val haptic = rememberHapticFeedback()
    val snackbarHostState = remember { SnackbarHostState() }

    val session = viewModel.session
    val selectedCourse = viewModel.selectedCourse
    val ssoLaunchUrl = viewModel.ssoLaunchUrl
    val standaloneWebUrl = viewModel.standaloneWebUrl

    var pendingAttachment by remember { mutableStateOf<PendingLessonAttachment?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.statusBanner) {
        val msg = viewModel.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Базовый слой (авторизация или главный экран СДО) всегда остаётся под оверлеем,
        // чтобы при предиктивном жесте «Назад» пользователь видел реальный превью-экран.
        if (session == null) {
            MoodleAuthScreen(
                isLoggingIn = viewModel.isLoggingIn,
                errorMessage = viewModel.errorMessage,
                onBack = onBack,
                onLogin = { user, pass ->
                    haptic.click()
                    viewModel.loginWithCredentials(user, pass)
                },
                onSsoLogin = {
                    haptic.click()
                    viewModel.startSsoLogin()
                },
                onOpenWebPortal = {
                    haptic.click()
                    viewModel.openStandaloneWebPortal(MoodleRepository.MOODLE_MY_URL)
                },
                onClearError = { viewModel.clearError() }
            )
        } else {
            // Основной экран авторизованного клиента СДО (Курсы / Задания / Портал)
            Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Шапка
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.padding(start = 2.dp)) {
                        Text(
                            text = "СДО Moodle",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = session.fullName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (viewModel.activeTab != MoodleTab.PORTAL) {
                        IconButton(
                            onClick = {
                                haptic.click()
                                viewModel.refreshDashboard(showPullRefresh = true)
                            },
                            enabled = !viewModel.isRefreshing
                        ) {
                            if (viewModel.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Обновить"
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Выйти из СДО",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Переключатель вкладок (Курсы / Задания / Портал)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MoodleTab.entries.forEach { tab ->
                    val selected = viewModel.activeTab == tab
                    val badge = when (tab) {
                        MoodleTab.COURSES -> if (viewModel.courses.isNotEmpty()) " (${viewModel.courses.size})" else ""
                        MoodleTab.DEADLINES -> if (viewModel.deadlines.isNotEmpty()) " (${viewModel.deadlines.size})" else ""
                        MoodleTab.PORTAL -> ""
                    }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            haptic.lightTick()
                            viewModel.selectTab(tab)
                        },
                        label = {
                            Text(
                                text = "${tab.label}$badge",
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Контент активной вкладки с MD3E Fade-Through анимацией
            AnimatedContent(
                targetState = viewModel.activeTab,
                transitionSpec = {
                    (fadeIn(AppMotionScheme.defaultEffectsSpec()) + scaleIn(
                        initialScale = 0.97f,
                        animationSpec = AppMotionScheme.defaultSpatialSpec()
                    )) togetherWith fadeOut(AppMotionScheme.fastEffectsSpec())
                },
                modifier = Modifier.weight(1f),
                label = "moodle_tab_transition"
            ) { tab ->
                when (tab) {
                    MoodleTab.COURSES -> {
                        MoodleCoursesTab(
                            courses = viewModel.courses,
                            isLoading = viewModel.isLoadingDashboard,
                            errorMessage = viewModel.errorMessage,
                            onRetry = { viewModel.refreshDashboard(showPullRefresh = false) },
                            onCourseClick = { course ->
                                haptic.click()
                                viewModel.openCourse(course)
                            }
                        )
                    }

                    MoodleTab.DEADLINES -> {
                        MoodleDeadlinesTab(
                            deadlines = viewModel.deadlines,
                            isLoading = viewModel.isLoadingDashboard,
                            onOpenDeadline = { deadline ->
                                haptic.click()
                                viewModel.openInPortal(deadline.url)
                            }
                        )
                    }

                    MoodleTab.PORTAL -> {
                        EmbeddedMoodleWebView(
                            url = viewModel.portalUrl,
                            isSsoMode = false,
                            onSsoCallback = {},
                            onNotify = { viewModel.showStatus(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Выйти из СДО Moodle?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Сохранённый токен, кэш курсов и cookies встроенного веб-портала будут очищены.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        haptic.click()
                        viewModel.logout(clearWebCookies = true)
                    }
                ) {
                    Text("Выйти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
        }

        // Оверлей 1: Детальный экран выбранного курса поверх списка курсов (с превью при Predictive Back)
        if (session != null && selectedCourse != null) {
            PredictiveBackContainer(
                onBack = { viewModel.closeCourse() },
                backdropColor = Color.Black.copy(alpha = 0.22f)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MoodleCourseDetailScreen(
                        course = selectedCourse,
                        sections = viewModel.courseSections,
                        grades = viewModel.courseGrades,
                        isLoading = viewModel.isCourseDetailLoading,
                        errorMessage = viewModel.courseDetailError,
                        snackbarHostState = snackbarHostState,
                        onBack = { viewModel.closeCourse() },
                        onRetry = { viewModel.openCourse(selectedCourse, forceRefresh = true) },
                        onOpenUrl = { url -> viewModel.openInPortal(url) },
                        onDownloadFile = { file ->
                            val started = enqueueMoodleDownload(context, file.fileUrl, file.fileName, file.mimeType)
                            if (started) {
                                haptic.click()
                                viewModel.showStatus("Загрузка «${file.fileName}» начата")
                            }
                        },
                        onAttachToLesson = { title, url, fileName, mimeType, sizeBytes ->
                            pendingAttachment = PendingLessonAttachment(
                                title = title,
                                url = url,
                                fileName = fileName,
                                mimeType = mimeType,
                                sizeBytes = sizeBytes,
                                courseHint = selectedCourse.fullName
                            )
                        }
                    )
                }
            }

            pendingAttachment?.let { attachment ->
                AttachMoodleToLessonSheet(
                    attachment = attachment,
                    allLessons = allLessons,
                    onDismiss = { pendingAttachment = null },
                    onSelectLesson = { lesson ->
                        haptic.click()
                        viewModel.attachResourceToLesson(
                            lesson = lesson,
                            title = attachment.title,
                            url = attachment.url,
                            fileName = attachment.fileName,
                            mimeType = attachment.mimeType,
                            sizeBytes = attachment.sizeBytes
                        )
                        pendingAttachment = null
                    }
                )
            }
        }

        // Оверлей 2: Полноэкранный веб-портал (без входа через API)
        if (standaloneWebUrl != null) {
            PredictiveBackContainer(
                onBack = { viewModel.closeStandaloneWebPortal() },
                backdropColor = Color.Black.copy(alpha = 0.22f)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MoodleWebPortalScreen(
                        title = "СДО МГУУ (Портал)",
                        initialUrl = standaloneWebUrl,
                        isSsoMode = false,
                        snackbarHostState = snackbarHostState,
                        onBack = { viewModel.closeStandaloneWebPortal() },
                        onSsoCallback = { viewModel.onSsoCallbackUrl(it) },
                        onNotify = { viewModel.showStatus(it) }
                    )
                }
            }
        }

        // Оверлей 3: Поток SSO-авторизации через Портал МГУУ (ks-auth.mguu.ru -> moodlemobile://token=...)
        if (ssoLaunchUrl != null) {
            PredictiveBackContainer(
                onBack = { viewModel.cancelSsoLogin() },
                backdropColor = Color.Black.copy(alpha = 0.22f)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MoodleWebPortalScreen(
                        title = "Вход через Портал МГУУ",
                        initialUrl = ssoLaunchUrl,
                        isSsoMode = true,
                        snackbarHostState = snackbarHostState,
                        onBack = { viewModel.cancelSsoLogin() },
                        onSsoCallback = { viewModel.onSsoCallbackUrl(it) },
                        onNotify = { viewModel.showStatus(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodleAuthScreen(
    isLoggingIn: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onLogin: (String, String) -> Unit,
    onSsoLogin: () -> Unit,
    onOpenWebPortal: () -> Unit,
    onClearError: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "СДО Moodle",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero-карточка СДО МГУУ
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "СДО МГУУ Правительства Москвы",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Курсы, дедлайны, оценки и прикрепление файлов к парам с online.mguu.ru",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Блок SSO входа (Портал МГУУ)
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Быстрый вход",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Рекомендуемый способ — авторизация через единый Портал МГУУ (SSO) с автоматическим сохранением сессии.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onSsoLogin,
                            enabled = !isLoggingIn,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Войти через Портал МГУУ", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Вход по логину и паролю СДО
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Логин и пароль СДО",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                if (errorMessage != null) onClearError()
                            },
                            label = { Text("Логин или email") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                if (errorMessage != null) onClearError()
                            },
                            label = { Text("Пароль") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (passwordVisible) "Скрыть" else "Показать"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (username.isNotBlank() && password.isNotBlank() && !isLoggingIn) {
                                        onLogin(username, password)
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!errorMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = { onLogin(username, password) },
                            enabled = username.isNotBlank() && password.isNotBlank() && !isLoggingIn,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (isLoggingIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Выполняем вход...")
                            } else {
                                Text("Войти по логину", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Запасной режим: встроенный веб-портал с сохранением cookies
                OutlinedButton(
                    onClick = onOpenWebPortal,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть веб-версию online.mguu.ru", fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MoodleCoursesTab(
    courses: List<MoodleCourse>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onCourseClick: (MoodleCourse) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCourses = remember(courses, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) courses
        else courses.filter {
            it.fullName.contains(q, ignoreCase = true) ||
                it.shortName.contains(q, ignoreCase = true)
        }
    }

    if (isLoading && courses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                com.mguuschedule.ui.components.ExpressiveLoadingIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary
            )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Загружаем курсы из СДО...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (!errorMessage.isNullOrBlank() && courses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry, shape = CircleShape) {
                    Text("Повторить")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (courses.size > 4) {
            item(key = "courses_search", contentType = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск по курсам...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Очистить")
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (filteredCourses.isEmpty()) {
            item(key = "courses_empty", contentType = "empty") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Курсы по запросу не найдены" else "Нет активных курсов в СДО",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredCourses,
                key = { it.id },
                contentType = { "course_card" }
            ) { course ->
                MoodleCourseCard(
                    course = course,
                    onClick = { onCourseClick(course) }
                )
            }
        }
    }
}

@Composable
private fun MoodleCourseCard(
    course: MoodleCourse,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (course.completed) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (course.completed) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = if (course.completed) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (course.shortName.isNotBlank() && course.shortName != course.fullName) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = course.shortName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (course.isFavourite) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Избранный курс",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            course.progress?.let { pct ->
                val clamped = (pct / 100f).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Прогресс курса",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${pct.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { clamped },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodleDeadlinesTab(
    deadlines: List<MoodleDeadline>,
    isLoading: Boolean,
    onOpenDeadline: (MoodleDeadline) -> Unit
) {
    if (isLoading && deadlines.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            com.mguuschedule.ui.components.ExpressiveLoadingIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        return
    }

    if (deadlines.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EventAvailable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Ближайших дедлайнов нет",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Новые задания и тесты из календаря СДО появятся здесь автоматически.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val now = remember { Instant.now() }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(now, zone) { now.atZone(zone).toLocalDate() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = deadlines,
            key = { "${it.id}_${it.dueDateEpochSec}" },
            contentType = { "deadline_card" }
        ) { deadline ->
            val dueZdt = remember(deadline.dueDateEpochSec) {
                Instant.ofEpochSecond(deadline.dueDateEpochSec).atZone(zone)
            }
            val dueDateStr = remember(dueZdt) { dueZdt.format(MOODLE_DATE_TIME_FORMATTER) }
            val daysDiff = remember(dueZdt, today) {
                ChronoUnit.DAYS.between(today, dueZdt.toLocalDate())
            }
            val (badgeText, badgeContainer, badgeContent) = when {
                deadline.overdue || dueZdt.toInstant().isBefore(now) -> Triple(
                    "Просрочено",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
                daysDiff == 0L -> Triple(
                    "Сегодня",
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer
                )
                daysDiff == 1L -> Triple(
                    "Завтра",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                else -> Triple(
                    "Через $daysDiff дн.",
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                onClick = { onOpenDeadline(deadline) },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = badgeContainer) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeContent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = dueDateStr,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = deadline.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (deadline.courseName.isNotBlank()) {
                        Text(
                            text = deadline.courseName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodleCourseDetailScreen(
    course: MoodleCourse,
    sections: List<MoodleCourseSection>,
    grades: List<MoodleGradeItem>,
    isLoading: Boolean,
    errorMessage: String?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDownloadFile: (MoodleFileContent) -> Unit,
    onAttachToLesson: (title: String, url: String, fileName: String?, mimeType: String?, sizeBytes: Long?) -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Материалы, 1 = Оценки

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
                Text(
                    text = course.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onOpenUrl("${MoodleRepository.MOODLE_BASE_URL}/course/view.php?id=${course.id}") }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = "Открыть курс на портале"
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    label = { Text("Материалы (${sections.sumOf { it.modules.size }})") },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    label = { Text("Оценки (${grades.size})") },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.mguuschedule.ui.components.ExpressiveLoadingIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary
            )
                }
                return@Column
            }

            if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRetry, shape = CircleShape) {
                            Text("Повторить")
                        }
                    }
                }
                return@Column
            }

            if (selectedSubTab == 0) {
                if (sections.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "В курсе пока нет опубликованных материалов",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = sections,
                            key = { it.id },
                            contentType = { "course_section" }
                        ) { section ->
                            MoodleSectionCard(
                                section = section,
                                onOpenUrl = onOpenUrl,
                                onDownloadFile = onDownloadFile,
                                onAttachToLesson = onAttachToLesson
                            )
                        }
                    }
                }
            } else {
                if (grades.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Оценки по курсу отсутствуют",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = grades,
                            key = { it.id },
                            contentType = { "grade_item" }
                        ) { grade ->
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = grade.itemName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = grade.gradeFormatted,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    if (grade.percentageFormatted.isNotBlank() || grade.rangeFormatted.isNotBlank()) {
                                        val meta = listOfNotNull(
                                            grade.percentageFormatted.takeIf { it.isNotBlank() },
                                            grade.rangeFormatted.takeIf { it.isNotBlank() }?.let { "диапазон: $it" }
                                        ).joinToString(" • ")
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (grade.feedback.isNotBlank()) {
                                        Text(
                                            text = grade.feedback,
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
}

@Composable
private fun MoodleSectionCard(
    section: MoodleCourseSection,
    onOpenUrl: (String) -> Unit,
    onDownloadFile: (MoodleFileContent) -> Unit,
    onAttachToLesson: (title: String, url: String, fileName: String?, mimeType: String?, sizeBytes: Long?) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = section.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (section.summary.isNotBlank()) {
                Text(
                    text = section.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            section.modules.forEach { module ->
                MoodleModuleRow(
                    module = module,
                    onOpenUrl = onOpenUrl,
                    onDownloadFile = onDownloadFile,
                    onAttachToLesson = onAttachToLesson
                )
            }
        }
    }
}

@Composable
private fun MoodleModuleRow(
    module: MoodleModule,
    onOpenUrl: (String) -> Unit,
    onDownloadFile: (MoodleFileContent) -> Unit,
    onAttachToLesson: (title: String, url: String, fileName: String?, mimeType: String?, sizeBytes: Long?) -> Unit
) {
    val icon = remember(module.modName) { moduleIconFor(module.modName) }

    Surface(
        onClick = {
            if (module.url.isNotBlank()) {
                onOpenUrl(module.url)
            } else if (module.contents.isNotEmpty()) {
                onDownloadFile(module.contents.first())
            }
        },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (module.modPlural.isNotBlank()) {
                        Text(
                            text = module.modPlural,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (module.completed == true) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Выполнено",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (module.url.isNotBlank() && module.contents.isEmpty()) {
                    IconButton(
                        onClick = {
                            onAttachToLesson(module.name, module.url, null, null, null)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = "Прикрепить к паре",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            module.contents.forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(
                        onClick = { onDownloadFile(file) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Скачать",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            onAttachToLesson(
                                module.name.ifBlank { file.fileName },
                                file.fileUrl,
                                file.fileName,
                                file.mimeType.ifBlank { null },
                                file.fileSize
                            )
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = "Прикрепить к паре",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachMoodleToLessonSheet(
    attachment: PendingLessonAttachment,
    allLessons: List<Lesson>,
    onDismiss: () -> Unit,
    onSelectLesson: (Lesson) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val today = remember { LocalDate.now() }

    val candidateLessons = remember(allLessons, search, attachment.courseHint) {
        val upcoming = allLessons
            .filter { !it.date.isBefore(today.minusDays(7)) }
            .sortedWith(compareBy<Lesson> { it.date }.thenBy { it.startTime })
            .ifEmpty { allLessons.sortedWith(compareByDescending<Lesson> { it.date }).take(40) }

        val q = search.trim()
        if (q.isNotBlank()) {
            upcoming.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.teacher.contains(q, ignoreCase = true)
            }.take(30)
        } else {
            val hintWords = attachment.courseHint
                .split(" ", ",", "-", ".")
                .map { it.trim().lowercase() }
                .filter { it.length >= 4 }
            upcoming.sortedByDescending { lesson ->
                val lower = lesson.title.lowercase()
                hintWords.count { lower.contains(it) }
            }.take(30)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Прикрепить к паре",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = attachment.fileName ?: attachment.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Поиск предмета...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (candidateLessons.isEmpty()) {
                Text(
                    text = "Подходящие пары не найдены. Убедитесь, что расписание загружено.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = candidateLessons,
                        key = { "${it.date}_${it.number}_${it.startTime}_${it.title}" }
                    ) { lesson ->
                        Surface(
                            onClick = { onSelectLesson(lesson) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = lesson.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${lesson.date.format(MOODLE_LESSON_DATE_FORMATTER)} • ${lesson.startTime}–${lesson.endTime}",
                                    style = MaterialTheme.typography.labelSmall,
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

@Composable
private fun MoodleWebPortalScreen(
    title: String,
    initialUrl: String,
    isSsoMode: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSsoCallback: (String) -> Unit,
    onNotify: (String) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            EmbeddedMoodleWebView(
                url = initialUrl,
                isSsoMode = isSsoMode,
                onSsoCallback = onSsoCallback,
                onNotify = onNotify,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedMoodleWebView(
    url: String,
    isSsoMode: Boolean,
    onSsoCallback: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNotify: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(url) }
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        fileChooserCallback?.onReceiveValue(uris.toTypedArray())
        fileChooserCallback = null
    }

    val scope = rememberCoroutineScope()
    val webBackProgress = remember { Animatable(0f) }
    var webSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    PredictiveBackHandler(enabled = canGoBack) { progressFlow ->
        try {
            progressFlow.collect { event ->
                webSwipeEdge = event.swipeEdge
                webBackProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            webViewRef?.goBack()
            webBackProgress.animateTo(0f, AppMotionScheme.fastSpatialSpec())
        } catch (_: CancellationException) {
            scope.launch {
                webBackProgress.animateTo(0f, AppMotionScheme.defaultSpatialSpec())
            }
        }
    }

    LaunchedEffect(url, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (url.isNotBlank() && wv.url != url) {
            wv.loadUrl(url)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    Column(modifier = modifier) {
        if (!isSsoMode) {
            // Быстрая панель навигации СДО
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { webViewRef?.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { webViewRef?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Вперёд", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { webViewRef?.reload() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", modifier = Modifier.size(18.dp))
                }
                AssistChip(
                    onClick = { webViewRef?.loadUrl(MoodleRepository.MOODLE_MY_URL) },
                    label = { Text("Личный кабинет") },
                    shape = CircleShape
                )
                AssistChip(
                    onClick = { webViewRef?.loadUrl(MoodleRepository.MOODLE_COURSES_URL) },
                    label = { Text("Мои курсы") },
                    shape = CircleShape
                )
                AssistChip(
                    onClick = { webViewRef?.loadUrl(MoodleRepository.MOODLE_CALENDAR_URL) },
                    label = { Text("Календарь") },
                    shape = CircleShape
                )
                AssistChip(
                    onClick = { webViewRef?.loadUrl(MoodleRepository.MOODLE_MESSAGES_URL) },
                    label = { Text("Сообщения") },
                    shape = CircleShape
                )
                IconButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl.ifBlank { MoodleRepository.MOODLE_BASE_URL }))
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Открыть в браузере", modifier = Modifier.size(18.dp))
                }
            }
        }

        if (isLoading && loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        } else if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
        ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = AppMotionScheme.StandardEasing.transform(webBackProgress.value.coerceIn(0f, 1f))
                    val dir = if (webSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                    translationX = dir * 20.dp.toPx() * p
                    val s = 1f - (p * 0.04f)
                    scaleX = s
                    scaleY = s
                    clip = p > 0.002f
                    shape = if (clip) RoundedCornerShape((24f * p).dp) else RectangleShape
                },
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        allowFileAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val reqUri = request?.url ?: return false
                            val reqUrl = reqUri.toString()

                            if (reqUrl.startsWith("${MoodleRepository.SSO_URL_SCHEME}://")) {
                                onSsoCallback(reqUrl)
                                return true
                            }

                            val scheme = reqUri.scheme?.lowercase()
                            if (scheme != "http" && scheme != "https") {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, reqUri))
                                }
                                return true
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                            isLoading = true
                            if (!pageUrl.isNullOrBlank()) currentUrl = pageUrl
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            isLoading = false
                            if (!pageUrl.isNullOrBlank()) currentUrl = pageUrl
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            CookieManager.getInstance().flush()
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                            if (newProgress >= 100) isLoading = false
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            fileChooserCallback?.onReceiveValue(null)
                            fileChooserCallback = filePathCallback
                            return runCatching {
                                val types = fileChooserParams?.acceptTypes
                                    ?.filter { !it.isNullOrBlank() }
                                    ?.toTypedArray()
                                    ?.takeIf { it.isNotEmpty() }
                                    ?: arrayOf("*/*")
                                filePickerLauncher.launch(types)
                                true
                            }.getOrElse {
                                fileChooserCallback = null
                                false
                            }
                        }
                    }

                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
                        val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                        val started = enqueueMoodleDownload(
                            context = ctx,
                            url = downloadUrl,
                            fileName = fileName,
                            mimeType = mimetype,
                            userAgent = userAgent
                        )
                        if (started) {
                            onNotify("Загрузка файла «$fileName» начата")
                        }
                    }

                    webViewRef = this
                    if (url.isNotBlank()) {
                        loadUrl(url)
                    }
                }
            }
        )
        }
    }
}

private fun moduleIconFor(modName: String): ImageVector = when (modName.lowercase()) {
    "assign" -> Icons.AutoMirrored.Rounded.Assignment
    "quiz" -> Icons.Rounded.Quiz
    "resource" -> Icons.Rounded.Description
    "folder" -> Icons.Rounded.Folder
    "url" -> Icons.Rounded.Link
    "forum" -> Icons.Rounded.Forum
    "page", "book" -> Icons.AutoMirrored.Rounded.MenuBook
    "label" -> Icons.Rounded.Info
    else -> Icons.AutoMirrored.Rounded.Article
}

private fun enqueueMoodleDownload(
    context: Context,
    url: String,
    fileName: String,
    mimeType: String?,
    userAgent: String? = null
): Boolean {
    return runCatching {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val safeName = fileName.ifBlank { "moodle_file" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(safeName)
            setDescription("СДО МГУУ")
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) addRequestHeader("Cookie", cookie)
            if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
        }
        dm.enqueue(request)
        true
    }.getOrElse {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        }.getOrDefault(false)
    }
}
