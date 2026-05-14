package com.example.linter.domain.repository

import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.WordMeta

interface VocabularyRepository {
    // Получить состояние слов для UI
    suspend fun getWordMetas(words: List<String>): Map<String, WordMeta>

    // Получить все желтые фразы (для подсветки фраз в тексте)
    suspend fun getLearningPhrasesMetas(): List<Pair<String, WordMeta>>

    // Перевод "на лету" без сохранения
    suspend fun fetchTranslation(wordOrPhrase: String, sourceLang: String): String

    // Действия пользователя
    suspend fun markAsKnown(word: String)
    suspend fun markAsIgnored(word: String)
    suspend fun createLearningCard(word: String, lectureId: Long, contextSentence: String, translation: String, status: LearningStatus)
    suspend fun updateCardStatus(cardId: Long, newStatus: LearningStatus)
    suspend fun moveCardToKnown(cardId: Long, word: String)
}