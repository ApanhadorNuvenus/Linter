package com.example.linter.presentation.ui.youtube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
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
import com.example.linter.ui.theme.BlueWordDark
import com.example.linter.ui.theme.BlueWordLight
import com.example.linter.ui.theme.YellowWordDark
import com.example.linter.ui.theme.YellowWordLight
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

    var wasPlayingWhenClicked by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(state.currentBlockIndex) {
        if (state.currentBlockIndex >= 0 && !listState.isScrollInProgress) {
            coroutineScope.launch { listState.animateScrollToItem(state.currentBlockIndex) }
        }
    }

    LaunchedEffect(state.popupState) {
        if (state.popupState is PopupState.Hidden && wasPlayingWhenClicked) {
            exoPlayer.play()
            wasPlayingWhenClicked = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, fontWeight = FontWeight.SemiBold) },
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

                        SubtitleRowCard(
                            isActive = isActive,
                            block = block,
                            state = state,
                            onClick = {
                                exoPlayer.seekTo(block.startTimeMs)
                                exoPlayer.play()
                            },
                            onWordSelected = { phrase ->
                                wasPlayingWhenClicked = exoPlayer.isPlaying
                                exoPlayer.pause()
                                viewModel.onWordClicked(phrase, block.sourceText)
                            }
                        )
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
                onDismiss = { viewModel.dismissPopup() },
                onSaveCustomTranslation = { cardId, word, translation ->
                    viewModel.onSaveCustomTranslation(cardId, word, translation)
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

@Composable
fun SubtitleRowCard(
    isActive: Boolean,
    block: com.example.linter.domain.model.SubtitleBlock,
    state: YoutubeDetailState,
    onClick: () -> Unit,
    onWordSelected: (String) -> Unit
) {
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    val cardBackground by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        label = "BgAnim"
    )
    val cardElevation by animateDpAsState(
        targetValue = if (isActive) 2.dp else 0.dp,
        label = "ElevAnim"
    )

    // Состояния жеста выделения внутри конкретной карточки субтитров
    var localSelectionRange by remember { mutableStateOf<IntRange?>(null) }
    var dragStartTokenIndex by remember { mutableStateOf(-1) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val tokens = state.tokenizedBlocks[block.id] ?: emptyList()
                val annotatedText = buildAnnotatedString {
                    tokens.forEach { token ->
                        val meta = state.wordMeta[token.value.lowercase()]
                        val bgColor = when (meta?.status) {
                            UiWordStatus.BLUE -> if (darkTheme) BlueWordDark else BlueWordLight
                            UiWordStatus.YELLOW -> if (darkTheme) YellowWordDark else YellowWordLight
                            else -> Color.Transparent
                        }

                        val startIndex = length
                        append(token.value)
                        val endIndex = length

                        if (bgColor != Color.Transparent) {
                            addStyle(SpanStyle(background = bgColor), startIndex, endIndex)
                        }
                    }

                    // Окрашивание свайп-выделения словосочетаний
                    localSelectionRange?.let { range ->
                        val intersectStart = maxOf(range.first, 0)
                        val intersectEnd = minOf(range.last + 1, block.sourceText.length)
                        if (intersectStart < intersectEnd) {
                            addStyle(SpanStyle(background = Color.Gray.copy(alpha = 0.4f)), intersectStart, intersectEnd)
                        }
                    }
                }

                // Интерактивный жест-выделяемый текст
                Text(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center
                    ),
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(block.sourceText) {
                            detectTapGestures(
                                onTap = { pos ->
                                    val offset = textLayoutResult?.getOffsetForPosition(pos) ?: return@detectTapGestures
                                    val token = tokens.find { it.startIndex <= offset && it.endIndex > offset && it.isWord }
                                    if (token != null) {
                                        onWordSelected(token.value)
                                    }
                                }
                            )
                        }
                        .pointerInput(block.sourceText) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { pos ->
                                    val offset = textLayoutResult?.getOffsetForPosition(pos) ?: return@detectDragGesturesAfterLongPress
                                    val token = tokens.find { it.startIndex <= offset && it.endIndex > offset } ?: return@detectDragGesturesAfterLongPress
                                    dragStartTokenIndex = tokens.indexOf(token)
                                    localSelectionRange = token.startIndex until token.endIndex
                                },
                                onDrag = { change, _ ->
                                    val offset = textLayoutResult?.getOffsetForPosition(change.position) ?: return@detectDragGesturesAfterLongPress
                                    val currentToken = tokens.find { it.startIndex <= offset && it.endIndex > offset } ?: return@detectDragGesturesAfterLongPress
                                    val currentIndex = tokens.indexOf(currentToken)
                                    if (dragStartTokenIndex != -1 && currentIndex != -1) {
                                        val startToken = tokens[minOf(dragStartTokenIndex, currentIndex)]
                                        val endToken = tokens[maxOf(dragStartTokenIndex, currentIndex)]
                                        localSelectionRange = startToken.startIndex until endToken.endIndex
                                    }
                                },
                                onDragEnd = {
                                    localSelectionRange?.let { range ->
                                        val phrase = block.sourceText.substring(range).trim()
                                        if (phrase.isNotBlank()) {
                                            onWordSelected(phrase)
                                        }
                                    }
                                    localSelectionRange = null
                                    dragStartTokenIndex = -1
                                },
                                onDragCancel = {
                                    localSelectionRange = null
                                    dragStartTokenIndex = -1
                                }
                            )
                        }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Отображение перевода субтитра (Скрываем полностью, если выбран режим DISABLED)
                if (state.translationMode != TranslationMode.DISABLED) {
                    val displayedTranslation = when (state.translationMode) {
                        TranslationMode.YOUTUBE_NATIVE -> block.translatedText ?: ""
                        TranslationMode.LOCAL_ML_KIT -> block.mlKitTranslatedText ?: ""
                        TranslationMode.ADVANCED_ONNX -> block.onnxTranslatedText ?: ""
                        else -> ""
                    }

                    if (displayedTranslation.isNotBlank()) {
                        Text(
                            text = displayedTranslation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}