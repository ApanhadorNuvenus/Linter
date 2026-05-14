package com.example.linter.presentation.ui.lecturedetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.linter.presentation.ui.components.EndOfPageDialog
import com.example.linter.presentation.ui.components.PaginatedLectureText
import com.example.linter.presentation.ui.components.WordPopup

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LectureDetailScreen(
    lectureId: Long,
    onBack: () -> Unit,
    viewModel: LectureDetailViewModel = viewModel()
) {
    LaunchedEffect(lectureId) { viewModel.loadLecture(lectureId) }
    val uiState by viewModel.uiState.collectAsState()

    var pageRanges by remember { mutableStateOf<List<IntRange>>(emptyList()) }
    val pagerState = rememberPagerState(pageCount = { maxOf(1, pageRanges.size) })

    // Отслеживаем действия (переход на страницу ИЛИ закрытие лекции)
    LaunchedEffect(uiState.pendingAction) {
        when (val action = uiState.pendingAction) {
            is PageAction.TurnPage -> {
                pagerState.animateScrollToPage(action.page)
                viewModel.clearPendingAction()
            }
            is PageAction.FinishLecture -> {
                viewModel.clearPendingAction()
                onBack() // Выходит из лекции!
            }
            null -> {}
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(uiState.title) }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                PaginatedLectureText(
                    text = uiState.text,
                    tokens = uiState.tokens,
                    wordMetadata = uiState.wordMeta,
                    phraseRanges = uiState.phraseRanges,
                    selectionRange = uiState.selectionRange,
                    pagerState = pagerState,
                    modifier = Modifier.weight(1f).padding(16.dp),
                    onWordClick = { offset -> viewModel.onWordClicked(offset) },
                    onSelectionStart = { offset -> viewModel.onSelectionStart(offset) },
                    onSelectionDrag = { offset -> viewModel.onSelectionDrag(offset) },
                    onSelectionEnd = { viewModel.onSelectionEnd() },
                    onClearSelection = { viewModel.clearSelection() },
                    onPageCalculated = { ranges -> pageRanges = ranges }
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            if (pagerState.currentPage > 0) {
                                viewModel.onAttemptPageTurn(pageRanges[pagerState.currentPage], pagerState.currentPage - 1)
                            }
                        },
                        enabled = pagerState.currentPage > 0
                    ) {
                        Text("Назад")
                    }

                    Button(
                        onClick = {
                            if (pagerState.currentPage < pageRanges.size - 1) {
                                viewModel.onAttemptPageTurn(pageRanges[pagerState.currentPage], pagerState.currentPage + 1)
                            } else {
                                // ИСПРАВЛЕНИЕ: Вызываем onAttemptFinish для последней страницы
                                viewModel.onAttemptFinish(pageRanges[pagerState.currentPage])
                            }
                        }
                    ) {
                        Text(if (pagerState.currentPage < pageRanges.size - 1) "Далее" else "Завершить")
                    }
                }
            }
        }

        if (uiState.popupWord != null) {
            WordPopup(
                word = uiState.popupWord!!,
                translation = uiState.popupTranslation ?: "Загрузка...",
                familiarity = uiState.popupFamiliarity,
                onFamiliarityChange = { viewModel.changeFamiliarity(uiState.popupWord!!, it) },
                onDismiss = { viewModel.dismissPopup() }
            )
        }

        if (uiState.showEndOfPageDialog) {
            EndOfPageDialog(
                unknownWords = uiState.unknownWordsOnPage,
                onConfirm = { selectedAsLearning ->
                    viewModel.processEndOfPageWords(selectedAsLearning)
                },
                onDismiss = { viewModel.dismissEndOfPageDialog() }
            )
        }
    }
}