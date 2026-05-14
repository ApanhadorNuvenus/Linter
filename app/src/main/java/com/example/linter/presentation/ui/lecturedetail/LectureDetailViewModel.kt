package com.example.linter.presentation.ui.lecturedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linter.data.model.AndroidBreakIteratorTokenizer
import com.example.linter.di.AppModule
import com.example.linter.domain.model.Familiarity
import com.example.linter.domain.model.Token
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.linter.presentation.ui.components.WordMeta
import kotlin.math.max
import kotlin.math.min

// Новая абстракция: что нужно сделать после закрытия диалога
sealed class PageAction {
    data class TurnPage(val page: Int) : PageAction()
    object FinishLecture : PageAction()
}

data class LectureDetailUiState(
    val title: String = "",
    val text: String = "",
    val language: String = "en",
    val tokens: List<Token> = emptyList(),
    val wordMeta: Map<String, WordMeta> = emptyMap(),
    val phraseRanges: List<Pair<IntRange, WordMeta>> = emptyList(),
    val selectionRange: IntRange? = null,
    val isLoading: Boolean = true,

    val popupWord: String? = null,
    val popupTranslation: String? = null,
    val popupFamiliarity: Familiarity = Familiarity.UNKNOWN,

    val showEndOfPageDialog: Boolean = false,
    val unknownWordsOnPage: List<String> = emptyList(),
    val pendingAction: PageAction? = null
)

class LectureDetailViewModel : ViewModel() {
    private val lectureRepository = AppModule.lectureRepository
    private val wordRepository = AppModule.wordRepository
    private val getLectureWithWordInfoUseCase = AppModule.getLectureWithWordInfoUseCase
    private val updateWordFamiliarityUseCase = AppModule.updateWordFamiliarityUseCase
    private val tokenizer = AndroidBreakIteratorTokenizer()

    private val _uiState = MutableStateFlow(LectureDetailUiState())
    val uiState: StateFlow<LectureDetailUiState> = _uiState.asStateFlow()

    private var dragStartTokenIndex: Int = -1

