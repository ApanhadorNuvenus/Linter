package com.example.linter.presentation.ui.youtube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    // Флаг, чтобы отследить самый первый прыжок к сохраненной позиции
    var isInitialScrollDone by remember { mutableStateOf(false) }

    // Контролируем прокрутку напрямую через корутину LaunchedEffect (без утечек)
    LaunchedEffect(state.currentBlockIndex) {
        val targetIndex = state.currentBlockIndex
        if (targetIndex >= 0 && state.subtitles.isNotEmpty()) {

            // Смещение в пикселях, чтобы активный субтитр был чуть ниже верхней границы (комфортно для глаз)
            val scrollOffset = -120

            if (!isInitialScrollDone) {
                // 1. ПЕРВЫЙ ЗАПУСК: Мгновенно позиционируем без анимации, исключая визуальный лаг
                listState.scrollToItem(targetIndex, scrollOffset)
                isInitialScrollDone = true
            } else {
                val firstVisible = listState.firstVisibleItemIndex
                val distance = kotlin.math.abs(targetIndex - firstVisible)

                if (distance > 3) {
                    // 2. ДЛИННЫЙ СКАЧОК (Seek/Перемотка): Мгновенно перемещаем, чтобы не грузить UI рендером
                    listState.scrollToItem(targetIndex, scrollOffset)
                } else {
                    // 3. ЕСТЕСТВЕННЫЙ ХОД (1-3 шага): Плавно передвигаем строку
                    listState.animateScrollToItem(targetIndex, scrollOffset)
                }
            }
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (state.originalUrl.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(state.originalUrl))
                                coroutineScope.launch { snackbarHostState.showSnackbar("URL copied to clipboard") }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Copy link")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else if (state.error != null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${state.error}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            } else {
                // Video & Control Dock with Frosted Pills
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                setShowSubtitleButton(false)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Frosted Pill Container (Speed, Resolution)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        // Quality Selector Pill
                        Box {
                            Surface(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .clickable { qualityMenuExpanded = true }
                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            ) {
                                Text(
                                    text = state.currentQuality,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
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

                        // Playback Speed Pill
                        Box {
                            Surface(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .clickable { speedMenuExpanded = true }
                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            ) {
                                Text(
                                    text = "${state.playbackSpeed}x",
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
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

                Spacer(modifier = Modifier.height(8.dp))

                // Beautifully Partitioned Subtitles List
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            onWordSelected = { word ->
                                wasPlayingWhenClicked = exoPlayer.isPlaying
                                exoPlayer.pause()
                                viewModel.onWordClicked(word, block.sourceText)
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
                onDismiss = { viewModel.dismissPopup() }
            )
        }

        // Settings BottomSheet Redesign (Clean M3 Sheet)
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 40.dp)
                ) {
                    Text(
                        "Translation Engines",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TranslationMode.entries.forEach { mode ->
                        val isEnabled = mode != TranslationMode.YOUTUBE_NATIVE || state.hasYoutubeTranslation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = isEnabled) { viewModel.setTranslationMode(mode) }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = state.translationMode == mode,
                                onClick = { viewModel.setTranslationMode(mode) },
                                enabled = isEnabled
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))

                    // Dangerous cache cleaning action styled elegantly
                    Button(
                        onClick = {
                            showSettings = false
                            viewModel.reloadAndClearSubtitles()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Subtitle Cache & Refresh")
                    }
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
    val darkTheme = isSystemInDarkTheme()

    // Smooth transition animations for state switching
    val cardBackground by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        label = "BgAnim"
    )
    val cardElevation by animateDpAsState(
        targetValue = if (isActive) 2.dp else 0.dp,
        label = "ElevAnim"
    )

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
            // Elegant left border indicator for active states instead of harsh overall background
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) MaterialTheme.colorScheme.tertiary else Color.Transparent)
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

                        if (token.isWord) {
                            addStringAnnotation("WORD", token.value, startIndex, endIndex)
                        }
                    }
                }

                androidx.compose.foundation.text.ClickableText(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        lineHeight = 24.sp
                    ),
                    onClick = { offset ->
                        annotatedText.getStringAnnotations("WORD", offset, offset)
                            .firstOrNull()?.let { annotation ->
                                onWordSelected(annotation.item)
                            }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                val displayedTranslation = when (state.translationMode) {
                    TranslationMode.YOUTUBE_NATIVE -> block.translatedText ?: ""
                    TranslationMode.LOCAL_ML_KIT -> block.mlKitTranslatedText ?: ""
                    TranslationMode.ADVANCED_ONNX -> block.onnxTranslatedText ?: ""
                }

                if (displayedTranslation.isNotBlank()) {
                    Text(
                        text = displayedTranslation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}