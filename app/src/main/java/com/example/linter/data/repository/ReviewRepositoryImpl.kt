package com.example.linter.data.repository

import com.example.linter.data.fsrs.CardPhase
import com.example.linter.data.fsrs.FSRS
import com.example.linter.data.fsrs.FlashCard
import com.example.linter.data.fsrs.Grade
import com.example.linter.data.local.ObjectBox
import com.example.linter.data.local.entity.FlashCardEntity_
import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.domain.model.WordMeta
import com.example.linter.domain.model.TextTokenizer
import com.example.linter.domain.repository.ReviewItem
import com.example.linter.domain.repository.ReviewRepository
import com.example.linter.domain.repository.VocabularyRepository

class ReviewRepositoryImpl(
    private val vocabularyRepository: VocabularyRepository,
    private val tokenizer: TextTokenizer
) : ReviewRepository {

    private val flashCardBox get() = ObjectBox.flashCardBox
    private val contextCardBox get() = ObjectBox.contextCardBox
    private val vocabBox get() = ObjectBox.vocabularyBox

    private val fsrs = FSRS(
        requestRetention = 0.9,
        params = listOf(0.4072, 1.1829, 3.1262, 15.4722, 7.2102, 0.5316, 1.0651, 0.0234, 1.616, 0.1544,
            1.0824, 1.9813, 0.0953, 0.2975, 2.2042, 0.2407, 2.9466, 0.0034, 0.5492, 0.7765, 0.4657)
    )

    override suspend fun getDueCardsCount(): Int {
        val now = System.currentTimeMillis()
        return flashCardBox.query(FlashCardEntity_.dueDateMillis.lessOrEqual(now))
            .build()
            .find()
            .count { it.contextCardId > 0L }
    }

    override suspend fun getDueReviewItems(lookaheadMs: Long): List<ReviewItem> {
        val threshold = System.currentTimeMillis() + lookaheadMs

        // Сортируем, чтобы сначала шли самые просроченные (настоящие due), а схлопнутые (lookahead) были в конце батча
        val dueEntities = flashCardBox.query(FlashCardEntity_.dueDateMillis.lessOrEqual(threshold))
            .order(FlashCardEntity_.dueDateMillis)
            .build()
            .find()

        val items = mutableListOf<ReviewItem>()
        val learningPhrases = vocabularyRepository.getLearningPhrasesMetas()

        for (entity in dueEntities) {
            if (entity.contextCardId <= 0L) { flashCardBox.remove(entity); continue }
            val contextCard = contextCardBox[entity.contextCardId]
            if (contextCard == null || contextCard.vocabularyItemId <= 0L) { flashCardBox.remove(entity); continue }
            val vocabItem = vocabBox[contextCard.vocabularyItemId]
            if (vocabItem == null) { flashCardBox.remove(entity); continue }

            val fsrsCard = FlashCard(
                id = entity.id,
                stability = entity.stability,
                difficulty = entity.difficulty,
                interval = entity.interval,
                dueDateMillis = entity.dueDateMillis,
                reviewCount = entity.reviewCount,
                lastReviewMillis = entity.lastReviewMillis,
                phase = entity.phase
            )
            val grades = fsrs.calculate(fsrsCard)

            // "Живой текст" - Подготавливаем токены для контекстного предложения
            val tokens = tokenizer.tokenize(contextCard.contextSentence)
            val wordsInSentence = tokens.filter { it.isWord }.map { it.value.lowercase() }.distinct()
            val wordMeta = vocabularyRepository.getWordMetas(wordsInSentence)

            val phraseRanges = mutableListOf<Pair<IntRange, WordMeta>>()
            learningPhrases.forEach { (phrase, meta) ->
                var idx = contextCard.contextSentence.indexOf(phrase, ignoreCase = true)
                while (idx >= 0) {
                    phraseRanges.add((idx until idx + phrase.length) to meta)
                    idx = contextCard.contextSentence.indexOf(phrase, startIndex = idx + phrase.length, ignoreCase = true)
                }
            }

            // Находим целевое слово, чтобы выделить его визуально
            val targetWordIndex = contextCard.contextSentence.indexOf(vocabItem.text, ignoreCase = true)
            val targetRange = if (targetWordIndex >= 0) targetWordIndex until (targetWordIndex + vocabItem.text.length) else null

            items.add(
                ReviewItem(
                    flashCardEntityId = entity.id,
                    contextCardId = contextCard.id,
                    word = vocabItem.text,
                    contextSentence = contextCard.contextSentence,
                    translations = MultiTranslation(contextCard.translation, contextCard.translationOnnx, contextCard.translationCloud),
                    fsrsCard = fsrsCard,
                    grades = grades,
                    tokens = tokens,
                    wordMeta = wordMeta,
                    phraseRanges = phraseRanges,
                    targetWordRange = targetRange
                )
            )
        }
        return items
    }

    override suspend fun submitReview(flashCardEntityId: Long, grade: Grade) {
        val entity = flashCardBox[flashCardEntityId] ?: return
        val contextCard = contextCardBox[entity.contextCardId] ?: return
        val vocabItem = vocabBox[contextCard.vocabularyItemId] ?: return

        val newInterval = grade.interval
        val newStatus = when {
            newInterval < 1 -> LearningStatus.NEW
            newInterval in 1..5 -> LearningStatus.RECOGNIZED
            newInterval in 6..14 -> LearningStatus.FAMILIAR
            newInterval in 15..30 -> LearningStatus.LEARNED
            else -> null
        }

        if (newStatus == null) {
            flashCardBox.remove(entity)
            vocabularyRepository.moveCardToKnown(contextCard.id, vocabItem.text)
        } else {
            entity.stability = grade.stability
            entity.difficulty = grade.difficulty
            entity.interval = grade.interval
            entity.dueDateMillis = System.currentTimeMillis() + grade.durationMillis
            entity.reviewCount += 1
            entity.lastReviewMillis = System.currentTimeMillis()
            entity.phase = CardPhase.Review.value
            flashCardBox.put(entity)

            vocabularyRepository.updateCardStatus(contextCard.id, newStatus)
        }
    }
}