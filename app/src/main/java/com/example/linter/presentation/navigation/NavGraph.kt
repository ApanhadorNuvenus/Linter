package com.example.linter.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search // Используем иконку лупы для Вкладки Словаря
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.linter.presentation.ui.lecturelist.LectureListScreen
import com.example.linter.presentation.ui.createlecture.CreateLectureScreen
import com.example.linter.presentation.ui.lecturedetail.LectureDetailScreen
import com.example.linter.presentation.ui.youtube.YoutubeListScreen
import com.example.linter.presentation.ui.youtube.YoutubeDetailScreen
import com.example.linter.presentation.ui.vocabulary.VocabularyScreen // Подключаем Словарь

sealed class Screen(val route: String, val title: String = "") {
    object LectureList : Screen("lecture_list", "Тексты")
    object YoutubeList : Screen("youtube_list", "YouTube")
    object Vocabulary : Screen("vocabulary", "Словарь") // Новая вкладка

    object CreateLecture : Screen("create_lecture")
    object LectureDetail : Screen("lecture_detail/{lectureId}") {
        fun createRoute(id: Long) = "lecture_detail/$id"
    }
    object YoutubeDetail : Screen("youtube_detail/{videoId}") {
        fun createRoute(id: Long) = "youtube_detail/$id"
    }
    object Review : Screen("review/{lang}") { // Добавлен обязательный параметр lang
        fun createRoute(lang: String) = "review/$lang"
    }
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    // Тройная навигация: Тексты, YouTube и Словарь
    val bottomNavItems = listOf(Screen.LectureList, Screen.YoutubeList, Screen.Vocabulary)

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            if (currentDestination?.route in bottomNavItems.map { it.route }) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = when (screen) {
                                        Screen.LectureList -> Icons.Default.List
                                        Screen.YoutubeList -> Icons.Default.PlayArrow
                                        else -> Icons.Default.Search // Лупа для Словаря
                                    },
                                    contentDescription = null
                                )
                            },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.LectureList.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.LectureList.route) {
                LectureListScreen(
                    onNavigateToCreate = { navController.navigate(Screen.CreateLecture.route) },
                    onNavigateToReview = { lang -> navController.navigate(Screen.Review.createRoute(lang)) }, // Передаем выбранный язык
                    onLectureClick = { id -> navController.navigate(Screen.LectureDetail.createRoute(id)) }
                )
            }
            composable(Screen.YoutubeList.route) {
                YoutubeListScreen(
                    onVideoClick = { id -> navController.navigate(Screen.YoutubeDetail.createRoute(id)) }
                )
            }
            composable(Screen.Vocabulary.route) {
                VocabularyScreen() // Новая вкладка Словаря
            }
            composable(
                route = Screen.Review.route,
                arguments = listOf(navArgument("lang") { type = NavType.StringType }) // Парсинг lang
            ) {
                com.example.linter.presentation.ui.review.ReviewScreen(onFinish = { navController.popBackStack() })
            }
            composable(Screen.CreateLecture.route) {
                CreateLectureScreen(onCreated = { navController.popBackStack() })
            }
            composable(Screen.LectureDetail.route) { backStackEntry ->
                val lectureId = backStackEntry.arguments?.getString("lectureId")?.toLongOrNull() ?: return@composable
                LectureDetailScreen(lectureId = lectureId, onBack = { navController.popBackStack() })
            }
            composable(Screen.YoutubeDetail.route) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId")?.toLongOrNull() ?: return@composable
                YoutubeDetailScreen(videoId = videoId, onBack = { navController.popBackStack() })
            }
        }
    }
}