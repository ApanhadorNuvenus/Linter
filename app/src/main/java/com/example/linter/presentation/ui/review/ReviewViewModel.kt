package com.example.linter.presentation.ui.review

import android.content.Context
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
import kotlin.math.abs
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

            val newQueue = state.queue.drop(1).toMutableList()

            if (grade.interval < 1) {
                val updatedFsrs = currentItem.fsrsCard.copy(
                    reviewCount = currentItem.fsrsCard.reviewCount + 1,
                    interval = grade.interval,
                    phase = com.example.linter.data.fsrs.CardPhase.Review.value
                )
                newQueue.add(currentItem.copy(fsrsCard = updatedFsrs))
            }

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
    }

    fun deleteCurrentCard() {
        val state = _uiState.value
        val currentItem = state.currentItem ?: return

        viewModelScope.launch {
            reviewRepository.deleteCard(currentItem.flashCardEntityId)
            advanceQueue()
        }
    }

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
        val tokens = currentItem.tokens

        var token = tokens.find { it.startIndex <= offset && it.endIndex > offset } ?: return

        if (!token.isWord) {
            val closestWord = tokens.minByOrNull {
                if (!it.isWord) Int.MAX_VALUE
                else minOf(abs(it.startIndex - offset), abs(it.endIndex - offset))
            }
            if (closestWord != null) {
                token = closestWord
            }
        }

        dragStartTokenIndex = tokens.indexOf(token)
        _uiState.value = _uiState.value.copy(selectionRange = token.startIndex until token.endIndex)
    }

    fun onSelectionDrag(offset: Int) {
        val state = _uiState.value
        val currentItem = state.currentItem ?: return
        if (dragStartTokenIndex == -1) return
        val tokens = currentItem.tokens

        var currentToken = tokens.find { it.startIndex <= offset && it.endIndex > offset } ?: return

        if (!currentToken.isWord) {
            val closestWord = tokens.minByOrNull {
                if (!it.isWord) Int.MAX_VALUE
                else minOf(abs(it.startIndex - offset), abs(it.endIndex - offset))
            }
            if (closestWord != null) {
                currentToken = closestWord
            }
        }

        val currentIndex = tokens.indexOf(currentToken)
        if (currentIndex != -1) {
            val startToken = tokens[min(dragStartTokenIndex, currentIndex)]
            val endToken = tokens[max(dragStartTokenIndex, currentIndex)]
            _uiState.value = state.copy(selectionRange = startToken.startIndex until endToken.endIndex)
        }
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
        val normalized = word.trim().lowercase()

        // Блокируем перевод при одиночном тапе на тестируемое или жёлтое слово, предотвращая подглядывание
        if (!normalized.contains(" ")) {
            if (normalized == currentItem.word.lowercase()) return
            val status = currentItem.wordMeta[normalized]?.status
            if (status == UiWordStatus.YELLOW) return
        }

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            val meta = if (word.contains(" ")) {
                vocabularyRepository.getWordMetas(listOf(word))[word] ?: WordMeta(UiWordStatus.BLUE)
            } else {
                currentItem.wordMeta[word] ?: WordMeta(UiWordStatus.BLUE)
            }

            if (meta.status == UiWordStatus.BLUE || meta.status == UiWordStatus.TRANSPARENT) {
                // Асинхронно собираем поток перевода фразы/предложения
                vocabularyRepository.fetchMultiTranslations(word, selectedLanguage)
                    .collect { progressiveTrans ->
                        // Считываем пользовательскую настройку маскирования из SharedPreferences
                        val prefs = AppModule.context.getSharedPreferences("linter_settings", Context.MODE_PRIVATE)
                        val enableMasking = prefs.getBoolean("pref_enable_masking", true)

                        // ИСПРАВЛЕНИЕ: Маскируем переводы жёлтых слов и тестируемого слова ТОЛЬКО если маскирование включено
                        val maskedTrans = if (enableMasking) {
                            MultiTranslation(
                                mlKit = maskTranslationText(progressiveTrans.mlKit, currentItem),
                                onnx = maskTranslationText(progressiveTrans.onnx, currentItem),
                                cloud = maskTranslationText(progressiveTrans.cloud, currentItem),
                                custom = maskTranslationText(progressiveTrans.custom, currentItem)
                            )
                        } else {
                            progressiveTrans
                        }

                        _uiState.value = state.copy(
                            popupState = PopupState.NewWord(word, maskedTrans, currentItem.contextSentence)
                        )
                    }
            } else {
                _uiState.value = state.copy(popupState = PopupState.LearningWord(word, meta, currentItem.contextSentence))
            }
        }
    }

    // ИСПРАВЛЕНИЕ: Метод нечеткого стемминга и маскирования изучаемых слов и тестируемого слова
    private fun maskTranslationText(text: String?, currentItem: ReviewItem): String? {
        if (text == null) return null
        val result = text

        // Находим все слова предложения, которые имеют статус YELLOW (изучаемые)
        val yellowWords = currentItem.wordMeta.filter { it.value.status == UiWordStatus.YELLOW }.keys
        val translationsToMask = mutableListOf<String>()

        // Сбор переводов жёлтых слов
        for (yw in yellowWords) {
            val meta = currentItem.wordMeta[yw] ?: continue
            meta.translations?.let { trans ->
                trans.custom?.let { translationsToMask.add(it) }
                trans.mlKit?.let { translationsToMask.add(it) }
                trans.onnx?.let { translationsToMask.add(it) }
                trans.cloud?.let { translationsToMask.add(it) }
            }
            translationsToMask.add(yw) // На всякий случай добавляем оригинал слова
        }

        // Обязательно добавляем само тестируемое слово карточки и его переводы
        val targetWord = currentItem.word.lowercase()
        currentItem.translations.let { trans ->
            trans.custom?.let { translationsToMask.add(it) }
            trans.mlKit?.let { translationsToMask.add(it) }
            trans.onnx?.let { translationsToMask.add(it) }
            trans.cloud?.let { translationsToMask.add(it) }
        }
        translationsToMask.add(targetWord)

        // Нормализуем цели маскирования (убираем лишние знаки и приводим к нижнему регистру)
        val cleanTargets = translationsToMask
            .map { it.trim().lowercase().replace(Regex("[^\\p{L}\\d]"), "") }
            .filter { it.length >= 2 }
            .distinct()

        if (cleanTargets.isEmpty()) return result

        // Разбиваем предложение на токены, сохраняя пунктуацию и пробелы
        val tokensInResult = result.split(Regex("(?<=[\\s\\p{Punct}])|(?=[\\s\\p{Punct}])"))
        val maskedTokens = tokensInResult.map { token ->
            val cleanWord = token.lowercase().replace(Regex("[^\\p{L}\\d]"), "")
            if (cleanWord.isEmpty()) return@map token

            var shouldMask = false
            for (target in cleanTargets) {
                if (cleanWord == target) {
                    shouldMask = true
                    break
                }
                // Нечеткий стемминг (Fuzzy Root Match): если совпадает корень длиной от 4 символов
                val minLen = minOf(cleanWord.length, target.length)
                if (minLen >= 4) {
                    val commonPrefixLength = cleanWord.zip(target).takeWhile { it.first == it.second }.size
                    if (commonPrefixLength >= 4 || (commonPrefixLength.toFloat() / target.length.toFloat()) >= 0.7f) {
                        shouldMask = true
                        break
                    }
                }
            }
            if (shouldMask) "███" else token
        }

        return maskedTokens.joinToString("")
    }

    private suspend fun addReviewItemToQueue(contextCardId: Long) {
        val state = _uiState.value
        val newItem = reviewRepository.getReviewItemByContextCardId(contextCardId) ?: return

        if (state.queue.any { it.contextCardId == contextCardId }) return

        val updatedQueue = state.queue.toMutableList()

        if (state.currentItem == null) {
            updatedQueue.add(newItem)
        } else {
            updatedQueue.add(1, newItem)
        }

        val (b, r, g) = calculateCounters(updatedQueue)
        val currentItem = state.currentItem ?: newItem

        _uiState.value = state.copy(
            queue = updatedQueue,
            currentItem = currentItem,
            currentBucket = getBucketForItem(currentItem),
            isFinished = false,
            blueCount = b,
            redCount = r,
            greenCount = g
        )
    }

    fun onStartLearning(word: String, translations: MultiTranslation, contextSentence: String) {
        viewModelScope.launch {
            val newContextCardId = vocabularyRepository.createLearningCard(word, 0L, 0L, contextSentence, translations, LearningStatus.NEW)
            addReviewItemToQueue(newContextCardId)
            refreshCurrentCard()
            dismissPopup()
        }
    }

    fun onMarkAsKnown(word: String, contextCardId: Long? = null) {
        viewModelScope.launch {
            if (contextCardId != null) {
                vocabularyRepository.moveCardToKnown(contextCardId, word)
            } else {
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
            addReviewItemToQueue(cardId)
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