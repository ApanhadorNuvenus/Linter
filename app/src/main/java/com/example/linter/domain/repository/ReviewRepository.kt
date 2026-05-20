package com.example.linter.domain.repository

import com.example.linter.data.fsrs.FlashCard
import com.example.linter.data.fsrs.Grade
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.domain.model.Token
import com.example.linter.domain.model.WordMeta

data class ReviewItem(
    val flashCardEntityId: Long,
    val contextCardId: Long,
    val word: String,
    val contextSentence: String,
    val translations: MultiTranslation,
    val fsrsCard: FlashCard,
    val grades: List<Grade>,
    // Добавлены поля для живого текста:
    val tokens: List<Token>,
    val wordMeta: Map<String, WordMeta>,
    val phraseRanges: List<Pair<IntRange, WordMeta>>,
    val targetWordRange: IntRange? // Чтобы подчеркнуть то самое слово, которое мы повторяем
)

interface ReviewRepository {
    suspend fun getDueCardsCount(): Int
    // Добавлен параметр lookaheadMs (по умолчанию 10 минут)
    suspend fun getDueReviewItems(lookaheadMs: Long = 600_000L): List<ReviewItem>
    suspend fun submitReview(flashCardEntityId: Long, grade: Grade)
}