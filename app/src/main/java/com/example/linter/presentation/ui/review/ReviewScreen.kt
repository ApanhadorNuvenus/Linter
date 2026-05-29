package com.example.linter.presentation.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete // Иконка корзины для удаления
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.linter.domain.model.UiWordStatus
import com.example.linter.domain.repository.ReviewItem
import com.example.linter.presentation.ui.components.WordPopup
import com.example.linter.presentation.ui.components.TranslationCard
import com.example.linter.presentation.ui.lecturedetail.PopupState
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onFinish: () -> Unit,
    viewModel: ReviewViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Повторение (${viewModel.selectedLanguage.uppercase()})", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))

                        if (!uiState.isFinished && !uiState.isLoading) {
                            Text(
                                text = "${uiState.blueCount}",
                                color = Color(0xFF2196F3),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textDecoration = if (uiState.currentBucket == CardBucket.BLUE) TextDecoration.Underline else null
                            )
                            Text(text = " • ", color = Color.LightGray, fontSize = 16.sp)
                            Text(
                                text = "${uiState.redCount}",
                                color = Color(0xFFF44336),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textDecoration = if (uiState.currentBucket == CardBucket.RED) TextDecoration.Underline else null
                            )
                            Text(text = " • ", color = Color.LightGray, fontSize = 16.sp)
                            Text(
                                text = "${uiState.greenCount}",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textDecoration = if (uiState.currentBucket == CardBucket.GREEN) TextDecoration.Underline else null
                            )
                        }
                    }
                },
                navigationIcon = { TextButton(onClick = onFinish) { Text("Закрыть") } },
                actions = {
                    if (!uiState.isFinished && !uiState.isLoading && uiState.currentItem != null) {
                        // Кнопка УДАЛЕНИЯ карточки
                        IconButton(onClick = { viewModel.deleteCurrentCard() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить карточку",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }

                        IconButton(onClick = { viewModel.postponeCurrentCard() }) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Отложить карточку",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.isFinished -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("На сегодня всё!", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onFinish) { Text("Вернуться") }
                    }
                }
                uiState.currentItem != null -> {
                    ReviewCard(
                        item = uiState.currentItem!!,
                        showAnswer = uiState.showAnswer,
                        selectionRange = uiState.selectionRange,
                        onShowAnswer = { viewModel.revealAnswer() },
                        onSubmitGrade = { grade -> viewModel.submitGrade(grade) },
                        onWordClick = { offset -> viewModel.onWordClicked(offset) },
                        onSelectionStart = { offset -> viewModel.onSelectionStart(offset) },
                        onSelectionDrag = { offset -> viewModel.onSelectionDrag(offset) },
                        onSelectionEnd = { viewModel.onSelectionEnd() },
                        onClearSelection = { viewModel.clearSelection() },
                        onSaveCustomTranslation = { cardId, word, translation ->
                            viewModel.onSaveCustomTranslation(cardId, word, translation)
                        }
                    )
                }
            }
        }

        if (uiState.popupState !is PopupState.Hidden) {
            WordPopup(
                state = uiState.popupState,
                onStartLearning = { word, trans, context -> viewModel.onStartLearning(word, trans, context) },
                onMarkAsKnown = { word, cardId -> viewModel.onMarkAsKnown(word, cardId) },
                onMarkAsIgnored = { word -> viewModel.onMarkAsIgnored(word) },
                onChangeLearningStatus = { cardId, word, status -> viewModel.onChangeLearningStatus(cardId, word, status) },
                onDismiss = { viewModel.dismissPopup() },
                onSaveCustomTranslation = { cardId, word, translation ->
                    viewModel.onSaveCustomTranslation(cardId, word, translation)
                }
            )
        }
    }
}

