package com.example.linter.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.presentation.ui.lecturedetail.PopupState

@Composable
fun TranslationCard(title: String, text: String, icon: String, accentColor: Color) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun MultiTranslationView(translations: MultiTranslation) {
    val dark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        translations.mlKit?.let {
            TranslationCard("Google ML Kit", it, "☁️", if (dark) Color(0xFF60A5FA) else Color(0xFF1D4ED8))
        }
        translations.onnx?.let {
            TranslationCard("OPUS-MT (Офлайн)", it, "🤖", if (dark) Color(0xFF34D399) else Color(0xFF047857))
        }
        translations.cloud?.let {
            TranslationCard("Cloud API", it, "⚡", if (dark) Color(0xFFFBBF24) else Color(0xFFB45309))
        }
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Заголовок слова
                val title = when (state) {
                    is PopupState.NewWord -> state.wordOrPhrase
                    is PopupState.LearningWord -> state.wordOrPhrase
                    else -> ""
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Контекст / Переводы
                when (state) {
                    is PopupState.NewWord -> {
                        MultiTranslationView(state.translations)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Контекстное предложение в виде цитаты
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "\"${state.contextSentence}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is PopupState.LearningWord -> {
                        state.meta.translations?.let { MultiTranslationView(it) }
                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Learning Level",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Дизайнерский Сегментированный переключатель уровня
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LearningStatus.entries.forEach { status ->
                                val isSelected = state.meta.learningStatus == status
                                val animatedColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    label = "tabBg"
                                )
                                val textColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    label = "tabText"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(animatedColor)
                                        .clickable {
                                            onChangeLearningStatus(state.meta.contextCardId!!, state.wordOrPhrase, status)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = status.name.take(4), // NEW, RECO, FAMI, LEAR
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Действия
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state is PopupState.NewWord) {
                        TextButton(onClick = { onMarkAsIgnored(state.wordOrPhrase) }) {
                            Text("Ignore", color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onMarkAsKnown(state.wordOrPhrase, null) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("I Know It")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onStartLearning(state.wordOrPhrase, state.translations, state.contextSentence) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Learn")
                        }
                    } else if (state is PopupState.LearningWord) {
                        TextButton(onClick = onDismiss) {
                            Text("Close", color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onMarkAsKnown(state.wordOrPhrase, state.meta.contextCardId) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("✓ Known")
                        }
                    }
                }
            }
        }
    }
}