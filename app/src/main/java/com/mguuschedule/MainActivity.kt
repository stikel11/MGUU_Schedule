package com.mguuschedule

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        CrashHandler.init(applicationContext)
        
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
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

            LaunchedEffect(startAction) {
                when(startAction) {
                    "com.mguuschedule.ACTION_TOMORROW" -> {
                        scheduleViewModel.onDateSelected(LocalDate.now().plusDays(1))
                    }
                }
            }

            MGUUScheduleTheme(
                themeMode = profileViewModel.themeMode,
                dynamicColor = profileViewModel.dynamicColorEnabled
            ) {
                AnimatedContent(
                    targetState = selectedGroup,
                    transitionSpec = {
                        fadeIn(animationSpec = AppMotionScheme.defaultEffectsSpec()) togetherWith
                        fadeOut(animationSpec = AppMotionScheme.defaultEffectsSpec())
                    },
                    label = "MainAppTransition"
                ) { group ->
                    if (group == null) {
                        OnboardingScreen(
                            groupsUiState = profileViewModel.groupsUiState,
                            onRetry = { profileViewModel.loadGroups() },
                            onGroupSelected = { profileViewModel.selectGroup(it) }
                        )
                    } else {
                        MainAppScaffold(profileViewModel, scheduleViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppScaffold(profileViewModel: ProfileViewModel, scheduleViewModel: ScheduleViewModel) {
    val navController = rememberNavController()
    val items = listOf(Screen.Schedule, Screen.Rating, Screen.Settings)
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

    LaunchedEffect(selectedGroup) {
        selectedGroup?.let { scheduleViewModel.loadSchedule(it.id) }
    }

    LaunchedEffect(lessonIdExtra) {
        lessonIdExtra?.let { lessonId ->
            navController.navigate("lesson/$lessonId")
        }
    }

    val bottomTabRoutes = remember { listOf(Screen.Schedule.route, Screen.Rating.route, Screen.Settings.route) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Schedule.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                if (initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes) {
                    EnterTransition.None
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                }
            },
            exitTransition = {
                if (initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes) {
                    ExitTransition.None
                } else {
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                }
            },
            popEnterTransition = {
                if (initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes) {
                    EnterTransition.None
                } else {
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                }
            },
            popExitTransition = {
                if (initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes) {
                    ExitTransition.None
                } else {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + scaleOut(
                        targetScale = 0.85f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                }
            }
        ) {
                composable(Screen.Schedule.route) {
                    ScheduleScreen(
                        viewModel = scheduleViewModel,
                        unreadNotificationCount = unreadCount,
                        onNotificationHistoryClick = { navController.navigate("notification_history") },
                        onLessonClick = { lesson -> 
                            haptic.click()
                            navController.navigate("lesson/${lesson.id}") 
                        }
                    )
                }
                composable(Screen.Rating.route) {
                    RatingScreen()
                }
                composable("notification_history") {
                    NotificationHistoryScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Settings.route) {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        scheduleViewModel = scheduleViewModel,
                        onNavigateToDebug = { navController.navigate(Screen.Debug.route) }
                    )
                }
                composable(Screen.Debug.route) {
                    DebugScreen(
                        viewModel = profileViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.LessonDetail.route,
                    arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
                ) { backStackEntry ->
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

                composable(
                    route = Screen.TeacherProfile.route,
                    arguments = listOf(navArgument("teacherName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val teacherName = backStackEntry.arguments?.getString("teacherName")?.let {
                        URLDecoder.decode(it, "UTF-8")
                    } ?: "Неизвестный преподаватель"
                    
                    TeacherProfileScreen(
                        teacherName = teacherName,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.Search.route,
                    enterTransition = {
                        scaleIn(
                            initialScale = 0.1f,
                            transformOrigin = TransformOrigin(pivotFractionX = 0.88f, pivotFractionY = 0.95f),
                            animationSpec = AppMotionScheme.defaultSpatialSpec()
                        ) + fadeIn(animationSpec = AppMotionScheme.fastEffectsSpec())
                    },
                    exitTransition = {
                        scaleOut(
                            targetScale = 0.1f,
                            transformOrigin = TransformOrigin(pivotFractionX = 0.88f, pivotFractionY = 0.95f),
                            animationSpec = AppMotionScheme.defaultSpatialSpec()
                        ) + fadeOut(animationSpec = AppMotionScheme.fastEffectsSpec())
                    }
                ) {
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

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val isDetailScreen = currentDestination?.route?.contains("lesson/") == true
            val isSearchScreen = currentDestination?.route == Screen.Search.route
            val isNotificationScreen = currentDestination?.route == "notification_history"

            if (!isDetailScreen && !isSearchScreen && !isNotificationScreen) {
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
                                if (isSelected && screen == Screen.Schedule) {
                                    scheduleViewModel.resetToToday()
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

                        Surface(
                            onClick = {
                                haptic.lightTick()
                                navController.navigate(Screen.Search.route)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 3.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                            modifier = Modifier.size(56.dp)
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
