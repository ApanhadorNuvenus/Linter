package com.example.linter.domain.repository

import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.domain.model.WordMeta
import kotlinx.coroutines.flow.Flow


interface VocabularyRepository {
    suspend fun getWordMetas(words: List<String>): Map<String, WordMeta>
    suspend fun getLearningPhrasesMetas(): List<Pair<String, WordMeta>>

    // ИЗМЕНЕНИЕ: Возвращает MultiTranslation
    suspend fun fetchMultiTranslations(wordOrPhrase: String, sourceLang: String): Flow<MultiTranslation>
    suspend fun updateCustomTranslation(cardId: Long, customTranslation: String?)

    suspend fun markAsKnown(word: String)
    suspend fun markAsIgnored(word: String)

    // ИЗМЕНЕНИЕ: Принимает MultiTranslation
    suspend fun createLearningCard(
        word: String, lectureId: Long, youtubeVideoId: Long = 0L,
        contextSentence: String, translations: MultiTranslation, status: LearningStatus
    )

    suspend fun updateCardStatus(cardId: Long, newStatus: LearningStatus)
    suspend fun moveCardToKnown(cardId: Long, word: String)
}