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

data class LectureDetailUiState(
    val title: String = "",
    val text: String = "",
    val tokens: List<Token> = emptyList(),
    val wordMeta: Map<String, WordMeta> = emptyMap(),
    val isLoading: Boolean = true,
    val popupWord: String? = null,
    val popupTranslation: String? = null,
    val popupFamiliarity: Familiarity = Familiarity.UNKNOWN
)


class LectureDetailViewModel : ViewModel() {
    private val lectureRepository = AppModule.lectureRepository
    private val getLectureWithWordInfoUseCase = AppModule.getLectureWithWordInfoUseCase
    private val updateWordFamiliarityUseCase = AppModule.updateWordFamiliarityUseCase
    private val tokenizer = AndroidBreakIteratorTokenizer()

    private val _uiState = MutableStateFlow(LectureDetailUiState())
    val uiState: StateFlow<LectureDetailUiState> = _uiState.asStateFlow()

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
                tokens = tokens,
                wordMeta = meta,
                isLoading = false
            )
        }
    }

    fun onWordClicked(word: String) {
        val meta = _uiState.value.wordMeta[word.lowercase()]
        _uiState.value = _uiState.value.copy(
            popupWord = word,
            popupTranslation = meta?.translation,
            popupFamiliarity = meta?.familiarity ?: Familiarity.UNKNOWN
        )
    }

    fun changeFamiliarity(word: String, familiarity: Familiarity) {
        viewModelScope.launch {
            updateWordFamiliarityUseCase(word, familiarity)
            // Обновляем локальное состояние
            val updatedMeta = _uiState.value.wordMeta.toMutableMap()
            updatedMeta[word.lowercase()] = WordMeta(familiarity, updatedMeta[word.lowercase()]?.translation)
            _uiState.value = _uiState.value.copy(
                wordMeta = updatedMeta,
                popupFamiliarity = familiarity
            )
        }
    }

    fun dismissPopup() {
        _uiState.value = _uiState.value.copy(popupWord = null)
    }
}