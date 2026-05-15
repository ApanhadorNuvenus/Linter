package com.example.linter.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.linter.presentation.ui.lecturelist.LectureListScreen
import com.example.linter.presentation.ui.createlecture.CreateLectureScreen
import com.example.linter.presentation.ui.lecturedetail.LectureDetailScreen

sealed class Screen(val route: String) {
    object LectureList : Screen("lecture_list")
    object CreateLecture : Screen("create_lecture")
    object LectureDetail : Screen("lecture_detail/{lectureId}") {
        fun createRoute(id: Long) = "lecture_detail/$id"
    }
    object Review : Screen("review")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.LectureList.route) {
        composable(Screen.LectureList.route) {
            LectureListScreen(
                onNavigateToCreate = { navController.navigate(Screen.CreateLecture.route) },
                onNavigateToReview = { navController.navigate(Screen.Review.route) }, // ТУТ
                onLectureClick = { id -> navController.navigate(Screen.LectureDetail.createRoute(id)) }
            )
        }
        composable(Screen.Review.route) {
            com.example.linter.presentation.ui.review.ReviewScreen(onFinish = { navController.popBackStack() })
        }
        composable(Screen.CreateLecture.route) {
            CreateLectureScreen(onCreated = { navController.popBackStack() })
        }
        composable(Screen.LectureDetail.route) { backStackEntry ->
            val lectureId = backStackEntry.arguments?.getString("lectureId")?.toLongOrNull() ?: return@composable
            LectureDetailScreen(lectureId = lectureId, onBack = { navController.popBackStack() })
        }
    }
}