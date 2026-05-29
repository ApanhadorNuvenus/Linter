package com.example.linter.domain.repository

import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.domain.model.WordMeta
import kotlinx.coroutines.flow.Flow

interface VocabularyRepository {
    suspend fun getWordMetas(words: List<String>): Map<String, WordMeta>
    suspend fun getLearningPhrasesMetas(): List<Pair<String, WordMeta>>
    suspend fun fetchMultiTranslations(wordOrPhrase: String, sourceLang: String): Flow<MultiTranslation>

    // Обновленная сигнатура метода: сохраняет перевод и контекст при пометке "Знаю"
    suspend fun markAsKnown(
        word: String,
        translations: MultiTranslation? = null,
        contextSentence: String = "",
        lectureId: Long = 0L,
        youtubeVideoId: Long = 0L
    )

    suspend fun markAsIgnored(word: String)
    suspend fun createLearningCard(
        word: String, lectureId: Long, youtubeVideoId: Long = 0L,
        contextSentence: String, translations: MultiTranslation, status: LearningStatus
    )
    suspend fun updateCardStatus(cardId: Long, newStatus: LearningStatus)
    suspend fun moveCardToKnown(cardId: Long, word: String)
    suspend fun updateCustomTranslation(cardId: Long, customTranslation: String?)
}