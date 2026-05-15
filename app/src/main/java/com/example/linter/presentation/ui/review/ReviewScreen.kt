package com.example.linter.presentation.ui.review

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
                        Text("Повторение", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(16.dp))

                        // СЧЁТЧИКИ С ПОДЧЁРКИВАНИЕМ ТЕКУЩЕГО
                        if (!uiState.isFinished && !uiState.isLoading) {
                            Text(
                                text = "${uiState.blueCount}",
                                color = Color(0xFF2196F3),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                textDecoration = if (uiState.currentBucket == CardBucket.BLUE) TextDecoration.Underline else null
                            )
                            Text(text = " • ", color = Color.LightGray, fontSize = 18.sp)
                            Text(
                                text = "${uiState.redCount}",
                                color = Color(0xFFF44336),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                textDecoration = if (uiState.currentBucket == CardBucket.RED) TextDecoration.Underline else null
                            )
                            Text(text = " • ", color = Color.LightGray, fontSize = 18.sp)
                            Text(
                                text = "${uiState.greenCount}",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                textDecoration = if (uiState.currentBucket == CardBucket.GREEN) TextDecoration.Underline else null
                            )
                        }
                    }
                },
                navigationIcon = { TextButton(onClick = onFinish) { Text("Закрыть") } }
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
                        onClearSelection = { viewModel.clearSelection() }
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
                onDismiss = { viewModel.dismissPopup() }
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
    onClearSelection: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        val annotatedContext = buildAnnotatedString {
            append(item.contextSentence)

            item.tokens.filter { it.isWord }.forEach { token ->
                val meta = item.wordMeta[token.value.lowercase()]
                val bgColor = getWordColor(meta?.status)
                if (bgColor != Color.Transparent) {
                    addStyle(SpanStyle(background = bgColor), token.startIndex, token.endIndex)
                }
            }

            item.phraseRanges.forEach { (range, meta) ->
                val bgColor = getWordColor(meta.status)
                if (bgColor != Color.Transparent) {
                    addStyle(SpanStyle(background = bgColor), range.first, range.last + 1)
                }
            }

            if (item.targetWordRange != null) {
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.primary),
                    item.targetWordRange.first,
                    item.targetWordRange.last
                )
            }

            // АКТИВНОЕ ВЫДЕЛЕНИЕ (Drag Selection)
            if (selectionRange != null) {
                val intersectStart = max(selectionRange.first, 0)
                val intersectEnd = min(selectionRange.last + 1, item.contextSentence.length)
                if (intersectStart < intersectEnd) {
                    addStyle(SpanStyle(background = Color.Gray.copy(alpha = 0.4f)), intersectStart, intersectEnd)
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
                .pointerInput(item.contextSentence) {
                    detectTapGestures(
                        onTap = { pos ->
                            textLayoutResult?.getOffsetForPosition(pos)?.let { offset ->
                                onWordClick(offset)
                            }
                        }
                    )
                }
                .pointerInput(item.contextSentence) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            textLayoutResult?.getOffsetForPosition(pos)?.let { offset ->
                                onSelectionStart(offset)
                            }
                        },
                        onDrag = { change, _ ->
                            textLayoutResult?.getOffsetForPosition(change.position)?.let { offset ->
                                onSelectionDrag(offset)
                            }
                        },
                        onDragEnd = { onSelectionEnd() },
                        onDragCancel = { onClearSelection() }
                    )
                }
        )

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = item.word, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(48.dp))

        if (showAnswer) {
            HorizontalDivider(modifier = Modifier.fillMaxWidth(0.8f))
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = item.translation, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
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