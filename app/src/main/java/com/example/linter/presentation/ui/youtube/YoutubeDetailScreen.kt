package com.example.linter.presentation.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.linter.domain.model.TranslationMode
import com.example.linter.domain.model.UiWordStatus
import com.example.linter.presentation.ui.components.WordPopup
import com.example.linter.presentation.ui.lecturedetail.PopupState
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeDetailScreen(
    videoId: Long,
    onBack: () -> Unit,
    viewModel: YoutubeDetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(videoId) { viewModel.initVideo(videoId, exoPlayer) }

    DisposableEffect(Unit) { onDispose { viewModel.releasePlayer() } }

    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.currentBlockIndex) {
        if (state.currentBlockIndex >= 0 && !listState.isScrollInProgress) {
            coroutineScope.launch { listState.animateScrollToItem(state.currentBlockIndex) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                // ИСПРАВЛЕНИЕ: Выводим ошибку на экран, если видео/сабы не смогли загрузиться
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Ошибка: ${state.error}", color = MaterialTheme.colorScheme.error)
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            setShowSubtitleButton(false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    itemsIndexed(state.subtitles) { index, block ->
                        val isActive = index == state.currentBlockIndex

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isActive) Color(0xFFF0F0F0) else Color.Transparent)
                                .clickable {
                                    exoPlayer.seekTo(block.startTimeMs)
                                    exoPlayer.play()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            val tokens = state.tokenizedBlocks[block.id] ?: emptyList()
                            val annotatedText = buildAnnotatedString {
                                tokens.forEach { token ->
                                    val meta = state.wordMeta[token.value.lowercase()]
                                    val bgColor = when (meta?.status) {
                                        UiWordStatus.BLUE -> Color(0xFFE3F2FD)
                                        UiWordStatus.YELLOW -> Color(0xFFFFF9C4)
                                        else -> Color.Transparent
                                    }

                                    val startIndex = length
                                    append(token.value)
                                    val endIndex = length

                                    if (bgColor != Color.Transparent) {
                                        addStyle(SpanStyle(background = bgColor), startIndex, endIndex)
                                    }

                                    if (token.isWord) {
                                        addStringAnnotation("WORD", token.value, startIndex, endIndex)
                                    }
                                }
                            }

                            androidx.compose.foundation.text.ClickableText(
                                text = annotatedText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                ),
                                onClick = { offset ->
                                    annotatedText.getStringAnnotations("WORD", offset, offset)
                                        .firstOrNull()?.let { annotation ->
                                            exoPlayer.pause()
                                            viewModel.onWordClicked(annotation.item, block.sourceText)
                                        }
                                }
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = block.translatedText ?: if (state.translationMode == TranslationMode.LOCAL_ML_KIT) "Перевод..." else "",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        if (state.popupState !is PopupState.Hidden) {
            WordPopup(
                state = state.popupState,
                onStartLearning = { word, trans, contextSent -> viewModel.onStartLearning(word, trans, contextSent) },
                onMarkAsKnown = { word, cardId -> viewModel.onMarkAsKnown(word, cardId) },
                onMarkAsIgnored = { word -> viewModel.onMarkAsIgnored(word) },
                onChangeLearningStatus = { cardId, word, lStatus -> viewModel.onChangeLearningStatus(cardId, word, lStatus) },
                onDismiss = {
                    viewModel.dismissPopup()
                    exoPlayer.play()
                }
            )
        }

        if (showSettings) {
            ModalBottomSheet(onDismissRequest = { showSettings = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Режим перевода", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.translationMode == TranslationMode.YOUTUBE_NATIVE,
                            onClick = { viewModel.setTranslationMode(TranslationMode.YOUTUBE_NATIVE) },
                            enabled = state.hasYoutubeTranslation
                        )
                        Text("Перевод YouTube (только если есть)", color = if (state.hasYoutubeTranslation) Color.Unspecified else Color.Gray)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.translationMode == TranslationMode.LOCAL_ML_KIT,
                            onClick = { viewModel.setTranslationMode(TranslationMode.LOCAL_ML_KIT) }
                        )
                        Text("Локальный ML Kit (Офлайн)")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}