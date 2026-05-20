package com.example.linter.presentation.ui.lecturelist

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LectureListScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToReview: () -> Unit,
    onLectureClick: (Long) -> Unit,
    viewModel: LectureListViewModel = viewModel()
) {
    val lectures by viewModel.lectures.collectAsState()
    val dueCount by viewModel.dueCardsCount.collectAsState()

    // Локальное сохранение настроек
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("linter_settings", Context.MODE_PRIVATE) }

    var showSettingsCard by remember { mutableStateOf(false) }
    var showMlKit by remember { mutableStateOf(prefs.getBoolean("pref_show_ml_kit", true)) }
    var showOnnx by remember { mutableStateOf(prefs.getBoolean("pref_show_onnx", true)) }
    var showCloud by remember { mutableStateOf(prefs.getBoolean("pref_show_cloud", true)) }

    LaunchedEffect(Unit) {
        viewModel.loadLectures()
        viewModel.loadDueCount()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onNavigateToCreate) {
                    Text("Создать лекцию")
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Иконка шестеренки настроек
                IconButton(onClick = { showSettingsCard = !showSettingsCard }) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки")
                }
            }

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

        // Выдвижная панель глобальных настроек перевода
        AnimatedVisibility(visible = showSettingsCard) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Отображение переводов в словаре",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Google ML Kit", modifier = Modifier.weight(1f))
                        Switch(checked = showMlKit, onCheckedChange = {
                            showMlKit = it
                            prefs.edit().putBoolean("pref_show_ml_kit", it).apply()
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("OPUS-MT (Локальная модель ONNX)", modifier = Modifier.weight(1f))
                        Switch(checked = showOnnx, onCheckedChange = {
                            showOnnx = it
                            prefs.edit().putBoolean("pref_show_onnx", it).apply()
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Облачный перевод (Stub)", modifier = Modifier.weight(1f))
                        Switch(checked = showCloud, onCheckedChange = {
                            showCloud = it
                            prefs.edit().putBoolean("pref_show_cloud", it).apply()
                        })
                    }
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