package com.example.linter.presentation.ui.youtube

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.linter.data.model.AndroidBreakIteratorTokenizer
import com.example.linter.di.AppModule
import com.example.linter.domain.model.*
import com.example.linter.presentation.ui.lecturedetail.PopupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class YoutubeDetailState(
    val videoId: Long = 0,
    val title: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,

    val subtitles: List<SubtitleBlock> = emptyList(),
    val currentBlockIndex: Int = -1,

    val tokenizedBlocks: Map<Long, List<Token>> = emptyMap(),
    val wordMeta: Map<String, WordMeta> = emptyMap(),

    val translationMode: TranslationMode = TranslationMode.YOUTUBE_NATIVE,
    val hasYoutubeTranslation: Boolean = false,
    val sourceLang: String = "en", // НОВОЕ: Храним язык видео

    val playbackSpeed: Float = 1.0f,
    val popupState: PopupState = PopupState.Hidden
)

class YoutubeDetailViewModel : ViewModel() {
    private val repo = AppModule.youtubeRepository
    private val vocabRepo = AppModule.vocabularyRepository
    private val tokenizer = AndroidBreakIteratorTokenizer()

    private val _uiState = MutableStateFlow(YoutubeDetailState())
    val uiState: StateFlow<YoutubeDetailState> = _uiState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var syncJob: Job? = null
    private var translateJob: Job? = null

