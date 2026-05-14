package com.example.linter.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.linter.domain.model.LearningStatus
import com.example.linter.presentation.ui.lecturedetail.PopupState

@Composable
fun WordPopup(
    state: PopupState,
    onStartLearning: (word: String, translation: String, context: String) -> Unit,
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
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                when (state) {
                    is PopupState.NewWord -> {
                        Text("Перевод: ${state.translation}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("\"${state.contextSentence}\"", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is PopupState.LearningWord -> {
                        Text("Перевод: ${state.meta.translation}", style = MaterialTheme.typography.bodyLarge)
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
                Button(onClick = { onStartLearning(state.wordOrPhrase, state.translation, state.contextSentence) }) {
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