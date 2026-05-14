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
    val popupFamiliarity: Familiarity = Familiarity.UNKNOWN
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

            // Загружаем слова
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

            // Ищем известные фразы в тексте
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

    // Обработка обычного клика по слову
    fun onWordClicked(offset: Int) {
        val state = _uiState.value

        // Если было выделение - просто сбрасываем его при любом клике
        if (state.selectionRange != null) {
            clearSelection()
            return
        }

        val token = state.tokens.find { it.startIndex <= offset && it.endIndex > offset && it.isWord }
        if (token != null) {
            val word = token.value
            val meta = state.wordMeta[word.lowercase()]
            _uiState.value = state.copy(
                popupWord = word,
                popupTranslation = meta?.translation,
                popupFamiliarity = meta?.familiarity ?: Familiarity.UNKNOWN
            )
        }
    }

    // НАЧАЛО ВЫДЕЛЕНИЯ (Долгое нажатие)
    fun onSelectionStart(offset: Int) {
        val state = _uiState.value
        val token = state.tokens.find { it.startIndex <= offset && it.endIndex > offset } ?: return
        dragStartTokenIndex = state.tokens.indexOf(token)

        _uiState.value = state.copy(
            selectionRange = token.startIndex until token.endIndex
        )
    }

    // ИЗМЕНЕНИЕ ВЫДЕЛЕНИЯ (Перетаскивание)
    fun onSelectionDrag(offset: Int) {
        val state = _uiState.value
        if (dragStartTokenIndex == -1) return

        val currentToken = state.tokens.find { it.startIndex <= offset && it.endIndex > offset }
        val currentIndex = currentToken?.let { state.tokens.indexOf(it) } ?: return

        val startToken = state.tokens[min(dragStartTokenIndex, currentIndex)]
        val endToken = state.tokens[max(dragStartTokenIndex, currentIndex)]

        _uiState.value = state.copy(
            selectionRange = startToken.startIndex until endToken.endIndex
        )
    }

    // ОКОНЧАНИЕ ВЫДЕЛЕНИЯ (Отпускание пальца)
    fun onSelectionEnd() {
        val state = _uiState.value
        val range = state.selectionRange ?: return
        dragStartTokenIndex = -1

        val phrase = state.text.substring(range).trim()
        if (phrase.isBlank()) {
            clearSelection()
            return
        }

        // Если выделили одно слово - показываем как слово
        if (!phrase.contains(" ")) {
            val meta = state.wordMeta[phrase.lowercase()]
            _uiState.value = state.copy(
                popupWord = phrase,
                popupTranslation = meta?.translation,
                popupFamiliarity = meta?.familiarity ?: Familiarity.UNKNOWN
            )
            return
        }

        // Если это фраза, переводим без сохранения в БД
        viewModelScope.launch {
            val translation = wordRepository.getTranslation(phrase, state.language, "ru", saveToDb = false)
            // Ищем, не сохраняли ли мы уже ее статус
            val existingWord = wordRepository.getWord(phrase)

            _uiState.value = _uiState.value.copy(
                popupWord = phrase,
                popupTranslation = translation,
                popupFamiliarity = existingWord?.familiarity ?: Familiarity.UNKNOWN
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
            // Если это фраза (содержит пробел), при сохранении статуса она будет создана в БД
            if (wordOrPhrase.contains(" ") && familiarity != Familiarity.UNKNOWN) {
                // Обязательно сохраняем перевод в БД, если юзер выбрал статус
                wordRepository.getTranslation(wordOrPhrase, state.language, "ru", saveToDb = true)
            }

            updateWordFamiliarityUseCase(wordOrPhrase, familiarity)

            if (wordOrPhrase.contains(" ")) {
                recalculatePhraseRanges() // Обновляем подсветку фраз
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