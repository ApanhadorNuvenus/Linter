package com.example.linter.presentation.ui.lecturedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linter.data.model.AndroidBreakIteratorTokenizer
import com.example.linter.di.AppModule
import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.Token
import com.example.linter.domain.model.UiWordStatus
import com.example.linter.domain.model.WordMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.BreakIterator
import kotlin.math.max
import kotlin.math.min

sealed class PageAction {
    data class TurnPage(val page: Int) : PageAction()
    object FinishLecture : PageAction()
}

sealed class PopupState {
    object Hidden : PopupState()
    data class NewWord(val wordOrPhrase: String, val translation: String, val contextSentence: String) : PopupState()
    data class LearningWord(val wordOrPhrase: String, val meta: WordMeta, val contextSentence: String) : PopupState()
}

data class LectureDetailUiState(
    val lectureId: Long = 0,
    val title: String = "",
    val text: String = "",
    val language: String = "en",
    val tokens: List<Token> = emptyList(),
    val wordMeta: Map<String, WordMeta> = emptyMap(),
    val phraseRanges: List<Pair<IntRange, WordMeta>> = emptyList(),
    val selectionRange: IntRange? = null,
    val isLoading: Boolean = true,

    val popupState: PopupState = PopupState.Hidden,

    val showEndOfPageDialog: Boolean = false,
    val unknownWordsOnPage: List<String> = emptyList(),
    val pendingAction: PageAction? = null
)

class LectureDetailViewModel : ViewModel() {
    private val lectureRepository = AppModule.lectureRepository
    private val vocabularyRepository = AppModule.vocabularyRepository
    private val tokenizer = AndroidBreakIteratorTokenizer()

    private val _uiState = MutableStateFlow(LectureDetailUiState())
    val uiState: StateFlow<LectureDetailUiState> = _uiState.asStateFlow()

    private var dragStartTokenIndex: Int = -1

    fun loadLecture(lectureId: Long) {
        viewModelScope.launch {
            val lecture = lectureRepository.getLectureById(lectureId) ?: return@launch
            val tokens = tokenizer.tokenize(lecture.text)
            val words = tokens.filter { it.isWord }.map { it.value.lowercase() }.distinct()

            val meta = vocabularyRepository.getWordMetas(words)

            _uiState.value = _uiState.value.copy(
                lectureId = lectureId,
                title = lecture.title,
                text = lecture.text,
                language = lecture.language,
                tokens = tokens,
                wordMeta = meta,
                isLoading = false
            )
            recalculatePhraseRanges()
        }
    }

    private suspend fun recalculatePhraseRanges() {
        val state = _uiState.value
        val learningPhrases = vocabularyRepository.getLearningPhrasesMetas()
        val phraseRanges = mutableListOf<Pair<IntRange, WordMeta>>()

        learningPhrases.forEach { (phrase, meta) ->
            var index = state.text.indexOf(phrase, ignoreCase = true)
            while (index >= 0) {
                phraseRanges.add((index until index + phrase.length) to meta)
                index = state.text.indexOf(phrase, startIndex = index + phrase.length, ignoreCase = true)
            }
        }
        _uiState.value = _uiState.value.copy(phraseRanges = phraseRanges)
    }

    private fun extractSentence(text: String, startIndex: Int): String {
        val iterator = BreakIterator.getSentenceInstance()
        iterator.setText(text)
        val start = iterator.preceding(startIndex).takeIf { it != BreakIterator.DONE } ?: 0
        val end = iterator.following(startIndex).takeIf { it != BreakIterator.DONE } ?: text.length
        return text.substring(start, end).trim().replace("\n", " ")
    }

    fun onWordClicked(offset: Int) {
        val state = _uiState.value
        if (state.selectionRange != null) {
            clearSelection()
            return
        }

        val token = state.tokens.find { it.startIndex <= offset && it.endIndex > offset && it.isWord }
        if (token != null) {
            val word = token.value.lowercase()
            handleWordOrPhraseOpen(word, token.startIndex)
        }
    }

    fun onSelectionStart(offset: Int) {
        val state = _uiState.value
        val token = state.tokens.find { it.startIndex <= offset && it.endIndex > offset } ?: return
        dragStartTokenIndex = state.tokens.indexOf(token)
        _uiState.value = state.copy(selectionRange = token.startIndex until token.endIndex)
    }

    fun onSelectionDrag(offset: Int) {
        val state = _uiState.value
        if (dragStartTokenIndex == -1) return
        val currentToken = state.tokens.find { it.startIndex <= offset && it.endIndex > offset }
        val currentIndex = currentToken?.let { state.tokens.indexOf(it) } ?: return
        val startToken = state.tokens[min(dragStartTokenIndex, currentIndex)]
        val endToken = state.tokens[max(dragStartTokenIndex, currentIndex)]
        _uiState.value = state.copy(selectionRange = startToken.startIndex until endToken.endIndex)
    }

    fun onSelectionEnd() {
        val state = _uiState.value
        val range = state.selectionRange ?: return
        dragStartTokenIndex = -1

        val phrase = state.text.substring(range).trim().lowercase()
        if (phrase.isBlank()) {
            clearSelection()
            return
        }
        handleWordOrPhraseOpen(phrase, range.first)
    }

