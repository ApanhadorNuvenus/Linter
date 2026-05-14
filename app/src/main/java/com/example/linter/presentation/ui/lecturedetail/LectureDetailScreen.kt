package com.example.linter.presentation.ui.lecturedetail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.linter.presentation.ui.components.PaginatedLectureText
import com.example.linter.presentation.ui.components.WordPopup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectureDetailScreen(
    lectureId: Long,
    onBack: () -> Unit,
    viewModel: LectureDetailViewModel = viewModel()
) {
    LaunchedEffect(lectureId) { viewModel.loadLecture(lectureId) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(uiState.title) }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.fillMaxSize().wrapContentSize())
            } else {
                PaginatedLectureText(
                    text = uiState.text,
                    tokens = uiState.tokens,
                    wordMetadata = uiState.wordMeta,
                    phraseRanges = uiState.phraseRanges,
                    selectionRange = uiState.selectionRange,
                    modifier = Modifier.fillMaxSize(),
                    onWordClick = { offset -> viewModel.onWordClicked(offset) },
                    onSelectionStart = { offset -> viewModel.onSelectionStart(offset) },
                    onSelectionDrag = { offset -> viewModel.onSelectionDrag(offset) },
                    onSelectionEnd = { viewModel.onSelectionEnd() },
                    onClearSelection = { viewModel.clearSelection() }
                )
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
    }
}