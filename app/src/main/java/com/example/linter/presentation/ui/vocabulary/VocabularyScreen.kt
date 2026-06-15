package com.example.linter.presentation.ui.vocabulary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.linter.domain.model.LearningStatus
import com.example.linter.presentation.ui.components.WordPopup
import com.example.linter.presentation.ui.lecturedetail.PopupState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    viewModel: VocabularyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val filteredWords = remember(uiState.words, uiState.wordMetas, uiState.searchQuery, uiState.selectedLanguage, uiState.selectedStatus) {
        uiState.words.filter { item ->
            val matchesSearch = item.text.contains(uiState.searchQuery, ignoreCase = true)
            val meta = uiState.wordMetas[item.text.lowercase()]

            val matchesLanguage = when (uiState.selectedLanguage) {
                "All" -> true
                else -> meta?.language == uiState.selectedLanguage
            }

            val matchesStatus = when (uiState.selectedStatus) {
                "All" -> true
                else -> meta?.learningStatus?.name == uiState.selectedStatus
            }

            matchesSearch && matchesLanguage && matchesStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vocabulary Dictionary", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Поиск слов или выражений...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InputChip(
                    selected = uiState.selectedLanguage == "All",
                    onClick = { viewModel.onLanguageSelected("All") },
                    label = { Text("Все языки") }
                )
                InputChip(
                    selected = uiState.selectedLanguage == "en",
                    onClick = { viewModel.onLanguageSelected("en") },
                    label = { Text("English") }
                )
                InputChip(
                    selected = uiState.selectedLanguage == "fr",
                    onClick = { viewModel.onLanguageSelected("fr") },
                    label = { Text("Français") }
                )

                Spacer(modifier = Modifier.width(8.dp))

                InputChip(
                    selected = uiState.selectedStatus == "All",
                    onClick = { viewModel.onStatusSelected("All") },
                    label = { Text("Все статусы") }
                )
                LearningStatus.entries.forEach { status ->
                    InputChip(
                        selected = uiState.selectedStatus == status.name,
                        onClick = { viewModel.onStatusSelected(status.name) },
                        label = { Text(status.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else if (filteredWords.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Слова не найдены по заданным фильтрам", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredWords, key = { it.id }) { item ->
                        val meta = uiState.wordMetas[item.text.lowercase()]
                        val translation = meta?.translations?.custom
                            ?: meta?.translations?.mlKit
                            ?: meta?.translations?.onnx
                            ?: "Перевод не найден"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onWordClicked(item.text) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(translation, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, maxLines = 1)
                                }

                                meta?.learningStatus?.let { status ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(status.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.popupState !is PopupState.Hidden) {
            WordPopup(
                state = uiState.popupState,
                onStartLearning = { _, _, _ -> },
                onMarkAsKnown = { word, cardId -> viewModel.onMarkAsKnown(word, cardId) },
                onMarkAsIgnored = { _ -> },
                onChangeLearningStatus = { cardId, word, status -> viewModel.onChangeLearningStatus(cardId, word, status) },
                onPlayTts = { word -> viewModel.playTts(word) }, // НОВОЕ
                onDismiss = { viewModel.dismissPopup() },
                onSaveCustomTranslation = { cardId, word, translation ->
                    viewModel.onSaveCustomTranslation(cardId, word, translation)
                }
            )
        }
    }
}