package com.example.linter.domain.usecase

import com.example.linter.domain.model.Familiarity
import com.example.linter.domain.model.Word
import com.example.linter.domain.repository.LectureRepository
import com.example.linter.domain.repository.WordRepository
import com.example.linter.domain.model.TextTokenizer

class GetLearningWordsForLectureUseCase(
    private val lectureRepository: LectureRepository,
    private val wordRepository: WordRepository,
    private val tokenizer: TextTokenizer
) {
    suspend operator fun invoke(lectureId: Long): List<Word> {
        // 1. Берем текст лекции
        val lecture = lectureRepository.getLectureById(lectureId) ?: return emptyList()

        // 2. Вытаскиваем все уникальные слова на лету
        val tokens = tokenizer.tokenize(lecture.text)
        val uniqueWordsInText = tokens.filter { it.isWord }.map { it.value.lowercase() }.distinct()

        // 3. Берем из БД только те слова, которые есть в тексте И имеют статус LEARNING
        val familiarityMap = wordRepository.getFamiliarityMap(uniqueWordsInText)
        val learningWordStrings = uniqueWordsInText.filter { familiarityMap[it] == Familiarity.LEARNING }

        // 4. Загружаем полноценные модели Word (с переводами) для карточек
        val resultList = mutableListOf<Word>()
        for (wordStr in learningWordStrings) {
            wordRepository.getWord(wordStr)?.let { resultList.add(it) }
        }

        return resultList
    }
}