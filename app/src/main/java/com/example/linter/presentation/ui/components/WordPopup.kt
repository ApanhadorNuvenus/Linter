package com.example.linter.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.presentation.ui.lecturedetail.PopupState

@Composable
fun TranslationCard(title: String, text: String, bgColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Замените только эту функцию в WordPopup.kt:
@Composable
fun MultiTranslationView(translations: MultiTranslation) {
    Column {
        translations.mlKit?.let { TranslationCard("Google ML Kit", it, Color(0xFFE3F2FD)) }
        translations.onnx?.let { TranslationCard("OPUS-MT (Локально)", it, Color(0xFFE8F5E9)) }
        translations.cloud?.let { TranslationCard("Облачный API", it, Color(0xFFFFF3E0)) }
    }
}

@Composable
fun WordPopup(
    state: PopupState,
    onStartLearning: (word: String, translations: MultiTranslation, context: String) -> Unit,
    onMarkAsKnown: (word: String, cardId: Long?) -> Unit,
    onMarkAsIgnored: (word: String) -> Unit,
    onChangeLearningStatus: (cardId: Long, word: String, status: LearningStatus) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is PopupState.Hidden) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (state) {
                    is PopupState.NewWord -> state.wordOrPhrase
                    is PopupState.LearningWord -> state.wordOrPhrase
                    else -> ""
                },
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        },
        text = {
            Column {
                when (state) {
                    is PopupState.NewWord -> {
                        MultiTranslationView(state.translations)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("\"${state.contextSentence}\"", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is PopupState.LearningWord -> {
                        state.meta.translations?.let { MultiTranslationView(it) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Текущий уровень: ${state.meta.learningStatus?.level ?: 1}", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LearningStatus.entries.forEach { status ->
                                FilterChip(
                                    selected = state.meta.learningStatus == status,
                                    onClick = { onChangeLearningStatus(state.meta.contextCardId!!, state.wordOrPhrase, status) },
                                    label = { Text(status.level.toString()) }
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            if (state is PopupState.NewWord) {
                Button(onClick = { onStartLearning(state.wordOrPhrase, state.translations, state.contextSentence) }) {
                    Text("Учу")
                }
            } else if (state is PopupState.LearningWord) {
                Button(onClick = { onMarkAsKnown(state.wordOrPhrase, state.meta.contextCardId) }) {
                    Text("✓ В Known")
                }
            }
        },
        dismissButton = {
            if (state is PopupState.NewWord) {
                Row {
                    TextButton(onClick = { onMarkAsIgnored(state.wordOrPhrase) }) { Text("Игнор") }
                    TextButton(onClick = { onMarkAsKnown(state.wordOrPhrase, null) }) { Text("✓ Знаю") }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}