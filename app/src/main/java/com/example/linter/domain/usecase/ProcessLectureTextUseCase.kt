package com.example.linter.domain.usecase

import com.example.linter.domain.model.Token
import com.example.linter.domain.repository.WordRepository
import com.example.linter.domain.model.TextTokenizer

class ProcessLectureTextUseCase(
    private val wordRepository: WordRepository,
    private val tokenizer: TextTokenizer
) {
    suspend operator fun invoke(text: String) {
        val tokens = tokenizer.tokenize(text)
        val words = tokens.filter { it.isWord }.map { it.value }
        wordRepository.addWordsIfNotExist(words)
    }
}