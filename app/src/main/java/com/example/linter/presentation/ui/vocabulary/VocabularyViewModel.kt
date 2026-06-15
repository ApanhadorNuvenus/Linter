package com.example.linter.presentation.ui.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linter.di.AppModule
import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.domain.model.WordMeta
import com.example.linter.presentation.ui.lecturedetail.PopupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VocabularyUiState(
    val words: List<com.example.linter.data.local.entity.VocabularyItemEntity> = emptyList(),
    val wordMetas: Map<String, WordMeta> = emptyMap(),
    val searchQuery: String = "",
    val selectedLanguage: String = "All",
    val selectedStatus: String = "All",
    val isLoading: Boolean = true,
    val popupState: PopupState = PopupState.Hidden
)

class VocabularyViewModel : ViewModel() {
    private val vocabularyRepository = AppModule.vocabularyRepository
    private val vocabBox = com.example.linter.data.local.ObjectBox.vocabularyBox
    private val cardBox = com.example.linter.data.local.ObjectBox.contextCardBox

    private val _uiState = MutableStateFlow(VocabularyUiState())
    val uiState: StateFlow<VocabularyUiState> = _uiState.asStateFlow()

    private var translationJob: kotlinx.coroutines.Job? = null

    init {
        loadVocabulary()
    }

    fun loadVocabulary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val allItems = vocabBox.all
            val wordsList = allItems.map { it.text }
            val metas = vocabularyRepository.getWordMetas(wordsList)

            _uiState.value = _uiState.value.copy(
                words = allItems,
                wordMetas = metas,
                isLoading = false
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onLanguageSelected(lang: String) {
        _uiState.value = _uiState.value.copy(selectedLanguage = lang)
    }

    fun onStatusSelected(status: String) {
        _uiState.value = _uiState.value.copy(selectedStatus = status)
    }

    fun onWordClicked(word: String) {
        val meta = _uiState.value.wordMetas[word.lowercase()] ?: return
        val activeCard = cardBox.all.find { it.vocabularyItemId > 0L && vocabBox[it.vocabularyItemId]?.text == word }
        val sentence = activeCard?.contextSentence ?: "Контекстное предложение отсутствует"

        _uiState.value = _uiState.value.copy(
            popupState = PopupState.LearningWord(word, meta, sentence)
        )
    }

    // ИСПРАВЛЕНИЕ: Вызывается напрямую из UI
    fun playTts(word: String) {
        val meta = _uiState.value.wordMetas[word.lowercase()]
        val lang = meta?.language ?: "en"
        AppModule.ttsRepository.speak(word, lang)
    }

    fun dismissPopup() {
        translationJob?.cancel()
        AppModule.ttsRepository.stop()
        _uiState.value = _uiState.value.copy(popupState = PopupState.Hidden)
    }

    fun onSaveCustomTranslation(cardId: Long, word: String, customTranslation: String) {
        viewModelScope.launch {
            vocabularyRepository.updateCustomTranslation(cardId, customTranslation)
            refreshSingleWord(word)
        }
    }

    fun onChangeLearningStatus(cardId: Long, word: String, status: LearningStatus) {
        viewModelScope.launch {
            vocabularyRepository.updateCardStatus(cardId, status)
            refreshSingleWord(word)
        }
    }

    fun onMarkAsKnown(word: String, cardId: Long?) {
        viewModelScope.launch {
            if (cardId != null) vocabularyRepository.moveCardToKnown(cardId, word)
            else vocabularyRepository.markAsKnown(word)
            refreshSingleWord(word)
            dismissPopup()
        }
    }

    private suspend fun refreshSingleWord(word: String) {
        val updatedMetas = _uiState.value.wordMetas.toMutableMap()
        val newMeta = vocabularyRepository.getWordMetas(listOf(word))[word.lowercase()]
        if (newMeta != null) {
            updatedMetas[word.lowercase()] = newMeta
        }
        _uiState.value = _uiState.value.copy(wordMetas = updatedMetas)
    }
}