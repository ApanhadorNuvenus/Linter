package com.example.linter.domain.repository

import com.example.linter.domain.model.Familiarity
import com.example.linter.domain.model.Word

interface WordRepository {
    suspend fun addWordsIfNotExist(words: List<String>)
    suspend fun getTranslation(word: String, sourceLang: String, targetLang: String): String
    suspend fun getWord(word: String): Word?
    suspend fun updateFamiliarity(word: String, familiarity: Familiarity)
    suspend fun getFamiliarityMap(words: List<String>): Map<String, Familiarity>
    suspend fun getTranslationMap(words: List<String>, sourceLang: String, targetLang: String): Map<String, String>
}