    @OptIn(UnstableApi::class)
    fun initVideo(videoId: Long, player: ExoPlayer) {
        exoPlayer = player
        viewModelScope.launch {
            val video = repo.getVideoById(videoId) ?: return@launch

            _uiState.value = _uiState.value.copy(videoId = videoId, title = video.title, isLoading = true, error = null)

            val infoResult = repo.fetchPlaybackInfo(videoId, video.url)

            if (infoResult.isSuccess) {
                val info = infoResult.getOrThrow()

                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

                val videoSource = ProgressiveMediaSource.Factory(httpDataSourceFactory).createMediaSource(MediaItem.fromUri(info.videoUrl))
                val audioSource = ProgressiveMediaSource.Factory(httpDataSourceFactory).createMediaSource(MediaItem.fromUri(info.audioUrl))
                val mergedSource = MergingMediaSource(videoSource, audioSource)

                player.setMediaSource(mergedSource)
                player.prepare()
                player.setPlaybackSpeed(_uiState.value.playbackSpeed)
                if (video.progressMs > 0) player.seekTo(video.progressMs)
                player.play()

                val tokenized = info.subtitles.associate { it.id to tokenizer.tokenize(it.sourceText) }
                val allWords = tokenized.values.flatten().filter { it.isWord }.map { it.value.lowercase() }.distinct()
                val metas = vocabRepo.getWordMetas(allWords)

                val mode = if (info.hasYoutubeTranslation) TranslationMode.YOUTUBE_NATIVE else TranslationMode.LOCAL_ML_KIT

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    subtitles = info.subtitles,
                    tokenizedBlocks = tokenized,
                    wordMeta = metas,
                    hasYoutubeTranslation = info.hasYoutubeTranslation,
                    translationMode = mode,
                    sourceLang = info.sourceLang // ИСПРАВЛЕНИЕ: Сохраняем язык
                )

                if (mode == TranslationMode.LOCAL_ML_KIT) triggerLocalTranslation()
                startSubtitleSync()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = infoResult.exceptionOrNull()?.message ?: "Неизвестная ошибка при загрузке"
                )
            }
        }
    }

    private fun startSubtitleSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (true) {
                val currentPos = exoPlayer?.currentPosition ?: 0L
                val blocks = _uiState.value.subtitles
                val activeIndex = blocks.indexOfFirst { currentPos in it.startTimeMs..it.endTimeMs }

                if (activeIndex != _uiState.value.currentBlockIndex) {
                    _uiState.value = _uiState.value.copy(currentBlockIndex = activeIndex)
                }

                if (currentPos > 0 && (currentPos % 5000) in 0..200) {
                    repo.updateProgress(_uiState.value.videoId, currentPos)
                }
                delay(200)
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun setTranslationMode(mode: TranslationMode) {
        if (_uiState.value.translationMode == mode) return

        viewModelScope.launch {
            repo.clearTranslationsForVideo(_uiState.value.videoId)
            val clearedBlocks = _uiState.value.subtitles.map { it.copy(translatedText = null) }

            _uiState.value = _uiState.value.copy(
                translationMode = mode,
                subtitles = clearedBlocks
            )

            if (mode == TranslationMode.LOCAL_ML_KIT) {
                triggerLocalTranslation()
            } else {
                initVideo(_uiState.value.videoId, exoPlayer!!)
            }
        }
    }

    private fun triggerLocalTranslation() {
        translateJob?.cancel()
        translateJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && _uiState.value.translationMode == TranslationMode.LOCAL_ML_KIT) {
                val state = _uiState.value
                val blocks = state.subtitles
                val currentIndex = maxOf(0, state.currentBlockIndex)
                var targetIndex = -1

                for (offset in 0..blocks.size) {
                    val forwardIndex = currentIndex + offset
                    val backwardIndex = currentIndex - offset

                    if (forwardIndex in blocks.indices && blocks[forwardIndex].translatedText == null) {
                        targetIndex = forwardIndex
                        break
                    }
                    if (backwardIndex in blocks.indices && blocks[backwardIndex].translatedText == null) {
                        targetIndex = backwardIndex
                        break
                    }
                }

                if (targetIndex != -1) {
                    val translatedBlock = repo.translateAndSaveBlockLocally(blocks[targetIndex], state.sourceLang)
                    val newBlocks = _uiState.value.subtitles.toMutableList()
                    newBlocks[targetIndex] = translatedBlock
                    _uiState.value = _uiState.value.copy(subtitles = newBlocks)
                } else {
                    break
                }
            }
        }
    }

    fun onWordClicked(word: String, contextSentence: String) {
        val state = _uiState.value
        val normalized = word.lowercase()
        viewModelScope.launch {
            val meta = state.wordMeta[normalized] ?: WordMeta(UiWordStatus.BLUE)
            if (meta.status == UiWordStatus.BLUE || meta.status == UiWordStatus.TRANSPARENT) {
                // ИСПРАВЛЕНИЕ: Передаем реальный язык видео вместо "en"
                val trans = vocabRepo.fetchTranslation(normalized, state.sourceLang)
                _uiState.value = state.copy(popupState = PopupState.NewWord(normalized, trans, contextSentence))
            } else {
                _uiState.value = state.copy(popupState = PopupState.LearningWord(normalized, meta, contextSentence))
            }
        }
    }

    fun onStartLearning(word: String, translation: String, contextSentence: String) {
        viewModelScope.launch {
            vocabRepo.createLearningCard(word, 0L, _uiState.value.videoId, contextSentence, translation, LearningStatus.NEW)
            refreshWordState(word)
            dismissPopup()
        }
    }

    fun onMarkAsKnown(word: String, contextCardId: Long? = null) {
        viewModelScope.launch {
            if (contextCardId != null) vocabRepo.moveCardToKnown(contextCardId, word)
            else vocabRepo.markAsKnown(word)
            refreshWordState(word)
            dismissPopup()
        }
    }

    fun onMarkAsIgnored(word: String) {
        viewModelScope.launch {
            vocabRepo.markAsIgnored(word)
            refreshWordState(word)
            dismissPopup()
        }
    }

    fun onChangeLearningStatus(cardId: Long, word: String, newStatus: LearningStatus) {
        viewModelScope.launch {
            vocabRepo.updateCardStatus(cardId, newStatus)
            refreshWordState(word)
            dismissPopup()
        }
    }

    private suspend fun refreshWordState(word: String) {
        val updatedMeta = _uiState.value.wordMeta.toMutableMap()
        updatedMeta[word] = vocabRepo.getWordMetas(listOf(word))[word]!!
        _uiState.value = _uiState.value.copy(wordMeta = updatedMeta)
    }

    fun dismissPopup() {
        _uiState.value = _uiState.value.copy(popupState = PopupState.Hidden)
    }

    fun releasePlayer() {
        exoPlayer?.let { player ->
            val pos = player.currentPosition
            CoroutineScope(Dispatchers.IO).launch {
                repo.updateProgress(_uiState.value.videoId, pos)
            }
            player.release()
        }
        exoPlayer = null
        syncJob?.cancel()
        translateJob?.cancel()
    }
}