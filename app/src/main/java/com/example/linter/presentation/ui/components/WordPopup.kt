package com.example.linter.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.linter.domain.model.Familiarity

@Composable
fun WordPopup(
    word: String,
    translation: String,
    familiarity: Familiarity,
    onFamiliarityChange: (Familiarity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word) },
        text = {
            Column {
                Text("Перевод: $translation")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Знание:")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Familiarity.entries.forEach { f ->
                        FilterChip(
                            selected = familiarity == f,
                            onClick = { onFamiliarityChange(f) },
                            label = {
                                Text(
                                    when (f) {
                                        Familiarity.UNKNOWN -> "Не знакомо"
                                        Familiarity.FAMILIAR -> "Знакомо"
                                        Familiarity.LEARNING -> "Учу"
                                        Familiarity.IGNORED -> "Игнор"
                                    }
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}