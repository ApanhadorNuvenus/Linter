package com.example.linter.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun EndOfPageDialog(
    unknownWords: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedLearningWords = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Делаем диалог шире
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
        title = { Text("Остались новые слова", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Отметьте слова (+), которые хотите добавить в карточки 'Учу'.\n" +
                            "Остальные слова будут автоматически отмечены как 'Знаю'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(unknownWords) { word ->
                        val isSelected = selectedLearningWords.contains(word)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedLearningWords.remove(word)
                                    else selectedLearningWords.add(word)
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = word,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedLearningWords) }) {
                Text("Подтвердить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}