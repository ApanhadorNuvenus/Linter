package com.example.linter.presentation.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeListScreen(
    onVideoClick: (Long) -> Unit,
    viewModel: YoutubeListViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var urlInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadVideos() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("YouTube Видео") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Вставьте ссылку на YouTube") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            viewModel.addVideo(urlInput)
                            urlInput = ""
                        },
                        enabled = urlInput.isNotBlank() && !state.isLoading
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить")
                    }
                }
            )

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            state.error?.let { err ->
                Text(text = err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Сохраненные видео", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(state.videos) { video ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onVideoClick(video.id) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        // ИСПРАВЛЕНИЕ: Добавлен Row для отрисовки превью (thumbnail) и текста
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = "Превью",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(16f / 9f)
                                    .background(Color.LightGray)
                            )

                            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                Spacer(modifier = Modifier.height(8.dp))
                                val progress = if (video.durationMs > 0) (video.progressMs.toFloat() / video.durationMs.toFloat()) else 0f
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}