    private fun handleWordOrPhraseOpen(wordOrPhrase: String, startIndex: Int) {
        val state = _uiState.value
        val contextSentence = extractSentence(state.text, startIndex)

        viewModelScope.launch {
            val meta = if (wordOrPhrase.contains(" ")) {
                vocabularyRepository.getWordMetas(listOf(wordOrPhrase))[wordOrPhrase] ?: WordMeta(UiWordStatus.BLUE)
            } else {
                state.wordMeta[wordOrPhrase] ?: WordMeta(UiWordStatus.BLUE)
            }

            if (meta.status == UiWordStatus.BLUE || meta.status == UiWordStatus.TRANSPARENT) {
                val trans = vocabularyRepository.fetchTranslation(wordOrPhrase, state.language)
                _uiState.value = state.copy(popupState = PopupState.NewWord(wordOrPhrase, trans, contextSentence))
            } else {
                _uiState.value = state.copy(popupState = PopupState.LearningWord(wordOrPhrase, meta, contextSentence))
            }
        }
    }

    fun onStartLearning(word: String, translation: String, contextSentence: String) {
        val state = _uiState.value
        viewModelScope.launch {
            // ИЗМЕНЕНИЕ: передаем 0L как youtubeVideoId
            vocabularyRepository.createLearningCard(word, state.lectureId, 0L, contextSentence, translation, LearningStatus.NEW)
            refreshWordState(word)
            dismissPopup()
        }
    }

    fun onMarkAsKnown(word: String, contextCardId: Long? = null) {
        viewModelScope.launch {
            if (contextCardId != null) {
                vocabularyRepository.moveCardToKnown(contextCardId, word)
            } else {
                vocabularyRepository.markAsKnown(word)
            }
            refreshWordState(word)
            dismissPopup()
        }
    }

    fun onMarkAsIgnored(word: String) {
        viewModelScope.launch {
            vocabularyRepository.markAsIgnored(word)
            refreshWordState(word)
            dismissPopup()
        }
    }

    fun onChangeLearningStatus(cardId: Long, word: String, newStatus: LearningStatus) {
        viewModelScope.launch {
            vocabularyRepository.updateCardStatus(cardId, newStatus)
            refreshWordState(word)
            dismissPopup()
        }
    }

    private suspend fun refreshWordState(word: String) {
        if (word.contains(" ")) {
            recalculatePhraseRanges()
        } else {
            val updatedMeta = _uiState.value.wordMeta.toMutableMap()
            updatedMeta[word] = vocabularyRepository.getWordMetas(listOf(word))[word]!!
            _uiState.value = _uiState.value.copy(wordMeta = updatedMeta)
        }
    }

    fun dismissPopup() {
        _uiState.value = _uiState.value.copy(popupState = PopupState.Hidden, selectionRange = null)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectionRange = null)
        dragStartTokenIndex = -1
    }

    fun onAttemptPageTurn(pageCharRange: IntRange, targetPage: Int) {
        checkUnknownWords(pageCharRange, PageAction.TurnPage(targetPage))
    }

    fun onAttemptFinish(pageCharRange: IntRange) {
        checkUnknownWords(pageCharRange, PageAction.FinishLecture)
    }

    private fun checkUnknownWords(pageCharRange: IntRange, action: PageAction) {
        val state = _uiState.value
        val tokensOnPage = state.tokens.filter { it.startIndex >= pageCharRange.first && it.endIndex <= pageCharRange.last + 1 }
        val wordsOnPage = tokensOnPage.filter { it.isWord }.map { it.value.lowercase() }.distinct()

        val unknownWords = wordsOnPage.filter { state.wordMeta[it]?.status == UiWordStatus.BLUE }

        if (unknownWords.isNotEmpty()) {
            _uiState.value = state.copy(
                showEndOfPageDialog = true,
                unknownWordsOnPage = unknownWords,
                pendingAction = action
            )
        } else {
            _uiState.value = state.copy(pendingAction = action)
        }
    }

    fun processEndOfPageWords(selectedAsLearning: List<String>) {
        val state = _uiState.value
        viewModelScope.launch {
            state.unknownWordsOnPage.forEach { word ->
                if (selectedAsLearning.contains(word)) {
                    val token = state.tokens.find { it.value.lowercase() == word }
                    val context = if (token != null) extractSentence(state.text, token.startIndex) else ""
                    val trans = vocabularyRepository.fetchTranslation(word, state.language)
                    // ИЗМЕНЕНИЕ: передаем 0L как youtubeVideoId
                    vocabularyRepository.createLearningCard(word, state.lectureId, 0L, context, trans, LearningStatus.NEW)
                } else {
                    vocabularyRepository.markAsKnown(word)
                }
            }

            val updatedMeta = state.wordMeta.toMutableMap()
            val newMetas = vocabularyRepository.getWordMetas(state.unknownWordsOnPage)
            updatedMeta.putAll(newMetas)

            _uiState.value = state.copy(
                wordMeta = updatedMeta,
                showEndOfPageDialog = false,
                unknownWordsOnPage = emptyList()
            )
        }
    }

    fun dismissEndOfPageDialog() {
        _uiState.value = _uiState.value.copy(showEndOfPageDialog = false, pendingAction = null)
    }

    fun clearPendingAction() {
        _uiState.value = _uiState.value.copy(pendingAction = null)
    }
}