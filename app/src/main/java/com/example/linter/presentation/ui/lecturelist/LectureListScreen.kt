package com.example.linter.presentation.ui.lecturelist

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LectureListScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToReview: () -> Unit, // НОВЫЙ КОЛЛБЭК
    onLectureClick: (Long) -> Unit,
    viewModel: LectureListViewModel = viewModel()
) {
    val lectures by viewModel.lectures.collectAsState()
    val dueCount by viewModel.dueCardsCount.collectAsState() // СЧЁТЧИК

    LaunchedEffect(Unit) {
        viewModel.loadLectures()
        viewModel.loadDueCount()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onNavigateToCreate) {
                Text("Создать лекцию")
            }

            // КНОПКА ПОВТОРЕНИЯ С БЕЙДЖЕМ
            BadgedBox(
                badge = {
                    if (dueCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) { Text(dueCount.toString()) }
                    }
                }
            ) {
                FilledTonalButton(onClick = onNavigateToReview, enabled = dueCount > 0) {
                    Text("Повторить карточки")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Мои лекции", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        lectures.forEach { lecture ->
            OutlinedButton(
                onClick = { onLectureClick(lecture.id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(lecture.title)
            }
        }
    }
}