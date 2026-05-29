package com.example.linter.domain.model

enum class LearningStatus(val level: Int) {
    NEW(1), RECOGNIZED(2), FAMILIAR(3), LEARNED(4);
    companion object { fun fromLevel(level: Int): LearningStatus = entries.find { it.level == level } ?: NEW }
}

enum class UiWordStatus { BLUE, YELLOW, TRANSPARENT }

data class WordMeta(
    val status: UiWordStatus,
    val learningStatus: LearningStatus? = null,
    val translations: MultiTranslation? = null,
    val contextCardId: Long? = null,
    val language: String? = null,
    val hasFlashCard: Boolean = true // Флаг: планируется ли слово в СРС-повторениях прямо сейчас
)