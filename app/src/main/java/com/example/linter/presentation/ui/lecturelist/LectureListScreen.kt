package com.example.linter.presentation.ui.lecturelist

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.linter.domain.model.Lecture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectureListScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToReview: (String) -> Unit, // Изменен тип: передаем язык во время перехода
    onLectureClick: (Long) -> Unit,
    viewModel: LectureListViewModel = viewModel()
) {
    val lectures by viewModel.lectures.collectAsState()
    val enDueCount by viewModel.enDueCount.collectAsState()
    val frDueCount by viewModel.frDueCount.collectAsState()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onNavigateToCreate, shape = RoundedCornerShape(12.dp)) {
                Text("Создать лекцию")
            }
            IconButton(onClick = { showSettingsCard = !showSettingsCard }) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки")
            }
        }

        // Шторка настроек
        AnimatedVisibility(visible = showSettingsCard) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dictionary Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Google ML Kit", modifier = Modifier.weight(1f))
                        Switch(checked = showMlKit, onCheckedChange = {
                            showMlKit = it
                            prefs.edit().putBoolean("pref_show_ml_kit", it).apply()
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("OPUS-MT (Local ONNX)", modifier = Modifier.weight(1f))
                        Switch(checked = showOnnx, onCheckedChange = {
                            showOnnx = it
                            prefs.edit().putBoolean("pref_show_onnx", it).apply()
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cloud Translation", modifier = Modifier.weight(1f))
                        Switch(checked = showCloud, onCheckedChange = {
                            showCloud = it
                            prefs.edit().putBoolean("pref_show_cloud", it).apply()
                        })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ИНТЕРАКТИВНЫЙ ДАШБОРД СРС-ПОВТОРЕНИЯ ПО ЯЗЫКАМ
        Text("Сессии повторения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (enDueCount == 0 && frDueCount == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("🎉 На сегодня все карточки разобраны!", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Карточка English
                if (enDueCount > 0) {
                    Card(
                        modifier = Modifier.weight(1f).clickable { onNavigateToReview("en") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("ENGLISH", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("$enDueCount", fontSize = 36.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("карточек", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
                // Карточка French
                if (frDueCount > 0) {
                    Card(
                        modifier = Modifier.weight(1f).clickable { onNavigateToReview("fr") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("FRENCH", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("$frDueCount", fontSize = 36.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("карточек", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Моя библиотека лекций", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (lectures.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("У вас пока нет лекций.\nНажмите на кнопку выше, чтобы импортировать первый текст.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(lectures, key = { it.id }) { lecture ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onLectureClick(lecture.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(lecture.language.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(lecture.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}