@Composable
fun ReviewCard(
    item: ReviewItem,
    showAnswer: Boolean,
    selectionRange: IntRange?,
    onShowAnswer: () -> Unit,
    onSubmitGrade: (com.example.linter.data.fsrs.Grade) -> Unit,
    onWordClick: (Int) -> Unit,
    onSelectionStart: (Int) -> Unit,
    onSelectionDrag: (Int) -> Unit,
    onSelectionEnd: () -> Unit,
    onClearSelection: () -> Unit,
    onSaveCustomTranslation: (Long, String, String) -> Unit
) {
    val trimmedContextResult = remember(item.contextSentence, item.word) {
        getTrimmedContext(item.contextSentence, item.word, maxWordsAround = 4)
    }

    // ИСПРАВЛЕНИЕ: Математический расчет сдвига (shift) для предотвращения смещения выделения текста
    val textShift = remember(trimmedContextResult, item.targetWordRange) {
        val originalStart = item.targetWordRange?.first ?: 0
        val trimmedStart = trimmedContextResult.targetRange?.first ?: 0
        originalStart - trimmedStart
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        val annotatedContext = buildAnnotatedString {
            append(trimmedContextResult.text)

            // Подсветка окружающих известных слов
            item.tokens.filter { it.isWord }.forEach { token ->
                val meta = item.wordMeta[token.value.lowercase()]
                val bgColor = getWordColor(meta?.status)
                if (bgColor != Color.Transparent) {
                    val indexInTrimmed = trimmedContextResult.text.indexOf(token.value, ignoreCase = true)
                    if (indexInTrimmed != -1) {
                        addStyle(SpanStyle(background = bgColor), indexInTrimmed, indexInTrimmed + token.value.length)
                    }
                }
            }

            trimmedContextResult.targetRange?.let { range ->
                addStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    range.first,
                    range.last + 1
                )
            }

            // ИСПРАВЛЕНИЕ: Визуальная маска выделения теперь рендерится с учетом обратного математического сдвига trimmed-текста
            if (selectionRange != null) {
                val trimmedStart = max(0, selectionRange.first - textShift)
                val trimmedEnd = min(trimmedContextResult.text.length, selectionRange.last + 1 - textShift)
                if (trimmedStart < trimmedEnd) {
                    addStyle(
                        style = SpanStyle(background = Color.Gray.copy(alpha = 0.4f)),
                        start = trimmedStart,
                        end = trimmedEnd
                    )
                }
            }
        }

        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        Text(
            text = annotatedContext,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier
                .pointerInput(trimmedContextResult.text) {
                    detectTapGestures(
                        onTap = { pos ->
                            textLayoutResult?.getOffsetForPosition(pos)?.let { offset ->
                                val originalOffset = offset + textShift
                                val targetRange = item.targetWordRange

                                // ИСПРАВЛЕНИЕ: Блокировка нажатия на само тестируемое слово
                                if (targetRange == null || originalOffset !in targetRange) {
                                    onWordClick(originalOffset)
                                }
                            }
                        }
                    )
                }
                .pointerInput(trimmedContextResult.text) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            textLayoutResult?.getOffsetForPosition(pos)?.let { offset ->
                                val originalOffset = offset + textShift
                                val targetRange = item.targetWordRange

                                // ИСПРАВЛЕНИЕ: Запрет драга по целевому слову
                                if (targetRange == null || originalOffset !in targetRange) {
                                    onSelectionStart(originalOffset)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            textLayoutResult?.getOffsetForPosition(change.position)?.let { offset ->
                                val originalOffset = offset + textShift
                                onSelectionDrag(originalOffset)
                            }
                        },
                        onDragEnd = {
                            val targetRange = item.targetWordRange

                            // Блокировка перевода словосочетаний, задевающих целевое слово
                            val isCheating = targetRange != null && selectionRange != null && (
                                    selectionRange.first in targetRange || selectionRange.last in targetRange
                                    )

                            if (!isCheating) {
                                onSelectionEnd()
                            } else {
                                onClearSelection()
                            }
                        },
                        onDragCancel = { onClearSelection() }
                    )
                }
        )

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = item.word, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(32.dp))

        if (showAnswer) {
            HorizontalDivider(modifier = Modifier.fillMaxWidth(0.8f))
            Spacer(modifier = Modifier.height(16.dp))

            var isEditing by remember { mutableStateOf(false) }
            var editInput by remember { mutableStateOf("") }
            var showAlternatives by remember { mutableStateOf(false) }

            LaunchedEffect(item) {
                isEditing = false
                editInput = ""
                showAlternatives = false
            }

            val activeTranslationText = item.translations.custom
                ?: item.translations.mlKit
                ?: item.translations.onnx
                ?: "Перевод отсутствует"

            Column(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editInput,
                        onValueChange = { editInput = it },
                        label = { Text("Корректировка перевода") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onSaveCustomTranslation(item.contextCardId, item.word, editInput)
                                    isEditing = false
                                }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Save")
                            }
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TranslationCard(
                                title = if (item.translations.custom != null) "Ваш перевод" else "Активный перевод",
                                text = activeTranslationText,
                                icon = "✍️",
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            editInput = activeTranslationText
                            isEditing = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Translation")
                        }
                    }
                }

                TextButton(
                    onClick = { showAlternatives = !showAlternatives },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(if (showAlternatives) "Скрыть альтернативы" else "Альтернативные автопереводы")
                }

                AnimatedVisibility(visible = showAlternatives) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.translations.mlKit?.let { ml ->
                            if (ml != activeTranslationText) {
                                TranslationCard("Google ML Kit", ml, "☁️", Color(0xFF1D4ED8))
                            }
                        }
                        item.translations.onnx?.let { onnx ->
                            if (onnx != activeTranslationText) {
                                TranslationCard("OPUS-MT (Офлайн)", onnx, "🤖", Color(0xFF047857))
                            }
                        }
                        item.translations.cloud?.let { cloud ->
                            TranslationCard("Cloud API", cloud, "⚡", Color(0xFFB45309))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1.5f))

        if (!showAnswer) {
            Button(onClick = onShowAnswer, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Показать ответ", fontSize = 18.sp)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                item.grades.forEach { grade ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = grade.txt, fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { onSubmitGrade(grade) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(grade.title)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun getWordColor(status: UiWordStatus?): Color {
    return when (status) {
        UiWordStatus.BLUE, null -> Color(0xFFE3F2FD)
        UiWordStatus.YELLOW -> Color(0xFFFFF9C4)
        UiWordStatus.TRANSPARENT -> Color.Transparent
    }
}

data class TrimmedContext(
    val text: String,
    val targetRange: IntRange?
)

fun getTrimmedContext(
    contextSentence: String,
    targetWord: String,
    maxWordsAround: Int = 4
): TrimmedContext {
    if (contextSentence.isBlank()) {
        return TrimmedContext("", null)
    }

    // НЕ вызываем .trim() и .replace(), чтобы сохранить оригинальную длину строки и индексы
    val targetIndex = contextSentence.indexOf(targetWord, ignoreCase = true)

    if (targetIndex == -1) {
        return TrimmedContext(contextSentence, null)
    }

    val targetLength = targetWord.length
    val targetEndIndex = targetIndex + targetLength

    val leftPart = contextSentence.substring(0, targetIndex)
    val leftWords = leftPart.split(Regex("\\s+")).filter { it.isNotEmpty() }

    val rightPart = contextSentence.substring(targetEndIndex)
    val rightWords = rightPart.split(Regex("\\s+")).filter { it.isNotEmpty() }

    val leftTrimmed = leftWords.takeLast(maxWordsAround)
    val rightTrimmed = rightWords.take(maxWordsAround)

    val sb = java.lang.StringBuilder()

    val hasLeftTrim = leftWords.size > maxWordsAround
    if (hasLeftTrim) {
        sb.append("... ")
    }

    val leftWordsStr = leftTrimmed.joinToString(" ")
    if (leftWordsStr.isNotEmpty()) {
        sb.append(leftWordsStr).append(" ")
    }

    val newTargetStart = sb.length
    sb.append(contextSentence.substring(targetIndex, targetEndIndex))
    val newTargetEnd = sb.length

    val rightWordsStr = rightTrimmed.joinToString(" ")
    if (rightWordsStr.isNotEmpty()) {
        sb.append(" ").append(rightWordsStr)
    }

    val hasRightTrim = rightWords.size > maxWordsAround
    if (hasRightTrim) {
        sb.append(" ...")
    }

    return TrimmedContext(
        text = sb.toString(),
        targetRange = newTargetStart until newTargetEnd
    )
}