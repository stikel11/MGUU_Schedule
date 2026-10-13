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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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
                            val ratingViewModel: RatingViewModel = viewModel(
                                factory = RatingViewModelFactory(app)
                            )
                            MainAppScaffold(profileViewModel, scheduleViewModel, ratingViewModel)
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

    val PredictiveBackEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Schedule.route,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background), // Убираем Color.Black, чтобы экран никогда не гас!
            enterTransition = {
                val isTabSwitch = initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes
                if (isTabSwitch) {
                    // Плавное появление вкладки БЕЗ задержки и БЕЗ изменения масштаба
                    fadeIn(animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing))
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        initialOffset = { fullWidth -> (fullWidth * 0.14f).toInt() },
                        animationSpec = tween(durationMillis = 300, easing = PredictiveBackEasing)
                    ) + fadeIn(animationSpec = tween(durationMillis = 120, easing = LinearEasing))
                }
            },
            exitTransition = {
                val isTabSwitch = initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes
                if (isTabSwitch) {
                    // Мягкое затухание старой вкладки БЕЗ изменения масштаба
                    fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing))
                } else {
                    // Нижний экран слегка отдаляется без ухода в темноту
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(durationMillis = 300, easing = PredictiveBackEasing)
                    )
                }
            },
            popEnterTransition = {
                val isTabSwitch = initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes
                if (isTabSwitch) {
                    // Возврат на вкладку «Расписание» БЕЗ scaleIn и БЕЗ delayMillis (не отдаляется и не гаснет!)
                    fadeIn(animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing))
                } else {
                    // Возврат из детального экрана: нижний экран просто восстанавливает масштаб 0.96f -> 1.0f
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(durationMillis = 320, easing = PredictiveBackEasing)
                    )
                }
            },
            popExitTransition = {
                val isTabSwitch = initialState.destination.route in bottomTabRoutes && targetState.destination.route in bottomTabRoutes
                if (isTabSwitch) {
                    // Уход с вкладки «Рейтинг»/«Настройки» на «Расписание» БЕЗ scaleOut и БЕЗ задержки
                    fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing))
                } else {
                    // Predictive Back только для детальных экранов (Информация о паре, Поиск и т.д.)
                    scaleOut(
                        targetScale = 0.88f,
                        animationSpec = tween(durationMillis = 320, easing = PredictiveBackEasing)
                    ) + slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        targetOffset = { fullWidth -> (fullWidth * 0.05f).toInt() },
                        animationSpec = tween(durationMillis = 320, easing = PredictiveBackEasing)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 90, delayMillis = 230, easing = LinearEasing)
                    )
                }
            }
        ) {
                composable(Screen.Schedule.route) {
                    ExpressiveScreenContainer(isDetailScreen = false) {
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
                }
                composable(Screen.Rating.route) {
                    ExpressiveScreenContainer(isDetailScreen = false) {
                        RatingScreen(viewModel = ratingViewModel)
                    }
                }
                composable("notification_history") {
                    ExpressiveScreenContainer(isDetailScreen = true) {
                        NotificationHistoryScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(Screen.Settings.route) {
                    ExpressiveScreenContainer(isDetailScreen = false) {
                        ProfileScreen(
                            viewModel = profileViewModel,
                            scheduleViewModel = scheduleViewModel,
                            onNavigateToDebug = { navController.navigate(Screen.Debug.route) }
                        )
                    }
                }
                composable(Screen.Debug.route) {
                    ExpressiveScreenContainer(isDetailScreen = true) {
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
                    ExpressiveScreenContainer(isDetailScreen = true) {
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
                    ExpressiveScreenContainer(isDetailScreen = true) {
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
                    ExpressiveScreenContainer(isDetailScreen = true) {
                        TodoScreen(
                            scheduleViewModel = scheduleViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(Screen.Search.route) {
                    ExpressiveScreenContainer(isDetailScreen = true) {
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
private fun AnimatedVisibilityScope.ExpressiveScreenContainer(
    isDetailScreen: Boolean = true,
    content: @Composable () -> Unit
) {
    val isTransitioning = transition.isRunning || transition.currentState != transition.targetState
    val cornerRadius by animateDpAsState(
        targetValue = if (isDetailScreen && isTransitioning) 28.dp else 0.dp,
        animationSpec = tween(durationMillis = 120),
        label = "predictive_corner_radius"
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = if (isDetailScreen && isTransitioning) 12.dp else 0.dp
    ) {
        content()
    }
}
