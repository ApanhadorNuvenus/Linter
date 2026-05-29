package com.example.linter.presentation.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linter.data.fsrs.Grade
import com.example.linter.di.AppModule
import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.UiWordStatus
import com.example.linter.domain.model.WordMeta
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.domain.repository.ReviewItem
import com.example.linter.presentation.ui.lecturedetail.PopupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

enum class CardBucket { BLUE, RED, GREEN }

data class ReviewUiState(
    val isLoading: Boolean = true,
    val queue: List<ReviewItem> = emptyList(),
    val currentItem: ReviewItem? = null,
    val showAnswer: Boolean = false,
    val isFinished: Boolean = false,
    val popupState: PopupState = PopupState.Hidden,
    val selectionRange: IntRange? = null,
    val blueCount: Int = 0,
    val redCount: Int = 0,
    val greenCount: Int = 0,
    val currentBucket: CardBucket? = null
)

class ReviewViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val reviewRepository = AppModule.reviewRepository
    private val vocabularyRepository = AppModule.vocabularyRepository

    // Извлекаем выбранный язык из аргументов навигации (default = "en")
    val selectedLanguage: String = savedStateHandle.get<String>("lang") ?: "en"

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var dragStartTokenIndex: Int = -1
    private var translationJob: kotlinx.coroutines.Job? = null

    init {
        loadDueCards()
    }

    private fun getBucketForItem(item: ReviewItem?): CardBucket? {
        if (item == null) return null
        val rc = item.fsrsCard.reviewCount
        val ivl = item.fsrsCard.interval
        return when {
            rc == 0 -> CardBucket.BLUE
            rc > 0 && ivl < 1 -> CardBucket.RED
            else -> CardBucket.GREEN
        }
    }

    private fun calculateCounters(queue: List<ReviewItem>): Triple<Int, Int, Int> {
        var blue = 0
        var red = 0
        var green = 0
        for (item in queue) {
            val bucket = getBucketForItem(item)
            when (bucket) {
                CardBucket.BLUE -> blue++
                CardBucket.RED -> red++
                CardBucket.GREEN -> green++
                null -> {}
            }
        }
        return Triple(blue, red, green)
    }

    private fun loadDueCards() {
        viewModelScope.launch {
            val items = reviewRepository.getDueReviewItems(lang = selectedLanguage, lookaheadMs = 600_000L)
            val (b, r, g) = calculateCounters(items)
            val firstItem = items.firstOrNull()

            _uiState.value = ReviewUiState(
                isLoading = false,
                queue = items,
                currentItem = firstItem,
                currentBucket = getBucketForItem(firstItem),
                showAnswer = false,
                isFinished = items.isEmpty(),
                blueCount = b,
                redCount = r,
                greenCount = g
            )
        }
    }

    fun revealAnswer() {
        _uiState.value = _uiState.value.copy(showAnswer = true)
    }

    fun submitGrade(grade: Grade) {
        val state = _uiState.value
        val currentItem = state.currentItem ?: return

        viewModelScope.launch {
            reviewRepository.submitReview(currentItem.flashCardEntityId, grade)
            advanceQueue()
        }
    }

    // Отложить текущую карточку
    fun postponeCurrentCard() {
        val state = _uiState.value
        val currentItem = state.currentItem ?: return

        viewModelScope.launch {
            reviewRepository.postponeCard(currentItem.flashCardEntityId)
            advanceQueue()
        }
    }

    private suspend fun advanceQueue() {
        val state = _uiState.value
        val newQueue = state.queue.drop(1)

        if (newQueue.isEmpty()) {
            val moreItems = reviewRepository.getDueReviewItems(lang = selectedLanguage, lookaheadMs = 600_000L)
            if (moreItems.isEmpty()) {
                _uiState.value = state.copy(queue = emptyList(), currentItem = null, currentBucket = null, isFinished = true, showAnswer = false, blueCount = 0, redCount = 0, greenCount = 0)
            } else {
                val (b, r, g) = calculateCounters(moreItems)
                val nextItem = moreItems.first()
                _uiState.value = state.copy(queue = moreItems, currentItem = nextItem, currentBucket = getBucketForItem(nextItem), showAnswer = false, blueCount = b, redCount = r, greenCount = g)
            }
        } else {
            val (b, r, g) = calculateCounters(newQueue)
            val nextItem = newQueue.first()
            _uiState.value = state.copy(queue = newQueue, currentItem = nextItem, currentBucket = getBucketForItem(nextItem), showAnswer = false, blueCount = b, redCount = r, greenCount = g)
        }
    }

    fun onWordClicked(offset: Int) {
        val state = _uiState.value
        if (state.selectionRange != null) {
            clearSelection()
            return
        }
        val currentItem = state.currentItem ?: return
        val token = currentItem.tokens.find { it.startIndex <= offset && it.endIndex > offset && it.isWord }
        if (token != null) {
            handleWordOpen(token.value.lowercase())
        }
    }

    fun onSelectionStart(offset: Int) {
        val currentItem = _uiState.value.currentItem ?: return
        val token = currentItem.tokens.find { it.startIndex <= offset && it.endIndex > offset } ?: return
        dragStartTokenIndex = currentItem.tokens.indexOf(token)
        _uiState.value = _uiState.value.copy(selectionRange = token.startIndex until token.endIndex)
    }

    fun onSelectionDrag(offset: Int) {
        val state = _uiState.value
        val currentItem = state.currentItem ?: return
        if (dragStartTokenIndex == -1) return
        val currentToken = currentItem.tokens.find { it.startIndex <= offset && it.endIndex > offset }
        val currentIndex = currentToken?.let { currentItem.tokens.indexOf(it) } ?: return

        val startToken = currentItem.tokens[min(dragStartTokenIndex, currentIndex)]
        val endToken = currentItem.tokens[max(dragStartTokenIndex, currentIndex)]
        _uiState.value = state.copy(selectionRange = startToken.startIndex until endToken.endIndex)
    }

    fun onSelectionEnd() {
        val state = _uiState.value
        val range = state.selectionRange ?: return
        dragStartTokenIndex = -1

        val phrase = state.currentItem?.contextSentence?.substring(range)?.trim()?.lowercase() ?: return
        if (phrase.isBlank()) {
            clearSelection()
            return
        }
        handleWordOpen(phrase)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectionRange = null)
        dragStartTokenIndex = -1
    }

    private fun handleWordOpen(word: String) {
        val state = _uiState.value
        val currentItem = state.currentItem ?: return

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            val meta = if (word.contains(" ")) {
                vocabularyRepository.getWordMetas(listOf(word))[word] ?: WordMeta(UiWordStatus.BLUE)
            } else {
                currentItem.wordMeta[word] ?: WordMeta(UiWordStatus.BLUE)
            }

            if (meta.status == UiWordStatus.BLUE || meta.status == UiWordStatus.TRANSPARENT) {
                vocabularyRepository.fetchMultiTranslations(word, selectedLanguage)
                    .collect { progressiveTrans ->
                        _uiState.value = state.copy(
                            popupState = PopupState.NewWord(word, progressiveTrans, currentItem.contextSentence)
                        )
                    }
            } else {
                _uiState.value = state.copy(popupState = PopupState.LearningWord(word, meta, currentItem.contextSentence))
            }
        }
    }

    fun onStartLearning(word: String, translations: MultiTranslation, contextSentence: String) {
        viewModelScope.launch {
            vocabularyRepository.createLearningCard(word, 0L, 0L, contextSentence, translations, LearningStatus.NEW)
            refreshCurrentCard()
            dismissPopup()
        }
    }

    fun onMarkAsKnown(word: String, contextCardId: Long? = null) {
        viewModelScope.launch {
            if (contextCardId != null) {
                vocabularyRepository.moveCardToKnown(contextCardId, word)
            } else {
                // Извлекаем переводы и контекст из открытого попапа NewWord
                val trans = (_uiState.value.popupState as? PopupState.NewWord)?.translations
                val context = (_uiState.value.popupState as? PopupState.NewWord)?.contextSentence ?: ""

                vocabularyRepository.markAsKnown(
                    word = word,
                    translations = trans,
                    contextSentence = context
                )
            }
            refreshCurrentCard()
            dismissPopup()
        }
    }

    fun onMarkAsIgnored(word: String) {
        viewModelScope.launch {
            vocabularyRepository.markAsIgnored(word)
            refreshCurrentCard()
            dismissPopup()
        }
    }

    fun onChangeLearningStatus(cardId: Long, word: String, newStatus: LearningStatus) {
        viewModelScope.launch {
            vocabularyRepository.updateCardStatus(cardId, newStatus)
            refreshCurrentCard()
            dismissPopup()
        }
    }

    fun onSaveCustomTranslation(cardId: Long, word: String, customTranslation: String) {
        viewModelScope.launch {
            vocabularyRepository.updateCustomTranslation(cardId, customTranslation)
            refreshCurrentCard()
        }
    }

    private suspend fun refreshCurrentCard() {
        val state = _uiState.value
        val currentItem = state.currentItem ?: return

        val updatedMeta = vocabularyRepository.getWordMetas(currentItem.wordMeta.keys.toList())
        val updatedTranslations = updatedMeta[currentItem.word.lowercase()]?.translations ?: currentItem.translations

        val updatedItem = currentItem.copy(
            wordMeta = updatedMeta,
            translations = updatedTranslations
        )

        val newQueue = state.queue.toMutableList()
        if (newQueue.isNotEmpty()) {
            newQueue[0] = updatedItem
        }

        _uiState.value = state.copy(queue = newQueue, currentItem = updatedItem)
    }

    fun dismissPopup() {
        translationJob?.cancel()
        _uiState.value = _uiState.value.copy(popupState = PopupState.Hidden, selectionRange = null)
    }
}