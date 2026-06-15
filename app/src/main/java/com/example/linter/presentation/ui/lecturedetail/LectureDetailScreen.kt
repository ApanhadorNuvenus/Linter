package com.example.linter.presentation.ui.lecturedetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

    LaunchedEffect(uiState.pendingAction) {
        when (val action = uiState.pendingAction) {
            is PageAction.TurnPage -> {
                pagerState.animateScrollToPage(action.page)
                viewModel.clearPendingAction()
            }
            is PageAction.FinishLecture -> {
                viewModel.clearPendingAction()
                onBack()
            }
            null -> {}
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(uiState.title, fontWeight = FontWeight.Bold, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                if (pageRanges.isNotEmpty()) {
                    val progress = (pagerState.currentPage + 1).toFloat() / pageRanges.size.toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    PaginatedLectureText(
                        text = uiState.text,
                        tokens = uiState.tokens,
                        wordMetadata = uiState.wordMeta,
                        phraseRanges = uiState.phraseRanges,
                        selectionRange = uiState.selectionRange,
                        pagerState = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        onWordClick = { offset -> viewModel.onWordClicked(offset) },
                        onSelectionStart = { offset -> viewModel.onSelectionStart(offset) },
                        onSelectionDrag = { offset -> viewModel.onSelectionDrag(offset) },
                        onSelectionEnd = { viewModel.onSelectionEnd() },
                        onClearSelection = { viewModel.clearSelection() },
                        onPageCalculated = { ranges -> pageRanges = ranges }
                    )
                }

                Surface(
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (pagerState.currentPage > 0) {
                                    viewModel.onAttemptPageTurn(pageRanges[pagerState.currentPage], pagerState.currentPage - 1)
                                }
                            },
                            enabled = pagerState.currentPage > 0,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Prev")
                        }

                        if (pageRanges.isNotEmpty()) {
                            Text(
                                text = "${pagerState.currentPage + 1} of ${pageRanges.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Button(
                            onClick = {
                                if (pagerState.currentPage < pageRanges.size - 1) {
                                    viewModel.onAttemptPageTurn(pageRanges[pagerState.currentPage], pagerState.currentPage + 1)
                                } else {
                                    viewModel.onAttemptFinish(pageRanges[pagerState.currentPage])
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (pagerState.currentPage < pageRanges.size - 1) "Next" else "Finish")
                        }
                    }
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
                onPlayTts = { word -> viewModel.playTts(word) }, // НОВОЕ
                onDismiss = { viewModel.dismissPopup() },
                onSaveCustomTranslation = { cardId, word, translation -> viewModel.onSaveCustomTranslation(cardId, word, translation) }
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