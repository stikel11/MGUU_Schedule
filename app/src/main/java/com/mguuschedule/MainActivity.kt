package com.mguuschedule

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
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
import com.mguuschedule.util.rememberHapticFeedback
import com.mguuschedule.util.NotificationHelper
import com.mguuschedule.util.ShortcutHelper
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        CrashHandler.init(applicationContext)
        
        val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                detectDarkMode = { isDark }
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                detectDarkMode = { isDark }
            )
        )

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        
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
            val repository = ScheduleRepository(app, database.scheduleDao())
            
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
    val items = listOf(Screen.Schedule, Screen.Profile)
    val selectedGroup = profileViewModel.selectedGroup
    val haptic = rememberHapticFeedback()

    val currentContext = LocalContext.current
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController,
                startDestination = Screen.Schedule.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(animationSpec = AppMotionScheme.fastEffectsSpec()) },
                exitTransition = { fadeOut(animationSpec = AppMotionScheme.fastEffectsSpec()) },
                popEnterTransition = { fadeIn(animationSpec = AppMotionScheme.fastEffectsSpec()) },
                popExitTransition = { fadeOut(animationSpec = AppMotionScheme.fastEffectsSpec()) }
            ) {
                composable(Screen.Schedule.route) {
                    ScheduleScreen(
                        viewModel = scheduleViewModel,
                        onLessonClick = { lesson -> 
                            haptic.click()
                            navController.navigate("lesson/${lesson.id}") 
                        }
                    )
                }
                composable(Screen.Profile.route) {
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
                        }
                    )
                }
            }

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val isDetailScreen = currentDestination?.route?.contains("lesson/") == true
            val isSearchScreen = currentDestination?.route == Screen.Search.route

            if (!isDetailScreen && !isSearchScreen) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
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
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 4.dp,
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
