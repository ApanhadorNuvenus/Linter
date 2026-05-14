package com.example.linter.domain.usecase

import com.example.linter.domain.model.Familiarity
import com.example.linter.domain.repository.WordRepository
import com.example.linter.domain.repository.FSRSRepository

class UpdateWordFamiliarityUseCase(
    private val wordRepository: WordRepository,
    private val fsrsRepository: FSRSRepository
) {
    suspend operator fun invoke(word: String, familiarity: Familiarity) {
        wordRepository.updateFamiliarity(word, familiarity)
        fsrsRepository.onFamiliarityChanged(word, familiarity)
    }
}