    fun loadLecture(lectureId: Long) {
        viewModelScope.launch {
            val lecture = lectureRepository.getLectureById(lectureId) ?: return@launch
            val tokens = tokenizer.tokenize(lecture.text)
            val words = tokens.filter { it.isWord }.map { it.value.lowercase() }.distinct()

            val (famMap, transMap) = getLectureWithWordInfoUseCase(words, lecture.language, "ru")
            val meta = words.associateWith {
                WordMeta(famMap[it] ?: Familiarity.UNKNOWN, transMap[it])
            }

            _uiState.value = _uiState.value.copy(
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
        val allPhrases = wordRepository.getAllKnownPhrases()
        val phraseRanges = mutableListOf<Pair<IntRange, WordMeta>>()

        allPhrases.forEach { phrase ->
            var index = state.text.indexOf(phrase.text, ignoreCase = true)
            while (index >= 0) {
                phraseRanges.add((index until index + phrase.text.length) to WordMeta(phrase.familiarity, phrase.translation))
                index = state.text.indexOf(phrase.text, startIndex = index + phrase.text.length, ignoreCase = true)
            }
        }
        _uiState.value = _uiState.value.copy(phraseRanges = phraseRanges)
    }

    // ЛОГИКА: Попытка перейти на другую страницу
    fun onAttemptPageTurn(pageCharRange: IntRange, targetPage: Int) {
        checkUnknownWords(pageCharRange, PageAction.TurnPage(targetPage))
    }

    // ЛОГИКА: Попытка закрыть лекцию
    fun onAttemptFinish(pageCharRange: IntRange) {
        checkUnknownWords(pageCharRange, PageAction.FinishLecture)
    }

    private fun checkUnknownWords(pageCharRange: IntRange, action: PageAction) {
        val state = _uiState.value
        val tokensOnPage = state.tokens.filter { it.startIndex >= pageCharRange.first && it.endIndex <= pageCharRange.last + 1 }
        val wordsOnPage = tokensOnPage.filter { it.isWord }.map { it.value.lowercase() }.distinct()

        val unknownWords = wordsOnPage.filter { state.wordMeta[it]?.familiarity == Familiarity.UNKNOWN }

        if (unknownWords.isNotEmpty()) {
            _uiState.value = state.copy(
                showEndOfPageDialog = true,
                unknownWordsOnPage = unknownWords,
                pendingAction = action
            )
        } else {
            // Если неизвестных слов нет, сразу выполняем действие
            _uiState.value = state.copy(pendingAction = action)
        }
    }

    fun processEndOfPageWords(selectedAsLearning: List<String>) {
        val state = _uiState.value
        viewModelScope.launch {
            state.unknownWordsOnPage.forEach { word ->
                if (selectedAsLearning.contains(word)) {
                    updateWordFamiliarityUseCase(word, Familiarity.LEARNING)
                } else {
                    updateWordFamiliarityUseCase(word, Familiarity.FAMILIAR)
                }
            }

            val updatedMeta = state.wordMeta.toMutableMap()
            state.unknownWordsOnPage.forEach { word ->
                val newStatus = if (selectedAsLearning.contains(word)) Familiarity.LEARNING else Familiarity.FAMILIAR
                updatedMeta[word] = WordMeta(newStatus, updatedMeta[word]?.translation)
            }

            _uiState.value = state.copy(
                wordMeta = updatedMeta,
                showEndOfPageDialog = false,
                unknownWordsOnPage = emptyList()
                // pendingAction остаётся, чтобы UI мог выполнить переход/выход
            )
        }
    }

    fun dismissEndOfPageDialog() {
        // При отмене мы скрываем диалог и сбрасываем действие, оставаясь на текущей странице
        _uiState.value = _uiState.value.copy(showEndOfPageDialog = false, pendingAction = null)
    }

    fun clearPendingAction() {
        _uiState.value = _uiState.value.copy(pendingAction = null)
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
            handleWordOrPhraseOpen(word)
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
        handleWordOrPhraseOpen(phrase)
    }

    private fun handleWordOrPhraseOpen(wordOrPhrase: String) {
        val state = _uiState.value
        val isPhrase = wordOrPhrase.contains(" ")

        viewModelScope.launch {
            val existingFam = if (isPhrase) {
                wordRepository.getWord(wordOrPhrase)?.familiarity ?: Familiarity.UNKNOWN
            } else {
                state.wordMeta[wordOrPhrase]?.familiarity ?: Familiarity.UNKNOWN
            }

            // ИСПРАВЛЕНИЕ: Мы применяем автоматическое "Учу" ТОЛЬКО к одиночным словам.
            // Фразы остаются UNKNOWN, пока пользователь сам не изменит их статус.
            val finalFam = if (!isPhrase && existingFam == Familiarity.UNKNOWN) Familiarity.LEARNING else existingFam

            val trans = wordRepository.getTranslation(
                word = wordOrPhrase,
                sourceLang = state.language,
                targetLang = "ru",
                saveToDb = finalFam == Familiarity.LEARNING // В БД сохраняем только если "Учу" или уже известно
            )

            if (!isPhrase && existingFam == Familiarity.UNKNOWN) {
                updateWordFamiliarityUseCase(wordOrPhrase, Familiarity.LEARNING)

                val updatedMeta = _uiState.value.wordMeta.toMutableMap()
                updatedMeta[wordOrPhrase] = WordMeta(Familiarity.LEARNING, trans)
                _uiState.value = _uiState.value.copy(wordMeta = updatedMeta)
            }

            _uiState.value = _uiState.value.copy(
                popupWord = wordOrPhrase,
                popupTranslation = trans,
                popupFamiliarity = finalFam
            )
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectionRange = null)
        dragStartTokenIndex = -1
    }

    fun changeFamiliarity(wordOrPhrase: String, familiarity: Familiarity) {
        val state = _uiState.value
        viewModelScope.launch {
            if (wordOrPhrase.contains(" ") && familiarity != Familiarity.UNKNOWN) {
                wordRepository.getTranslation(wordOrPhrase, state.language, "ru", saveToDb = true)
            }
            updateWordFamiliarityUseCase(wordOrPhrase, familiarity)

            if (wordOrPhrase.contains(" ")) {
                recalculatePhraseRanges()
            } else {
                val updatedMeta = state.wordMeta.toMutableMap()
                updatedMeta[wordOrPhrase.lowercase()] = WordMeta(familiarity, updatedMeta[wordOrPhrase.lowercase()]?.translation)
                _uiState.value = state.copy(wordMeta = updatedMeta)
            }
            _uiState.value = _uiState.value.copy(popupFamiliarity = familiarity)
        }
    }

    fun dismissPopup() {
        _uiState.value = _uiState.value.copy(popupWord = null, selectionRange = null)
    }
}