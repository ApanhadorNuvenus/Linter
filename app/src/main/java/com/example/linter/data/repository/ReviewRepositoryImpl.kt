package com.example.linter.data.repository

import com.example.linter.data.fsrs.CardPhase
import com.example.linter.data.fsrs.FSRS
import com.example.linter.data.fsrs.FlashCard
import com.example.linter.data.fsrs.Grade
import com.example.linter.data.local.ObjectBox
import com.example.linter.data.local.entity.FlashCardEntity_
import com.example.linter.data.local.entity.ContextCardEntity
import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.domain.model.WordMeta
import com.example.linter.domain.model.TextTokenizer
import com.example.linter.domain.repository.ReviewItem
import com.example.linter.domain.repository.ReviewRepository
import com.example.linter.domain.repository.VocabularyRepository
import java.util.Calendar

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

    private fun getCardLanguage(card: ContextCardEntity): String {
        if (card.lectureId > 0L) {
            val lecture = ObjectBox.lectureBox[card.lectureId]
            if (lecture != null) return lecture.language
        }
        if (card.youtubeVideoId > 0L) {
            val video = ObjectBox.store.boxFor(com.example.linter.data.local.entity.YoutubeVideoEntity::class.java)[card.youtubeVideoId]
            if (video != null) {
                return video.language ?: "en"
            }
        }
        return "en"
    }

    override suspend fun getDueCardsCount(lang: String): Int {
        val now = System.currentTimeMillis()
        val dueEntities = flashCardBox.query(FlashCardEntity_.dueDateMillis.lessOrEqual(now))
            .build()
            .find()

        return dueEntities.count { entity ->
            if (entity.contextCardId <= 0L || entity.postponeUntilMillis > now) return@count false
            val card = contextCardBox[entity.contextCardId] ?: return@count false
            getCardLanguage(card) == lang
        }
    }

    override suspend fun getDueReviewItems(lang: String, lookaheadMs: Long): List<ReviewItem> {
        val threshold = System.currentTimeMillis() + lookaheadMs
        val now = System.currentTimeMillis()

        val dueEntities = flashCardBox.query(FlashCardEntity_.dueDateMillis.lessOrEqual(threshold))
            .order(FlashCardEntity_.dueDateMillis)
            .build()
            .find()

        val items = mutableListOf<ReviewItem>()
        val learningPhrases = vocabularyRepository.getLearningPhrasesMetas()

        for (entity in dueEntities) {
            if (entity.contextCardId <= 0L || entity.postponeUntilMillis > now) continue
            val contextCard = contextCardBox[entity.contextCardId] ?: continue
            if (getCardLanguage(contextCard) != lang) continue

            val vocabItem = vocabBox[contextCard.vocabularyItemId] ?: continue

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

            val targetWordIndex = contextCard.contextSentence.indexOf(vocabItem.text, ignoreCase = true)
            val targetRange = if (targetWordIndex >= 0) targetWordIndex until (targetWordIndex + vocabItem.text.length) else null

            items.add(
                ReviewItem(
                    flashCardEntityId = entity.id,
                    contextCardId = contextCard.id,
                    word = vocabItem.text,
                    contextSentence = contextCard.contextSentence,
                    translations = MultiTranslation(contextCard.translation, contextCard.translationOnnx, contextCard.translationCloud, contextCard.translationCustom),
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

    override suspend fun getReviewItemByContextCardId(contextCardId: Long): ReviewItem? {
        val contextCard = contextCardBox[contextCardId] ?: return null
        val entity = flashCardBox.query(FlashCardEntity_.contextCardId.equal(contextCardId)).build().findFirst() ?: return null
        val vocabItem = vocabBox[contextCard.vocabularyItemId] ?: return null

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

        val tokens = tokenizer.tokenize(contextCard.contextSentence)
        val wordsInSentence = tokens.filter { it.isWord }.map { it.value.lowercase() }.distinct()
        val wordMeta = vocabularyRepository.getWordMetas(wordsInSentence)

        val learningPhrases = vocabularyRepository.getLearningPhrasesMetas()
        val phraseRanges = mutableListOf<Pair<IntRange, WordMeta>>()
        learningPhrases.forEach { (phrase, meta) ->
            var idx = contextCard.contextSentence.indexOf(phrase, ignoreCase = true)
            while (idx >= 0) {
                phraseRanges.add((idx until idx + phrase.length) to meta)
                idx = contextCard.contextSentence.indexOf(phrase, startIndex = idx + phrase.length, ignoreCase = true)
            }
        }

        val targetWordIndex = contextCard.contextSentence.indexOf(vocabItem.text, ignoreCase = true)
        val targetRange = if (targetWordIndex >= 0) targetWordIndex until (targetWordIndex + vocabItem.text.length) else null

        return ReviewItem(
            flashCardEntityId = entity.id,
            contextCardId = contextCard.id,
            word = vocabItem.text,
            contextSentence = contextCard.contextSentence,
            translations = MultiTranslation(contextCard.translation, contextCard.translationOnnx, contextCard.translationCloud, contextCard.translationCustom),
            fsrsCard = fsrsCard,
            grades = grades,
            tokens = tokens,
            wordMeta = wordMeta,
            phraseRanges = phraseRanges,
            targetWordRange = targetRange
        )
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

    override suspend fun postponeCard(flashCardEntityId: Long) {
        val entity = flashCardBox[flashCardEntityId] ?: return

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        entity.postponeUntilMillis = calendar.timeInMillis
        flashCardBox.put(entity)
    }

    override suspend fun deleteCard(flashCardEntityId: Long) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            flashCardBox.remove(flashCardEntityId)
        }
    }
}