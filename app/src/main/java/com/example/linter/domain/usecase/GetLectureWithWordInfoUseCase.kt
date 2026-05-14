package com.example.linter.domain.usecase

import com.example.linter.domain.model.Familiarity
import com.example.linter.domain.repository.WordRepository

class GetLectureWithWordInfoUseCase(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(
        words: List<String>,
        sourceLang: String,
        targetLang: String
    ): Pair<Map<String, Familiarity>, Map<String, String>> {
        val familiarityMap = wordRepository.getFamiliarityMap(words.distinct())
        val translationMap = wordRepository.getTranslationMap(words.distinct(), sourceLang, targetLang)
        return familiarityMap to translationMap
    }
}