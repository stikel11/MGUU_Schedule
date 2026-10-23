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
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animate
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        CrashHandler.init(applicationContext)

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
            val database = AppDatabase.getDatabase(app)
            val repository = ScheduleRepository(app, database)
            
            val profileViewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModelFactory(repository, app)
            )
            val scheduleViewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModelFactory(app)
            )
            val selectedGroup = profileViewModel.selectedGroup

            // FCM & Remote Config Initialization
            LaunchedEffect(Unit) {
                com.mguuschedule.fcm.FCMManager.initialize()
                com.mguuschedule.config.RemoteConfigManager.initialize(app)
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

            LaunchedEffect(startAction) {
                when(startAction) {
                    "com.mguuschedule.ACTION_TOMORROW" -> {
                        scheduleViewModel.onDateSelected(LocalDate.now().plusDays(1))
                    }
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
    val items = listOf(Screen.Schedule, Screen.Rating, Screen.Sections)
    val selectedGroup = profileViewModel.selectedGroup
    val haptic = rememberHapticFeedback()

    val currentContext = LocalContext.current
    val app = currentContext.applicationContext as Application
    val notificationViewModel: NotificationHistoryViewModel = viewModel(
        factory = NotificationHistoryViewModelFactory(app)
    )
    val unreadCount by notificationViewModel.unreadCount.collectAsState()

    val activity = remember(currentContext) { currentContext as? ComponentActivity }
    val lessonIdExtra = remember { activity?.intent?.getStringExtra("navigate_to_lesson_id") }
    val routeExtra = remember { activity?.intent?.getStringExtra("navigate_to_route") }

    LaunchedEffect(selectedGroup) {
        selectedGroup?.let { scheduleViewModel.loadSchedule(it.id) }
    }

    LaunchedEffect(lessonIdExtra) {
        lessonIdExtra?.let { lessonId ->
            navController.navigate("lesson/$lessonId")
        }
    }

    LaunchedEffect(routeExtra) {
        routeExtra?.let { route ->
            navController.navigate(route)
        }
    }

    val bottomTabRoutes = remember { listOf(Screen.Schedule.route, Screen.Rating.route, Screen.Sections.route) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Schedule.route,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            enterTransition = {
                val isTabSwitch = initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes
                if (isTabSwitch) com.mguuschedule.ui.theme.AppMotionScheme.tabEnterTransition()
                else com.mguuschedule.ui.theme.AppMotionScheme.detailEnterTransition()
            },
            exitTransition = {
                val isTabSwitch = initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes
                if (isTabSwitch) com.mguuschedule.ui.theme.AppMotionScheme.tabExitTransition()
                else com.mguuschedule.ui.theme.AppMotionScheme.detailBackgroundExitTransition()
            },
            popEnterTransition = {
                val isTabSwitch = initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes
                if (isTabSwitch) com.mguuschedule.ui.theme.AppMotionScheme.tabEnterTransition()
                else com.mguuschedule.ui.theme.AppMotionScheme.detailBackgroundPopEnterTransition()
            },
            popExitTransition = {
                val isTabSwitch = initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes
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
                        onOpenSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }
            }
            composable("curriculum") {
                val allLessons by scheduleViewModel.allLessonsFlow.collectAsState()
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
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
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
                    com.mguuschedule.ui.screens.MaterialsHubScreen(
                        allLessons = allLessons,
                        addonSummaries = addonSummaries,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("attendance") {
                val allLessons by scheduleViewModel.allLessonsFlow.collectAsState()
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
                    com.mguuschedule.ui.screens.AttendanceScreen(
                        allLessons = allLessons,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("notification_history") {
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
                    NotificationHistoryScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.Settings.route) {
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        scheduleViewModel = scheduleViewModel,
                        onNavigateToDebug = { navController.navigate(Screen.Debug.route) },
                        onOpenFeatureTour = { navController.navigate("feature_tour") }
                    )
                }
            }
            composable("feature_tour") {
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
                    FeatureTourScreen(
                        onFinish = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.Debug.route) {
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
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
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
                    val lessonId = backStackEntry.arguments?.getString("lessonId")
                    val lesson = lessonId?.let { scheduleViewModel.getLessonById(it) }
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
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
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
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
                    TodoScreen(
                        scheduleViewModel = scheduleViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Screen.Search.route) {
                ExpressiveDetailScreenContainer(onBack = { navController.popBackStack() }) {
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
            val isMainScreen = currentDestination?.route in bottomTabRoutes

            if (isMainScreen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(top = 32.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AnimatedFloatingNavBar(
                            items = items,
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
                                        Screen.Settings -> {
                                            profileViewModel.scrollToTop()
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
                            targetValue = if (isSearchPressed) 0.93f else 1f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 600f),
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
                                .scale(searchScale)
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

@Composable
fun ExpressiveDetailScreenContainer(
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    var backProgress by remember { mutableFloatStateOf(0f) }
    var swipeEdge by remember { mutableIntStateOf(androidx.activity.BackEventCompat.EDGE_LEFT) }
    var touchOffsetY by remember { mutableFloatStateOf(0f) }
    var startTouchY by remember { mutableFloatStateOf(0f) }

    androidx.activity.compose.PredictiveBackHandler(enabled = true) { progressFlow ->
        try {
            var isFirstEvent = true
            progressFlow.collect { backEvent ->
                if (isFirstEvent) {
                    startTouchY = backEvent.touchY
                    isFirstEvent = false
                }
                backProgress = backEvent.progress
                swipeEdge = backEvent.swipeEdge
                touchOffsetY = (backEvent.touchY - startTouchY) * 0.08f
            }
            // Пользователь завершил жест "Назад" — плавно доводим прогресс и вызываем onBack()
            animate(
                initialValue = backProgress,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
            ) { value, _ ->
                backProgress = value
            }
            onBack()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Пользователь отменил жест — возвращаем экран обратно на пружине MD3 Expressive
            scope.launch {
                animate(
                    initialValue = backProgress,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 260f)
                ) { value, _ ->
                    backProgress = value
                }
                touchOffsetY = 0f
            }
        }
    }

    // Нелинейное сглаживание прогресса по гайдлайнам Android 15/16 Predictive Back
    val easedProgress = FastOutSlowInEasing.transform(backProgress.coerceIn(0f, 1f))
    val scale = 1f - (easedProgress * 0.11f) // Сжатие до 89%
    val horizontalShiftDp = if (swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT) {
        22f * easedProgress
    } else {
        -22f * easedProgress
    }
    val cornerRadius = (28f * easedProgress).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = horizontalShiftDp.dp.toPx()
                translationY = touchOffsetY * easedProgress
                alpha = 1f - (easedProgress * 0.15f).coerceAtMost(0.15f)
                shape = RoundedCornerShape(cornerRadius)
                clip = easedProgress > 0.005f
            }
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}
