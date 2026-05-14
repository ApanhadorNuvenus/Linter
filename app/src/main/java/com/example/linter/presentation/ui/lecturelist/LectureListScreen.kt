package com.example.linter.presentation.ui.lecturelist

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LectureListScreen(
    onNavigateToCreate: () -> Unit,
    onLectureClick: (Long) -> Unit,
    viewModel: LectureListViewModel = viewModel()
) {
    val lectures by viewModel.lectures.collectAsState()

    // ДОБАВИТЬ ЭТОТ БЛОК:
    // Он будет срабатывать каждый раз, когда мы возвращаемся на этот экран
    LaunchedEffect(Unit) {
        viewModel.loadLectures()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = onNavigateToCreate) {
            Text("Создать лекцию")
        }
        Spacer(modifier = Modifier.height(8.dp))
        lectures.forEach { lecture ->
            TextButton(onClick = { onLectureClick(lecture.id) }) {
                Text(lecture.title) // Если title пустой, кнопка может быть не видна, проверьте, что вводите заголовок!
            }
        }
    }
}