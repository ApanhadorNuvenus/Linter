package com.example.linter.presentation.ui.lecturedetail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                    wordMetadata = uiState.wordMeta,
                    modifier = Modifier.fillMaxSize(),
                    onWordClick = { word -> viewModel.onWordClicked(word) }
                )
            }
        }
        if (uiState.popupWord != null) {
            WordPopup(
                word = uiState.popupWord!!,
                translation = uiState.popupTranslation ?: "",
                familiarity = uiState.popupFamiliarity,
                onFamiliarityChange = { viewModel.changeFamiliarity(uiState.popupWord!!, it) },
                onDismiss = { viewModel.dismissPopup() }
            )
        }
    }
}