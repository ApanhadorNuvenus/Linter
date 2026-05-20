package com.example.linter.presentation.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(videoId) { viewModel.initVideo(videoId, exoPlayer) }
    DisposableEffect(Unit) { onDispose { viewModel.releasePlayer() } }

    var showSettings by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    // НОВОЕ: Запоминаем состояние плеера до вызова попапа
    var wasPlayingWhenClicked by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(state.currentBlockIndex) {
        if (state.currentBlockIndex >= 0 && !listState.isScrollInProgress) {
            coroutineScope.launch { listState.animateScrollToItem(state.currentBlockIndex) }
        }
    }

    // НОВОЕ: Централизованный слушатель закрытия попапа
    // Неважно, как закрылся попап (кнопкой Отмена, кнопкой Знаю/Учу или Назад) —
    // этот блок поймает момент закрытия (Hidden) и правильно восстановит видео.
    LaunchedEffect(state.popupState) {
        if (state.popupState is PopupState.Hidden) {
            if (wasPlayingWhenClicked) {
                exoPlayer.play()
                wasPlayingWhenClicked = false // Сбрасываем флаг
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = {
                    IconButton(
                        onClick = {
                            if (state.originalUrl.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(state.originalUrl))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Ссылка скопирована")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Копировать ссылку")
                    }

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
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Ошибка: ${state.error}", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                setShowSubtitleButton(false)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Box {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { qualityMenuExpanded = true }
                            ) {
                                Text(
                                    text = state.currentQuality,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = qualityMenuExpanded,
                                onDismissRequest = { qualityMenuExpanded = false }
                            ) {
                                state.availableQualities.forEach { q ->
                                    DropdownMenuItem(
                                        text = { Text(q) },
                                        onClick = {
                                            viewModel.changeQuality(q)
                                            qualityMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { speedMenuExpanded = true }
                            ) {
                                Text(
                                    text = "${state.playbackSpeed}x",
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            DropdownMenu(
                                expanded = speedMenuExpanded,
                                onDismissRequest = { speedMenuExpanded = false }
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x") },
                                        onClick = {
                                            viewModel.setPlaybackSpeed(speed)
                                            speedMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)
                ) {
                    itemsIndexed(
                        items = state.subtitles,
                        key = { _, block -> block.id }
                    ) { index, block ->
                        val isActive = index == state.currentBlockIndex

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isActive) Color(0xFFF5F5F5) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable {
                                    exoPlayer.seekTo(block.startTimeMs)
                                    exoPlayer.play()
                                }
                                .padding(vertical = 16.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
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
                                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                ),
                                onClick = { offset ->
                                    annotatedText.getStringAnnotations("WORD", offset, offset)
                                        .firstOrNull()?.let { annotation ->
                                            // НОВОЕ: Запоминаем, играло ли видео, и ставим на паузу
                                            wasPlayingWhenClicked = exoPlayer.isPlaying
                                            exoPlayer.pause()

                                            viewModel.onWordClicked(annotation.item, block.sourceText)
                                        }
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val displayedTranslation = when (state.translationMode) {
                                TranslationMode.YOUTUBE_NATIVE -> block.translatedText ?: ""
                                TranslationMode.LOCAL_ML_KIT -> block.mlKitTranslatedText ?: "Перевод (ML Kit)..."
                                TranslationMode.ADVANCED_ONNX -> block.onnxTranslatedText ?: "Перевод (ONNX)..."
                            }

                            Text(
                                text = displayedTranslation,
                                color = Color.Gray,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
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
                    // ИЗМЕНЕНИЕ: Просто закрываем. LaunchedEffect сам разберется, включать ли видео.
                    viewModel.dismissPopup()
                }
            )
        }

        if (showSettings) {
            ModalBottomSheet(onDismissRequest = { showSettings = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Режим перевода", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))

                    TranslationMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = mode != TranslationMode.YOUTUBE_NATIVE || state.hasYoutubeTranslation) {
                                    viewModel.setTranslationMode(mode)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = state.translationMode == mode,
                                onClick = { viewModel.setTranslationMode(mode) },
                                enabled = mode != TranslationMode.YOUTUBE_NATIVE || state.hasYoutubeTranslation
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.title,
                                color = if (mode == TranslationMode.YOUTUBE_NATIVE && !state.hasYoutubeTranslation) Color.Gray else Color.Unspecified
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            showSettings = false
                            viewModel.reloadAndClearSubtitles()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Очистить кэш")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Очистить кэш и загрузить заново")
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}