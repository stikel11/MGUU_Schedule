package com.mguuschedule

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mguuschedule.ui.screens.NotificationHistoryViewModel
import com.mguuschedule.ui.screens.NotificationHistoryViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import com.mguuschedule.model.Group
import com.mguuschedule.ui.screens.FeatureTourScreen
import com.mguuschedule.ui.screens.OnboardingScreen
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.repository.ScheduleRepository
import com.mguuschedule.ui.components.AnimatedFloatingNavBar
import com.mguuschedule.ui.components.SearchOverlay
import com.mguuschedule.ui.navigation.Screen
import com.mguuschedule.ui.screens.*
import com.mguuschedule.ui.theme.AppMotionScheme
import com.mguuschedule.ui.theme.MGUUScheduleTheme
import com.mguuschedule.util.CrashHandler
import com.mguuschedule.util.NotificationHelper
import com.mguuschedule.util.ShortcutHelper
import com.mguuschedule.util.rememberHapticFeedback
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate

private data class MainScreenState(val group: Group?, val isFeatureTourCompleted: Boolean)

private val BottomNavItems = listOf(Screen.Schedule, Screen.Rating, Screen.Sections)
private val BottomTabRoutes = listOf(Screen.Schedule.route, Screen.Rating.route, Screen.Sections.route)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        CrashHandler.init(applicationContext)
        processFCMIntent(intent)

        splashScreen.setOnExitAnimationListener { splashScreenProvider ->
            val splashView = splashScreenProvider.view
            val alpha = ObjectAnimator.ofFloat(
                splashView,
                View.ALPHA,
                1f,
                0f
            )
            alpha.duration = 400L
            alpha.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    splashScreenProvider.remove()
                }
            })
            alpha.start()
        }

        NotificationHelper.createNotificationChannels(this)
        ShortcutHelper.setupShortcuts(this)
        
        val startAction = intent.action
        
        setContent {
            val app = LocalContext.current.applicationContext as Application
            val database = remember(app) { AppDatabase.getDatabase(app) }
            val repository = remember(app, database) { ScheduleRepository(app, database) }
            
            val profileViewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModelFactory(repository, app)
            )
            val scheduleViewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModelFactory(app, repository)
            )
            val selectedGroup = profileViewModel.selectedGroup

            // FCM & Remote Config Initialization
            LaunchedEffect(Unit) {
                com.mguuschedule.fcm.FCMManager.initialize()
                com.mguuschedule.config.RemoteConfigManager.initialize(app)
                profileViewModel.checkForUpdates() // Start background update check
            }

            val emergencyAnnouncement by com.mguuschedule.config.RemoteConfigManager.announcement.collectAsState()

            val darkTheme = when (profileViewModel.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            if (startAction == "com.mguuschedule.ACTION_TOMORROW") {
                LaunchedEffect(startAction) {
                    scheduleViewModel.onDateSelected(LocalDate.now().plusDays(1))
                }
            }

            val prefs = remember { applicationContext.getSharedPreferences("mguu_prefs", android.content.Context.MODE_PRIVATE) }
            var isFeatureTourCompleted by remember {
                mutableStateOf(prefs.getBoolean("feature_tour_completed", false))
            }

            val appState = remember(selectedGroup, isFeatureTourCompleted) {
                MainScreenState(selectedGroup, isFeatureTourCompleted)
            }

            MGUUScheduleTheme(
                themeMode = profileViewModel.themeMode,
                dynamicColor = profileViewModel.dynamicColorEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = appState,
                        transitionSpec = {
                            fadeIn(animationSpec = AppMotionScheme.defaultEffectsSpec()) togetherWith
                            fadeOut(animationSpec = AppMotionScheme.defaultEffectsSpec())
                        },
                        label = "MainAppTransition"
                    ) { currentState: MainScreenState ->
                        if (currentState.group == null) {
                            OnboardingScreen(
                                groupsUiState = profileViewModel.groupsUiState,
                                onRetry = { profileViewModel.loadGroups() },
                                onGroupSelected = { profileViewModel.selectGroup(it) }
                            )
                        } else if (!currentState.isFeatureTourCompleted) {
                            FeatureTourScreen(
                                onFinish = {
                                    prefs.edit().putBoolean("feature_tour_completed", true).apply()
                                    isFeatureTourCompleted = true
                                }
                            )
                        } else {
                            val ratingViewModel: RatingViewModel = viewModel(
                                factory = RatingViewModelFactory(app)
                            )
                            MainAppScaffold(profileViewModel, scheduleViewModel, ratingViewModel)
                        }
                    }

                    emergencyAnnouncement?.let { announcement ->
                        if (announcement.enabled) {
                            AlertDialog(
                                onDismissRequest = {
                                    if (announcement.dismissible) {
                                        com.mguuschedule.config.RemoteConfigManager.acknowledgeAnnouncement(app, announcement.id)
                                    }
                                },
                                title = {
                                    Text(
                                        text = announcement.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                text = {
                                    Text(
                                        text = announcement.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            com.mguuschedule.config.RemoteConfigManager.acknowledgeAnnouncement(app, announcement.id)
                                            if (announcement.url.isNotBlank()) {
                                                runCatching {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(announcement.url))
                                                    app.startActivity(intent.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) })
                                                }
                                            }
                                        }
                                    ) {
                                        Text(announcement.buttonText, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    }
                                },
                                dismissButton = if (announcement.dismissible) {
                                    {
                                        TextButton(onClick = {
                                            com.mguuschedule.config.RemoteConfigManager.acknowledgeAnnouncement(app, announcement.id)
                                        }) {
                                            Text("Закрыть")
                                        }
                                    }
                                } else null,
                                shape = RoundedCornerShape(28.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        }
                    }

                    // Bottom Sheet for GitHub Updates
                    val updateState = profileViewModel.updateState
                    if (updateState is UpdateUiState.UpdateAvailable) {
                        val release = updateState.release
                        val skippedTag = remember(release.tagName) { prefs.getString("skipped_update_tag", null) }
                        if (release.tagName != skippedTag) {
                            var showSheet by remember(release.tagName) { mutableStateOf(true) }
                            if (showSheet) {
                                @OptIn(ExperimentalMaterial3Api::class)
                                ModalBottomSheet(
                                    onDismissRequest = { showSheet = false },
                                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 16.dp)
                                            .navigationBarsPadding(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.SystemUpdate,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Доступно обновление",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = release.tagName,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        // Changelog Box
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 80.dp, max = 280.dp)
                                        ) {
                                            val cleanChangelog = remember(release.changelog) {
                                                release.changelog
                                                    .replace("###", "")
                                                    .replace("##", "")
                                                    .replace("#", "")
                                                    .replace("**", "")
                                                    .trim()
                                            }
                                            
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(16.dp)
                                            ) {
                                                Text(
                                                    text = "Что нового:\n$cleanChangelog",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(20.dp))
                                        
                                        Button(
                                            onClick = {
                                                showSheet = false
                                                profileViewModel.downloadAndInstallUpdate(app, release.apkDownloadUrl)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp),
                                            shape = RoundedCornerShape(26.dp)
                                        ) {
                                            Text("Скачать и установить", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        TextButton(
                                            onClick = {
                                                prefs.edit().putString("skipped_update_tag", release.tagName).apply()
                                                showSheet = false
                                            }
                                        ) {
                                            Text("Пропустить эту версию", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun MainAppScaffold(
    profileViewModel: ProfileViewModel,
    scheduleViewModel: ScheduleViewModel,
    ratingViewModel: RatingViewModel
) {
    val navController = rememberNavController()
    val selectedGroup = profileViewModel.selectedGroup
    val haptic = rememberHapticFeedback()

    val currentContext = LocalContext.current
    val app = currentContext.applicationContext as Application
    val notificationViewModel: NotificationHistoryViewModel = viewModel(
        factory = NotificationHistoryViewModelFactory(app)
    )
    val unreadCount by notificationViewModel.unreadCount.collectAsState()

    val activity = remember(currentContext) { currentContext as? ComponentActivity }
    val lessonIdExtra = remember(activity) { activity?.intent?.getStringExtra("navigate_to_lesson_id") }
    val routeExtra = remember(activity) { activity?.intent?.getStringExtra("navigate_to_route") }

    LaunchedEffect(selectedGroup) {
        selectedGroup?.let { scheduleViewModel.loadSchedule(it.id) }
    }

    if (lessonIdExtra != null) {
        LaunchedEffect(lessonIdExtra) {
            navController.navigate("lesson/$lessonIdExtra")
        }
    }

    if (routeExtra != null) {
        LaunchedEffect(routeExtra) {
            navController.navigate(routeExtra)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Schedule.route,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            enterTransition = {
                val isTabSwitch = initialState.destination.route in BottomTabRoutes && targetState.destination.route in BottomTabRoutes
                if (isTabSwitch) com.mguuschedule.ui.theme.AppMotionScheme.tabEnterTransition()
                else com.mguuschedule.ui.theme.AppMotionScheme.detailEnterTransition()
            },
            exitTransition = {
                val isTabSwitch = initialState.destination.route in BottomTabRoutes && targetState.destination.route in BottomTabRoutes
                if (isTabSwitch) com.mguuschedule.ui.theme.AppMotionScheme.tabExitTransition()
                else com.mguuschedule.ui.theme.AppMotionScheme.detailBackgroundExitTransition()
            },
            popEnterTransition = {
                val isTabSwitch = initialState.destination.route in BottomTabRoutes && targetState.destination.route in BottomTabRoutes
                if (isTabSwitch) com.mguuschedule.ui.theme.AppMotionScheme.tabEnterTransition()
                else com.mguuschedule.ui.theme.AppMotionScheme.detailBackgroundPopEnterTransition()
            },
            popExitTransition = {
                val isTabSwitch = initialState.destination.route in BottomTabRoutes && targetState.destination.route in BottomTabRoutes
                if (isTabSwitch) com.mguuschedule.ui.theme.AppMotionScheme.tabExitTransition()
                else com.mguuschedule.ui.theme.AppMotionScheme.detailPopExitTransition()
            }
        ) {
            composable(Screen.Schedule.route) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ScheduleScreen(
                        viewModel = scheduleViewModel,
                        unreadNotificationCount = unreadCount,
                        onNotificationHistoryClick = { navController.navigate("notification_history") },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) },
                        onLessonClick = { lesson ->
                            haptic.click()
                            navController.navigate("lesson/${lesson.id}")
                        }
                    )
                }
            }
            composable(Screen.Rating.route) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    RatingScreen(viewModel = ratingViewModel)
                }
            }
            composable(Screen.Sections.route) {
                val allLessons by scheduleViewModel.allLessonsFlow.collectAsState()
                val addonSummaries by scheduleViewModel.lessonAddonSummaries.collectAsState()
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    com.mguuschedule.ui.screens.SectionsScreen(
                        allLessons = allLessons,
                        addonSummaries = addonSummaries,
                        onOpenCurriculum = { navController.navigate("curriculum") },
                        onOpenMaterials = { navController.navigate("materials_hub") },
                        onOpenAttendance = { navController.navigate("attendance") },
                        onOpenFreeRooms = { navController.navigate("free_rooms") },
                        onOpenMoodle = { navController.navigate("moodle") },
                        onOpenSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }
            }
            composable("moodle") {
                val allLessons by scheduleViewModel.allLessonsFlow.collectAsState()
                DetailDestinationContainer {
                    MoodleScreen(
                        allLessons = allLessons,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("curriculum") {
                val allLessons by scheduleViewModel.allLessonsFlow.collectAsState()
                DetailDestinationContainer {
                    com.mguuschedule.ui.screens.CurriculumScreen(
                        allLessons = allLessons,
                        subjectsFromRating = emptyList(),
                        controlPointsMap = emptyMap(),
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("materials_hub") {
                val allLessons by scheduleViewModel.allLessonsFlow.collectAsState()
                val addonSummaries by scheduleViewModel.lessonAddonSummaries.collectAsState()
                DetailDestinationContainer {
                    com.mguuschedule.ui.screens.MaterialsHubScreen(
                        allLessons = allLessons,
                        addonSummaries = addonSummaries,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("attendance") {
                val allLessons by scheduleViewModel.allLessonsFlow.collectAsState()
                DetailDestinationContainer {
                    com.mguuschedule.ui.screens.AttendanceScreen(
                        groupName = profileViewModel.selectedGroup?.name ?: "",
                        allLessons = allLessons,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("free_rooms") {
                DetailDestinationContainer {
                    com.mguuschedule.ui.screens.FreeRoomsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("notification_history") {
                DetailDestinationContainer {
                    NotificationHistoryScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.Settings.route) {
                DetailDestinationContainer {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        scheduleViewModel = scheduleViewModel,
                        onNavigateToDebug = { navController.navigate(Screen.Debug.route) },
                        onOpenFeatureTour = { navController.navigate("feature_tour") }
                    )
                }
            }
            composable("feature_tour") {
                DetailDestinationContainer {
                    FeatureTourScreen(
                        onFinish = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.Debug.route) {
                DetailDestinationContainer {
                    DebugScreen(
                        viewModel = profileViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = Screen.LessonDetail.route,
                arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
            ) { backStackEntry ->
                DetailDestinationContainer {
                    val lessonId = backStackEntry.arguments?.getString("lessonId")
                    val lesson = remember(lessonId) {
                        lessonId?.let { scheduleViewModel.getLessonById(it) }
                    }
                    LessonDetailScreen(
                        lesson = lesson,
                        onBack = { navController.popBackStack() },
                        onTeacherClick = { teacherName ->
                            val encodedName = URLEncoder.encode(teacherName, "UTF-8")
                            navController.navigate("teacher/$encodedName")
                        }
                    )
                }
            }

            composable(
                route = Screen.TeacherProfile.route,
                arguments = listOf(navArgument("teacherName") { type = NavType.StringType })
            ) { backStackEntry ->
                DetailDestinationContainer {
                    val teacherName = backStackEntry.arguments?.getString("teacherName")?.let {
                        URLDecoder.decode(it, "UTF-8")
                    } ?: "Неизвестный преподаватель"

                    TeacherProfileScreen(
                        teacherName = teacherName,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Screen.Todo.route) {
                DetailDestinationContainer {
                    TodoScreen(
                        scheduleViewModel = scheduleViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Screen.Search.route) {
                DetailDestinationContainer {
                    SearchOverlay(
                        onDismiss = { navController.popBackStack() },
                        allLessons = scheduleViewModel.lessons,
                        onLessonClick = { lesson ->
                            navController.navigate("lesson/${lesson.id}")
                        },
                        onTeacherClick = { teacherName ->
                            val encodedName = URLEncoder.encode(teacherName, "UTF-8")
                            navController.navigate("teacher/$encodedName")
                        }
                    )
                }
            }
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val isMainScreen = currentDestination?.route in BottomTabRoutes

        val bgColor = MaterialTheme.colorScheme.background
        val bottomScrimBrush = remember(bgColor) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    bgColor.copy(alpha = 0.4f),
                    bgColor.copy(alpha = 0.85f)
                )
            )
        }

        AnimatedVisibility(
            visible = isMainScreen,
            enter = slideInVertically(
                animationSpec = AppMotionScheme.defaultSpatialSpec(),
                initialOffsetY = { it / 2 }
            ) + fadeIn(animationSpec = AppMotionScheme.defaultEffectsSpec()),
            exit = slideOutVertically(
                animationSpec = AppMotionScheme.fastSpatialSpec(),
                targetOffsetY = { it / 2 }
            ) + fadeOut(animationSpec = AppMotionScheme.fastEffectsSpec()),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bottomScrimBrush)
                    .navigationBarsPadding()
                    .padding(top = 32.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    AnimatedFloatingNavBar(
                        items = BottomNavItems,
                        currentRoute = currentDestination?.route,
                        onItemClick = { screen ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            if (isSelected) {
                                when (screen) {
                                    Screen.Schedule -> {
                                        scheduleViewModel.resetToToday()
                                        scheduleViewModel.scrollToTop()
                                    }
                                    Screen.Rating -> {
                                        ratingViewModel.scrollToTop()
                                    }
                                    Screen.Sections -> {
                                        // Handle sections specific reset if needed
                                    }
                                    else -> {}
                                }
                            } else {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    val searchInteractionSource = remember { MutableInteractionSource() }
                    val isSearchPressed by searchInteractionSource.collectIsPressedAsState()
                    val searchScale by animateFloatAsState(
                        targetValue = if (isSearchPressed) 0.92f else 1f,
                        animationSpec = AppMotionScheme.fastSpatialSpec(),
                        label = "searchScale"
                    )

                    Surface(
                        onClick = {
                            haptic.lightTick()
                            navController.navigate(Screen.Search.route)
                        },
                        interactionSource = searchInteractionSource,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                scaleX = searchScale
                                scaleY = searchScale
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Поиск",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Непрозрачный контейнер для детальных экранов внутри NavHost.
 * Не перехватывает жест «Назад» вручную, позволяя встроенному SeekableTransitionState
 * в Navigation Compose 2.8+ выполнять настоящий меэкранный Predictive Back в стиле Pixel.
 */
@Composable
private fun DetailDestinationContainer(
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        content()
    }
}

// ---------------- FCM Helper ----------------

private fun ComponentActivity.processFCMIntent(intent: android.content.Intent?) {
    val extras = intent?.extras ?: return
    val msgId = extras.getString("google.message_id")
    if (!msgId.isNullOrBlank()) {
        val prefs = getSharedPreferences("processed_fcm_ids", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(msgId, false)) return // Уже обработано
        prefs.edit().putBoolean(msgId, true).apply()

        val title = extras.getString("gcm.notification.title") ?: extras.getString("title") ?: "Объявление"
        val body = extras.getString("gcm.notification.body") ?: extras.getString("body") ?: extras.getString("message")

        if (!body.isNullOrBlank()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(applicationContext)
                db.notificationDao().insertNotification(
                    com.mguuschedule.repository.NotificationEntity(
                        title = title,
                        message = body,
                        timestamp = System.currentTimeMillis(),
                        type = "GENERAL",
                        isRead = false
                    )
                )
            }
        }
    }
}
